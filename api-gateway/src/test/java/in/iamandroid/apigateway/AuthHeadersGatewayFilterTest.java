package in.iamandroid.apigateway;

import in.iamandroid.apigateway.security.AuthHeadersGatewayFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthHeadersGatewayFilterTest {

    private static final String USER_ID = "197b7a63-5541-5052-88db-7a1eb783cd44";

    @Test
    void validatesBasicCredentialsAndReplacesClientIdentityHeaders() {
        String authResponse = """
                {
                  "id": "%s",
                  "email": "dummy.validation+trainee@example.invalid",
                  "roles": ["ROLE_TRAINEE"],
                  "enabled": true
                }
                """.formatted(USER_ID);

        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, authResponse);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .header(AuthHeadersGatewayFilter.USER_ID_HEADER, "spoofed")
                        .build());

        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = next -> {
            forwarded.set(next);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertEquals(USER_ID,
                forwarded.get().getRequest().getHeaders().getFirst(AuthHeadersGatewayFilter.USER_ID_HEADER));
        assertEquals("dummy.validation+trainee@example.invalid",
                forwarded.get().getRequest().getHeaders().getFirst(AuthHeadersGatewayFilter.USER_EMAIL_HEADER));
        assertEquals("ROLE_TRAINEE",
                forwarded.get().getRequest().getHeaders().getFirst(AuthHeadersGatewayFilter.USER_ROLES_HEADER));
    }

    @Test
    void rejectsRequestsWithoutBasicCredentials() {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/courses").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsInvalidCredentials() {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.UNAUTHORIZED, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjp3cm9uZw==")
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void publicRegistrationDoesNotForwardClientIdentityHeaders() {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthHeadersGatewayFilter.USER_ID_HEADER, "spoofed")
                        .body("{}"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get().getRequest().getHeaders().getFirst(AuthHeadersGatewayFilter.USER_ID_HEADER));
        assertTrue(forwarded.get().getRequest().getPath().value().equals("/api/v1/auth/register"));
    }

    @Test
    void publicLoginDoesNotForwardClientIdentityHeaders() {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthHeadersGatewayFilter.USER_ID_HEADER, "spoofed")
                        .body("{}"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get().getRequest().getHeaders().getFirst(AuthHeadersGatewayFilter.USER_ID_HEADER));
        assertEquals("/api/v1/auth/login", forwarded.get().getRequest().getPath().value());
    }

    private AuthHeadersGatewayFilter filterWithAuthResponse(HttpStatus status, String body) {
        ClientResponse response = ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();

        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(response));

        return new AuthHeadersGatewayFilter(builder, "http://localhost:8082");
    }
}
