package in.iamandroid.order.exception;

import in.iamandroid.order.dto.ApiError;
import in.iamandroid.order.dto.ApiFieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), text(error.getDefaultMessage(), "Invalid value")))
                .sorted(Comparator.comparing(ApiFieldError::field))
                .toList();
        String message = errors.stream()
                .map(error -> error.field() + " " + error.message())
                .collect(Collectors.joining("; "));

        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                text(message, "The request contains invalid fields."), errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ApiFieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        violation.getPropertyPath().toString(),
                        text(violation.getMessage(), "Invalid value")))
                .sorted(Comparator.comparing(ApiFieldError::field))
                .toList();
        String message = errors.stream()
                .map(error -> error.field() + " " + error.message())
                .collect(Collectors.joining("; "));

        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                text(message, "The request contains invalid fields."), errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_JSON",
                "The request body could not be read.", List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleInvalidParameter(HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "The request contains an invalid parameter.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpServletRequest request) {
        return error(request, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This method is not allowed for the requested resource.", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpServletRequest request) {
        return error(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "This content type is not supported.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        return error(request, HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION",
                "The request conflicts with existing learning data.", List.of());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(
            DuplicateResourceException exception,
            HttpServletRequest request
    ) {
        return error(request, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
                text(exception.getMessage(), "The resource already exists."), List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(request, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                text(exception.getMessage(), "The requested resource was not found."), List.of());
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ApiError> handleUpstreamUnavailable(
            UpstreamUnavailableException exception,
            HttpServletRequest request
    ) {
        return error(request, HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                text(exception.getMessage(), "A required service is temporarily unavailable."), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception for {}", request.getRequestURI(), exception);
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be completed.", List.of());
    }

    private ResponseEntity<ApiError> error(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message,
            List<ApiFieldError> errors
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                code,
                text(message, "The request could not be completed."),
                request.getRequestURI(),
                errors);
        return ResponseEntity.status(status).body(body);
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
