package in.iamandroid.apigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.iamandroid.apigateway.security.AuthHeadersGatewayFilter;
import in.iamandroid.apigateway.security.GatewayErrorWriter;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthHeadersGatewayFilterTest {

    private static final String USER_ID = "197b7a63-5541-5052-88db-7a1eb783cd44";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
                MockServerHttpRequest.get("/api/v1/orders/enrollments/me")
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
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void rejectsRequestsWithoutBasicCredentials() throws Exception {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/enrollments/me").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertApiError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Authentication is required to access this resource.");
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.UNAUTHORIZED, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/enrollments/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjp3cm9uZw==")
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertApiError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "The supplied credentials were rejected.");
    }

    @Test
    void reportsAuthServiceOutageAsUnavailable() throws Exception {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.SERVICE_UNAVAILABLE, "{}");
        MockServerWebExchange exchange = exchangeWithBasicCredentials("/api/v1/orders/enrollments/me");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertApiError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                "The authentication service is temporarily unavailable.");
    }

    @Test
    void publicCourseGetDoesNotRequireCredentials() {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .header(AuthHeadersGatewayFilter.USER_ID_HEADER, "spoofed")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get().getRequest().getHeaders().getFirst(AuthHeadersGatewayFilter.USER_ID_HEADER));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("/api/v1/courses", forwarded.get().getRequest().getPath().value());
    }

    @Test
    void coursePostStillRequiresCredentials() throws Exception {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/courses").body("{}"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertApiError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Authentication is required to access this resource.");
    }

    @Test
    void publicRegistrationDoesNotForwardClientIdentityHeaders() {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .header(AuthHeadersGatewayFilter.USER_ID_HEADER, "spoofed")
                        .body("{}"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get().getRequest().getHeaders().getFirst(AuthHeadersGatewayFilter.USER_ID_HEADER));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertTrue(forwarded.get().getRequest().getPath().value().equals("/api/v1/auth/register"));
    }

    @Test
    void publicLoginDoesNotForwardClientIdentityHeaders() {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .header(AuthHeadersGatewayFilter.USER_ID_HEADER, "spoofed")
                        .body("{}"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get().getRequest().getHeaders().getFirst(AuthHeadersGatewayFilter.USER_ID_HEADER));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("/api/v1/auth/login", forwarded.get().getRequest().getPath().value());
    }

    @Test
    void downstreamFailureIsNotReportedAsUnauthorized() {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, identityBody(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/enrollments/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .build());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> filter.filter(exchange, next -> Mono.error(new IllegalStateException("upstream failure")))
                        .block());

        assertEquals("upstream failure", thrown.getMessage());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void protectedAuthRouteKeepsBasicCredentialsAndStripsIdentityHeaders() {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, identityBody(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .header(AuthHeadersGatewayFilter.USER_ID_HEADER, "spoofed")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertEquals("Basic dXNlcjpwYXNz",
                forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(AuthHeadersGatewayFilter.USER_ID_HEADER));
    }

    @Test
    void identityWithoutIdOrEmailIsRejected() throws Exception {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "{\"enabled\":true}");
        MockServerWebExchange exchange = exchangeWithBasicCredentials("/api/v1/orders/enrollments/me");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertApiError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY",
                "The authenticated identity could not be used.");
    }

    @Test
    void disabledIdentityIsRejected() throws Exception {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, identityBody(false));
        MockServerWebExchange exchange = exchangeWithBasicCredentials("/api/v1/orders/enrollments/me");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertApiError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY",
                "The authenticated identity could not be used.");
    }

    @Test
    void emptyAuthResponseIsRejected() throws Exception {
        AuthHeadersGatewayFilter filter = filterWithAuthResponse(HttpStatus.OK, "");
        MockServerWebExchange exchange = exchangeWithBasicCredentials("/api/v1/orders/enrollments/me");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertApiError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY",
                "The authenticated identity could not be used.");
    }

    private void assertApiError(
            MockServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message
    ) throws Exception {
        assertEquals(status, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals(status.value(), body.get("status").asInt());
        assertEquals(code, body.get("code").asText());
        assertEquals(message, body.get("message").asText());
        assertEquals(exchange.getRequest().getPath().value(), body.get("path").asText());
        assertTrue(body.hasNonNull("timestamp"));
        assertTrue(body.get("errors").isArray());
    }

    private MockServerWebExchange exchangeWithBasicCredentials(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .build());
    }

    private String identityBody(boolean enabled) {
        return """
                {
                  "id": "%s",
                  "email": "dummy.validation+trainee@example.invalid",
                  "roles": ["ROLE_TRAINEE"],
                  "enabled": %s
                }
                """.formatted(USER_ID, enabled);
    }

    private AuthHeadersGatewayFilter filterWithAuthResponse(HttpStatus status, String body) {
        ClientResponse response = ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();

        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(response));
        GatewayErrorWriter errorWriter = new GatewayErrorWriter(objectMapper);

        return new AuthHeadersGatewayFilter(builder, "http://localhost:8082", errorWriter);
    }
}
