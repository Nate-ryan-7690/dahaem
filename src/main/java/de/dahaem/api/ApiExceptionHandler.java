package de.dahaem.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;

/**
 * Turns exceptions into JSON error responses with sensible status codes.
 * The services report failures as plain RuntimeExceptions wrapping the SQLException,
 * so a constraint violation (foreign key, unique, primary key) is recognised from the cause
 * and reported as 409 instead of a generic 500.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> badRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "Missing required parameter '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value '" + e.getValue() + "' for '" + e.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or not valid JSON");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> unexpected(RuntimeException e) {
        if (isConstraintViolation(e)) {
            return error(HttpStatus.CONFLICT, e.getMessage() + " (conflicts with existing data)");
        }
        log.error("Unhandled error in API request", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static boolean isConstraintViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getMessage() != null
                    && sql.getMessage().contains("SQLITE_CONSTRAINT")) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
