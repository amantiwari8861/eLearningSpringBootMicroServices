package in.iamandroid.apigateway.security;

import in.iamandroid.apigateway.dto.ApiFieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final GatewayErrorWriter errorWriter;

    public GatewayExceptionHandler(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exception instanceof WebExchangeBindException bindException) {
            List<ApiFieldError> errors = bindException.getFieldErrors().stream()
                    .map(error -> new ApiFieldError(error.getField(), text(error.getDefaultMessage(), "Invalid value")))
                    .sorted(Comparator.comparing(ApiFieldError::field))
                    .toList();
            String message = errors.stream()
                    .map(error -> error.field() + " " + error.message())
                    .collect(Collectors.joining("; "));

            return errorWriter.write(exchange, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    text(message, "The request contains invalid fields."), errors);
        }

        HttpStatusCode status = statusOf(exception);
        String code = codeOf(status);
        String message = detailOf(exception, status);

        if (status.is5xxServerError()) {
            log.error("Gateway request failed for {}", exchange.getRequest().getPath().value(), exception);
        }

        return errorWriter.write(exchange, status, code, message);
    }

    private HttpStatusCode statusOf(Throwable exception) {
        if (exception instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode();
        }
        if (exception instanceof ResponseStatusException statusException) {
            return statusException.getStatusCode();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String codeOf(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 503 -> "SERVICE_UNAVAILABLE";
            case 504 -> "GATEWAY_TIMEOUT";
            default -> status.is5xxServerError() ? "UPSTREAM_ERROR" : "REQUEST_REJECTED";
        };
    }

    private String detailOf(Throwable exception, HttpStatusCode status) {
        if (exception instanceof NotFoundException) {
            return "The requested route was not found.";
        }
        if (exception instanceof ErrorResponse errorResponse) {
            var body = errorResponse.getBody();
            if (body != null && body.getDetail() != null && !body.getDetail().isBlank()) {
                return body.getDetail();
            }
        }
        if (exception instanceof ResponseStatusException statusException
                && statusException.getReason() != null
                && !statusException.getReason().isBlank()) {
            return statusException.getReason();
        }

        return switch (status.value()) {
            case 400 -> "The request could not be read.";
            case 401 -> "Authentication is required to access this resource.";
            case 403 -> "You do not have permission to access this resource.";
            case 404 -> "The requested resource was not found.";
            case 405 -> "This method is not allowed for the requested resource.";
            case 415 -> "This content type is not supported.";
            case 503 -> "A required upstream service is temporarily unavailable.";
            case 504 -> "A required upstream service did not respond in time.";
            default -> "The request could not be completed.";
        };
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
