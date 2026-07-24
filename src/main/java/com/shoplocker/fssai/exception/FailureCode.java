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
    UNSUPPORTED_DOCUMENT_FORMAT("unsupported_document_format", HttpStatus.BAD_REQUEST),

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

    /** Ola Maps API call failed (network, auth, rate-limit, or invalid response). */
    OLA_MAPS_API_FAILURE       ("ola_maps_api_failure",       HttpStatus.BAD_GATEWAY),

    /** Resource not found (e.g. shop, license, document). */
    NOT_FOUND                  ("not_found",                  HttpStatus.NOT_FOUND),

    // ---- Auth / registration failures (added by JwtAuth feature) ----
    /** Defensive shape failure detected server-side on /api/auth/register (e.g. mobile regex miss). */
    INVALID_REQUEST            ("invalid_request",            HttpStatus.BAD_REQUEST),
    /** /api/auth/register tried to reuse an existing email (case-insensitive). */
    DUPLICATE_EMAIL            ("duplicate_email",            HttpStatus.CONFLICT),
    /** /api/auth/register tried to reuse an existing mobile number. */
    DUPLICATE_MOBILE           ("duplicate_mobile",           HttpStatus.CONFLICT),
    /** /api/auth/login email+password did not match (or email unknown — same code by design). */
    INVALID_CREDENTIALS        ("invalid_credentials",        HttpStatus.UNAUTHORIZED),
    /** /api/auth/login hit a User that has been disabled in the DB. */
    DISABLED_USER              ("disabled_user",              HttpStatus.FORBIDDEN),

    /** Rasterizer itself crashed (native lib failure, OOM, etc.) - NOT for client-bad PDFs.
     *  Corrupted / encrypted / image-only PDFs use UNSUPPORTED_DOCUMENT_FORMAT (400) instead. */
    PDF_PROCESSING_ERROR       ("pdf_processing_error",       HttpStatus.INTERNAL_SERVER_ERROR),

    // ---- Location / External API errors ----

    // ---- Shop / Manager / Assignment errors ----
    /** Shop not found. */
    SHOP_NOT_FOUND             ("shop_not_found",             HttpStatus.NOT_FOUND),
    /** Manager not found. */
    MANAGER_NOT_FOUND          ("manager_not_found",          HttpStatus.NOT_FOUND),
    /** User not found. */
    USER_NOT_FOUND             ("user_not_found",             HttpStatus.NOT_FOUND),
    /** Document not found. */
    DOCUMENT_NOT_FOUND         ("document_not_found",         HttpStatus.NOT_FOUND),
    /** Assignment not found. */
    ASSIGNMENT_NOT_FOUND       ("assignment_not_found",       HttpStatus.NOT_FOUND),

    /** Wizard business profile not found. */
    WIZARD_PROFILE_NOT_FOUND   ("wizard_profile_not_found",   HttpStatus.NOT_FOUND),

    /** Duplicate manager-shop assignment. */
    DUPLICATE_ASSIGNMENT       ("duplicate_assignment",       HttpStatus.CONFLICT),

    /** Unauthorized access. */
    UNAUTHORIZED               ("unauthorized",               HttpStatus.UNAUTHORIZED),
    /** Forbidden role or resource access. */
    FORBIDDEN                  ("forbidden",                  HttpStatus.FORBIDDEN),

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
