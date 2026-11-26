package in.iamandroid.apigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.iamandroid.apigateway.security.GatewayErrorWriter;
import in.iamandroid.apigateway.security.GatewayExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void missingRouteUsesTheUniformErrorBody() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/missing").build());
        GatewayExceptionHandler handler = new GatewayExceptionHandler(new GatewayErrorWriter(objectMapper));

        handler.handle(exchange, new NotFoundException("No route found")).block();

        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND.value(), body.get("status").asInt());
        assertEquals("RESOURCE_NOT_FOUND", body.get("code").asText());
        assertFalse(body.get("message").asText().isBlank());
        assertEquals("/api/v1/missing", body.get("path").asText());
        assertTrue(body.hasNonNull("timestamp"));
        assertTrue(body.get("errors").isArray());
    }

    @Test
    void unhandledFailureUsesTheUniformErrorBody() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/enrollments/me").build());
        GatewayExceptionHandler handler = new GatewayExceptionHandler(new GatewayErrorWriter(objectMapper));

        handler.handle(exchange, new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Upstream is down")).block();

        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals("SERVICE_UNAVAILABLE", body.get("code").asText());
        assertEquals("Upstream is down", body.get("message").asText());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }
}
