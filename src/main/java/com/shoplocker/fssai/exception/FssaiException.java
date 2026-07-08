package com.shoplocker.fssai.exception;

import java.util.List;

/**
 * Single business exception used throughout the upload / scrape pipeline.
 * Carries a {@link FailureCode} so the {@link GlobalExceptionHandler}
 * can map the failure to the correct HTTP status and expose a stable
 * {@code code} string in the response body, plus optional structured
 * {@code details} entries that the API puts into FssaiErrorResponse.details.
 *
 * <p>User-facing messages should NEVER include raw SDK text (AWS exception
 * class names, ARNs, request IDs). Pass the upstream exception as
 * {@code cause} so it lands in server-side logs but not the response.</p>
 */
public class FssaiException extends RuntimeException {

    private final FailureCode failureCode;
    private final List<String> details;

    public FssaiException(String message) {
        super(message);
        this.failureCode = FailureCode.INTERNAL_ERROR;
        this.details = null;
    }

    public FssaiException(String message, Throwable cause) {
        super(message, cause);
        this.failureCode = FailureCode.INTERNAL_ERROR;
        this.details = null;
    }

    public FssaiException(String message, FailureCode failureCode) {
        super(message);
        this.failureCode = failureCode == null ? FailureCode.INTERNAL_ERROR : failureCode;
        this.details = null;
    }

    public FssaiException(String message, FailureCode failureCode, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode == null ? FailureCode.INTERNAL_ERROR : failureCode;
        this.details = null;
    }

    public FssaiException(String message, FailureCode failureCode, List<String> details) {
        super(message);
        this.failureCode = failureCode == null ? FailureCode.INTERNAL_ERROR : failureCode;
        this.details = details;
    }

    public FssaiException(String message, FailureCode failureCode, List<String> details, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode == null ? FailureCode.INTERNAL_ERROR : failureCode;
        this.details = details;
    }

    public FailureCode getFailureCode() {
        return failureCode;
    }

    public List<String> getDetails() {
        return details;
    }
}
