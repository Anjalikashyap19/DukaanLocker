package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API error response shape.
 *
 * <pre>{@code
 * {
 *   "status": 422,
 *   "code": "document_validation_failed",
 *   "message": "Uploaded file \"foo.pdf\" is not a valid GST Registration Certificate. ...",
 *   "details": ["missing fields: \"GSTIN\"", "expected document: GST Registration Certificate"],
 *   "timestamp": "2026-07-08T08:00:00"
 * }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FssaiErrorResponse {

    private int status;
    private String code;
    private String message;
    private List<String> details;
    private LocalDateTime timestamp;

    public FssaiErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public FssaiErrorResponse(int status, String code, String message,
                              List<String> details, LocalDateTime timestamp) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.details = details;
        this.timestamp = timestamp;
    }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getDetails() { return details; }
    public void setDetails(List<String> details) { this.details = details; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public static FssaiErrorResponseBuilder builder() {
        return new FssaiErrorResponseBuilder();
    }

    public static class FssaiErrorResponseBuilder {
        private int status;
        private String code;
        private String message;
        private List<String> details;
        private LocalDateTime timestamp;

        FssaiErrorResponseBuilder() {}

        public FssaiErrorResponseBuilder status(int status)              { this.status = status; return this; }
        public FssaiErrorResponseBuilder code(String code)               { this.code = code; return this; }
        public FssaiErrorResponseBuilder message(String message)         { this.message = message; return this; }
        public FssaiErrorResponseBuilder details(List<String> details)  { this.details = details; return this; }
        public FssaiErrorResponseBuilder timestamp(LocalDateTime t)      { this.timestamp = t; return this; }

        public FssaiErrorResponse build() {
            if (timestamp == null) timestamp = LocalDateTime.now();
            return new FssaiErrorResponse(status, code, message, details, timestamp);
        }
    }
}
