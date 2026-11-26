package in.iamandroid.apigateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
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

    public AuthHeadersGatewayFilter(
            WebClient.Builder webClientBuilder,
            @Value("${lms.auth-service-url:http://localhost:8082}") String authServiceUrl
    ) {
        this.authWebClient = webClientBuilder.baseUrl(authServiceUrl).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = withoutClientIdentityHeaders(exchange);
        ServerHttpRequest request = sanitizedExchange.getRequest();
        String path = request.getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(withoutForwardedAuthorization(sanitizedExchange));
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return unauthorized(sanitizedExchange);
        }

        ServerWebExchange downstreamExchange = path.startsWith("/api/v1/auth/")
                ? sanitizedExchange
                : withoutForwardedAuthorization(sanitizedExchange);

        return authWebClient.get()
                .uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .onStatus(status -> status.isError(), ClientResponse::createException)
                .bodyToMono(GatewayIdentity.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(error -> Mono.just(Optional.empty()))
                .flatMap(optionalIdentity -> optionalIdentity
                        .filter(this::isUsableIdentity)
                        .map(identity -> path.startsWith("/api/v1/auth/")
                                ? chain.filter(downstreamExchange)
                                : chain.filter(withIdentityHeaders(downstreamExchange, identity)))
                        .orElseGet(() -> unauthorized(downstreamExchange)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isPublicPath(String path) {
        return path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login")
                || path.startsWith("/actuator/");
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
        return identity.enabled()
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

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"lms\"");
        return response.setComplete();
    }
}
