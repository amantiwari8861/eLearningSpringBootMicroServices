package in.iamandroid.apigateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AuthHeadersGatewayFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    private final WebClient authWebClient;
    private final GatewayErrorWriter errorWriter;

    public AuthHeadersGatewayFilter(
            WebClient.Builder webClientBuilder,
            @Value("${lms.auth-service-url:http://localhost:8082}") String authServiceUrl,
            GatewayErrorWriter errorWriter
    ) {
        this.authWebClient = webClientBuilder.baseUrl(authServiceUrl).build();
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = withoutClientIdentityHeaders(exchange);
        ServerHttpRequest request = sanitizedExchange.getRequest();
        String path = request.getPath().value();

        if (isPublicPath(path, request.getMethod())) {
            return chain.filter(withoutForwardedAuthorization(sanitizedExchange));
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return unauthenticated(sanitizedExchange);
        }

        ServerWebExchange downstreamExchange = path.startsWith("/api/v1/auth/")
                ? sanitizedExchange
                : withoutForwardedAuthorization(sanitizedExchange);

        return authenticate(authorization)
                .flatMap(outcome -> {
                    if (outcome.failed()) {
                        return errorWriter.write(downstreamExchange, outcome.status(), outcome.code(), outcome.message());
                    }

                    GatewayIdentity identity = outcome.identity().orElse(null);
                    if (!isUsableIdentity(identity)) {
                        return invalidIdentity(downstreamExchange);
                    }

                    return path.startsWith("/api/v1/auth/")
                            ? chain.filter(downstreamExchange)
                            : chain.filter(withIdentityHeaders(downstreamExchange, identity));
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<AuthOutcome> authenticate(String authorization) {
        return authWebClient.get()
                .uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.is4xxClientError()) {
                        return Mono.just(AuthOutcome.failure(HttpStatus.UNAUTHORIZED,
                                "INVALID_CREDENTIALS", "The supplied credentials were rejected."));
                    }
                    if (!status.is2xxSuccessful()) {
                        return Mono.just(AuthOutcome.failure(HttpStatus.SERVICE_UNAVAILABLE,
                                "UPSTREAM_UNAVAILABLE", "The authentication service is temporarily unavailable."));
                    }

                    return response.bodyToMono(GatewayIdentity.class)
                            .map(identity -> AuthOutcome.success(Optional.of(identity)))
                            .defaultIfEmpty(AuthOutcome.success(Optional.empty()))
                            .onErrorResume(error -> Mono.just(AuthOutcome.success(Optional.empty())));
                })
                .onErrorResume(WebClientRequestException.class, error ->
                        Mono.just(AuthOutcome.failure(HttpStatus.SERVICE_UNAVAILABLE,
                                "UPSTREAM_UNAVAILABLE", "The authentication service is temporarily unavailable.")))
                .onErrorResume(error -> Mono.just(AuthOutcome.failure(HttpStatus.SERVICE_UNAVAILABLE,
                        "UPSTREAM_UNAVAILABLE", "The authentication service is temporarily unavailable.")));
    }

    private boolean isPublicPath(String path, HttpMethod method) {
        return path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login")
                || path.startsWith("/actuator/")
                || (method == HttpMethod.GET
                        && (path.equals("/api/v1/courses") || path.startsWith("/api/v1/courses/")));
    }

    private ServerWebExchange withoutClientIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withoutForwardedAuthorization(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                .build();
        return exchange.mutate().request(request).build();
    }

    private boolean isUsableIdentity(GatewayIdentity identity) {
        return identity != null
                && identity.enabled()
                && identity.id() != null
                && identity.email() != null
                && !identity.email().isBlank();
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange, GatewayIdentity identity) {
        Set<String> roles = identity.roles() == null ? Set.of() : identity.roles();
        String rolesHeader = roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .sorted()
                .collect(Collectors.joining(","));

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(USER_ID_HEADER, identity.id().toString());
                    headers.set(USER_EMAIL_HEADER, identity.email());
                    headers.set(USER_ROLES_HEADER, rolesHeader);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private Mono<Void> unauthenticated(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"lms\"");
        return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED", "Authentication is required to access this resource.");
    }

    private Mono<Void> invalidIdentity(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"lms\"");
        return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                "INVALID_IDENTITY", "The authenticated identity could not be used.");
    }

    private record AuthOutcome(
            Optional<GatewayIdentity> identity,
            HttpStatus status,
            String code,
            String message
    ) {
        private static AuthOutcome success(Optional<GatewayIdentity> identity) {
            return new AuthOutcome(identity, null, null, null);
        }

        private static AuthOutcome failure(HttpStatus status, String code, String message) {
            return new AuthOutcome(Optional.empty(), status, code, message);
        }

        private boolean failed() {
            return status != null;
        }
    }
}
