package com.shoplocker.fssai.exception;

/**
 * Single business exception used throughout the upload / scrape pipeline.
 * Carries a {@link FailureCode} so the {@link GlobalExceptionHandler}
 * can map the failure to the correct HTTP status and expose a stable
 * {@code code} string in the response body.
 */
public class FssaiException extends RuntimeException {

    private final FailureCode failureCode;

    public FssaiException(String message) {
        super(message);
        this.failureCode = FailureCode.INTERNAL_ERROR;
    }

    public FssaiException(String message, Throwable cause) {
        super(message, cause);
        this.failureCode = FailureCode.INTERNAL_ERROR;
    }

    public FssaiException(String message, FailureCode failureCode) {
        super(message);
        this.failureCode = failureCode == null ? FailureCode.INTERNAL_ERROR : failureCode;
    }

    public FssaiException(String message, FailureCode failureCode, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode == null ? FailureCode.INTERNAL_ERROR : failureCode;
    }

    public FailureCode getFailureCode() {
        return failureCode;
    }
}
