package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FssaiErrorResponse {

    private int status;
    private String message;
    private List<String> details;
    private LocalDateTime timestamp;

    public FssaiErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public FssaiErrorResponse(int status, String message, List<String> details, LocalDateTime timestamp) {
        this.status = status;
        this.message = message;
        this.details = details;
        this.timestamp = timestamp;
    }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

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
        private String message;
        private List<String> details;
        private LocalDateTime timestamp;

        FssaiErrorResponseBuilder() {}

        public FssaiErrorResponseBuilder status(int status) { this.status = status; return this; }
        public FssaiErrorResponseBuilder message(String message) { this.message = message; return this; }
        public FssaiErrorResponseBuilder details(List<String> details) { this.details = details; return this; }
        public FssaiErrorResponseBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }

        public FssaiErrorResponse build() {
            if (timestamp == null) timestamp = LocalDateTime.now();
            return new FssaiErrorResponse(status, message, details, timestamp);
        }
    }
}
