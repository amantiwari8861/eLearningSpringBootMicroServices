package in.iamandroid.apigateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.iamandroid.apigateway.dto.ApiError;
import in.iamandroid.apigateway.dto.ApiFieldError;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class GatewayErrorWriter {

    private final ObjectMapper objectMapper;

    public GatewayErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatusCode status,
            String code,
            String message
    ) {
        return write(exchange, status, code, message, List.of());
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatusCode status,
            String code,
            String message,
            List<ApiFieldError> errors
    ) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                exchange.getRequest().getPath().value(),
                errors);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize gateway error response", exception);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(body.length);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");

        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
