package de.dahaem.barcode;

/**
 * An intake request id that cannot be used again as it is: it is still being processed (REQUEST_IN_PROGRESS) or it was
 * used before for a different request (REQUEST_ID_REUSED). A conflict (409) that carries its own code.
 */
public class IntakeConflictException extends RuntimeException {

    private final String code;

    public IntakeConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
