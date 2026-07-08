package com.shoplocker.fssai.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned by the API in
 * {@code FssaiErrorResponse.code}. Each value pairs a lower_snake_case
 * string with the HTTP status that {@link GlobalExceptionHandler} should
 * surface for that failure reason.
 */
public enum FailureCode {

    /** Uploaded file was missing, empty, oversized, wrong content type, or had bad PDF magic bytes. */
    INVALID_FILE_FORMAT        ("invalid_file_format",        HttpStatus.BAD_REQUEST),

    /** AWS SDK call to Textract failed at the AWS side (AccessDenied, throttling, network, etc.). */
    TEXTRACT_FAILURE           ("textract_failure",           HttpStatus.BAD_GATEWAY),

    /** AWS SDK call to S3 PutObject failed at the AWS side. */
    S3_UPLOAD_FAILED           ("s3_upload_failed",           HttpStatus.BAD_GATEWAY),

    /** OCR succeeded but the document content does not match the expected document type (missing fields, bad ID regex, etc.). */
    DOCUMENT_VALIDATION_FAILED ("document_validation_failed", HttpStatus.UNPROCESSABLE_ENTITY),

    /** OCR succeeded but the document strongly resembles a *different* document type (cross-contamination detected). */
    DOCUMENT_TYPE_MISMATCH     ("document_type_mismatch",     HttpStatus.UNPROCESSABLE_ENTITY),

    /** FSSAI portal scraper (Playwright) failed to fetch or parse a license on the government site. */
    SCRAPER_FAILURE            ("scraper_failure",            HttpStatus.BAD_GATEWAY),

    /** Resource not found (e.g. shop, license, document). */
    NOT_FOUND                  ("not_found",                  HttpStatus.NOT_FOUND),

    /** Unhandled / unexpected exception. */
    INTERNAL_ERROR             ("internal_error",             HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus httpStatus;

    FailureCode(String code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public HttpStatus getHttpStatus() { return httpStatus; }

    /** Lookup by stable code string; returns {@code null} if unknown. */
    public static FailureCode fromCode(String code) {
        if (code == null) return null;
        for (FailureCode fc : values()) {
            if (fc.code.equalsIgnoreCase(code)) return fc;
        }
        return null;
    }
}
