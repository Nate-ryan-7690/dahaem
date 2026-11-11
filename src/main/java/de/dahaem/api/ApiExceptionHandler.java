package de.dahaem.api;

import de.dahaem.auth.EmailTakenException;
import de.dahaem.auth.InvalidCredentialsException;
import de.dahaem.auth.TooManyAttemptsException;
import de.dahaem.barcode.IntakeConflictException;
import de.dahaem.barcode.InvalidBarcodeException;
import de.dahaem.barcode.PackUnitException;
import de.dahaem.mealplanner.PastDayException;
import de.dahaem.shopping.ListCompletedException;
import de.dahaem.stores.UnknownBrandException;
import de.dahaem.stores.UnknownStoreException;
import de.dahaem.shopping.PastShoppingDayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Turns exceptions into JSON error responses with sensible status codes.
 * The services report failures as plain RuntimeExceptions wrapping the SQLException,
 * so a constraint violation (foreign key, unique, primary key) is recognised from the cause
 * and reported as 409 instead of a generic 500.
 * Every error carries a stable machine-readable {@code code} (NOT_FOUND, PAST_DAY, LIST_COMPLETED, ...) next to the
 * English message, so a client can show its own text without matching message strings.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    /** A meal on a past day cannot be added, changed, moved or removed: the request conflicts with the calendar. */
    @ExceptionHandler(PastDayException.class)
    public ResponseEntity<ErrorResponse> pastDay(PastDayException e) {
        return error(HttpStatus.CONFLICT, "PAST_DAY", e.getMessage());
    }

    /** A completed shopping list cannot be edited. */
    @ExceptionHandler(ListCompletedException.class)
    public ResponseEntity<ErrorResponse> listCompleted(ListCompletedException e) {
        return error(HttpStatus.CONFLICT, "LIST_COMPLETED", e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException e) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
    }

    /** A barcode number that cannot be right: a bad request with a code of its own (INVALID_BARCODE, BARCODE_CHECKSUM). */
    @ExceptionHandler(InvalidBarcodeException.class)
    public ResponseEntity<ErrorResponse> invalidBarcode(InvalidBarcodeException e) {
        return error(HttpStatus.BAD_REQUEST, e.code(), e.getMessage());
    }

    /** The acting user belongs to no household. */
    @ExceptionHandler(NoHouseholdException.class)
    public ResponseEntity<ErrorResponse> noHousehold(NoHouseholdException e) {
        return error(HttpStatus.FORBIDDEN, "NO_HOUSEHOLD", e.getMessage());
    }

    /** The email address belongs to another account. */
    @ExceptionHandler(EmailTakenException.class)
    public ResponseEntity<ErrorResponse> emailTaken(EmailTakenException e) {
        return error(HttpStatus.CONFLICT, "EMAIL_TAKEN", e.getMessage());
    }

    /** A wrong email or password (the same answer for both). */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> invalidCredentials(InvalidCredentialsException e) {
        return error(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", e.getMessage());
    }

    /** Too many failed logins: the caller is slowed down; Retry-After says for how long. */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ErrorResponse> tooManyAttempts(TooManyAttemptsException e) {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status)
                .header("Retry-After", String.valueOf(e.retryAfterSeconds()))
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), e.getMessage(), "TOO_MANY_ATTEMPTS"));
    }

    /** An intake request id that cannot be used again as it is: a conflict with a code of its own. */
    @ExceptionHandler(IntakeConflictException.class)
    public ResponseEntity<ErrorResponse> intakeConflict(IntakeConflictException e) {
        return error(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    /** A store or brand name that does not exist, where creating one was not asked for: a bad request with its own code. */
    @ExceptionHandler(UnknownStoreException.class)
    public ResponseEntity<ErrorResponse> unknownStore(UnknownStoreException e) {
        return error(HttpStatus.BAD_REQUEST, "UNKNOWN_STORE", e.getMessage());
    }

    @ExceptionHandler(UnknownBrandException.class)
    public ResponseEntity<ErrorResponse> unknownBrand(UnknownBrandException e) {
        return error(HttpStatus.BAD_REQUEST, "UNKNOWN_BRAND", e.getMessage());
    }

    /** The pack of a barcode cannot be stocked in the item's unit: a bad request with a code of its own. */
    @ExceptionHandler(PackUnitException.class)
    public ResponseEntity<ErrorResponse> packUnit(PackUnitException e) {
        return error(HttpStatus.BAD_REQUEST, "PACK_UNIT_MISMATCH", e.getMessage());
    }

    /** The shopping day of a list is in the past: a bad request with a code of its own. */
    @ExceptionHandler(PastShoppingDayException.class)
    public ResponseEntity<ErrorResponse> pastShoppingDay(PastShoppingDayException e) {
        return error(HttpStatus.BAD_REQUEST, "PAST_SHOPPING_DAY", e.getMessage());
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> badRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Missing required parameter '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_VALUE", "Invalid value '" + e.getValue() + "' for '" + e.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_BODY", "Request body is missing or not valid JSON");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> unexpected(RuntimeException e) {
        if (isConstraintViolation(e)) {
            return error(HttpStatus.CONFLICT, "CONFLICT", e.getMessage() + " (conflicts with existing data)");
        }
        log.error("Unhandled error in API request", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    private static boolean isConstraintViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message, code));
    }
}
