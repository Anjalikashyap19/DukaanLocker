package com.shoplocker.fssai.exception;

import com.shoplocker.fssai.dto.FssaiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates exceptions into {@link FssaiErrorResponse}. The HTTP status
 * is driven by {@link FssaiException#getFailureCode()} so that callers
 * can distinguish "user uploaded a non-PDF" (400) from "AWS Textract is
 * down" (502) from "the file OCR'd but is the wrong document" (422).
 *
 * <p>Raw exception types are NEVER serialised into the response body —
 * they are logged server-side for observability, and the client only
 * sees {status, code, message, details, timestamp}.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FssaiException.class)
    public ResponseEntity<FssaiErrorResponse> handleFssaiException(FssaiException ex) {
        FailureCode failureCode = ex.getFailureCode();

        // Server-side observability: log the cause if any. Never expose raw exception class to clients.
        if (ex.getCause() != null) {
            log.warn("FssaiException code={} status={} message=\"{}\"",
                    failureCode.getCode(), failureCode.getHttpStatus().value(), ex.getMessage(), ex.getCause());
        } else {
            log.info("FssaiException code={} status={} message=\"{}\"",
                    failureCode.getCode(), failureCode.getHttpStatus().value(), ex.getMessage());
        }

        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(failureCode.getHttpStatus().value())
                .code(failureCode.getCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .build();
        return ResponseEntity.status(failureCode.getHttpStatus()).body(body);
    }

    /** Bean Validation failures (e.g. on FssaiRequest.licenseNumber). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<FssaiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.toList());
        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .code("validation_failed")
                .message("Validation failed")
                .details(details)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * The multipart upload omitted the required "file" part (e.g. curl call without -F file=@...).
     * Without this handler the exception would fall through to the catch-all 500.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<FssaiErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        String partName = ex.getRequestPartName();
        log.info("Missing multipart part '{}'", partName);
        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .code(FailureCode.INVALID_FILE_FORMAT.getCode())
                .message("Required file part '" + partName + "' is missing. " +
                        "Please attach a PDF document in the 'file' form field and try again.")
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** A required @RequestParam is missing — same semantic as above, different Spring class. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<FssaiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        log.info("Missing request parameter '{}'", ex.getParameterName());
        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .code(FailureCode.INVALID_FILE_FORMAT.getCode())
                .message("Required parameter '" + ex.getParameterName() + "' is missing.")
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Multipart upload raw failure: Spring's container-level multipart ceiling trips before
     * the request reaches our service code. Without this handler it falls through to 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<FssaiErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        long maxUploadSize = ex.getMaxUploadSize();
        log.info("Upload exceeded the multipart ceiling (max={} bytes)", maxUploadSize);
        String msg = "The uploaded file is larger than the maximum allowed size";
        if (maxUploadSize > 0) {
            msg += " (" + maxUploadSize + " bytes). Please upload a smaller PDF.";
        } else {
            msg += ". Please upload a smaller PDF.";
        }
        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .code(FailureCode.INVALID_FILE_FORMAT.getCode())
                .message(msg)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Parent of MaxUploadSizeExceededException — catches any other multipart parse failure. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<FssaiErrorResponse> handleMultipart(MultipartException ex) {
        log.info("Multipart parse failure: {}", ex.getMessage());
        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .code(FailureCode.INVALID_FILE_FORMAT.getCode())
                .message("The multipart upload could not be parsed: " + ex.getMessage() +
                        ". Please ensure the request includes a 'file' field with a PDF document.")
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Catch-all. Logged with stack trace; the client only sees a generic message + a stable code. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<FssaiErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception reached the global handler", ex);
        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .code(FailureCode.INTERNAL_ERROR.getCode())
                .message("An unexpected error occurred while processing the request.")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
