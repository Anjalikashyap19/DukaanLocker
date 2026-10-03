package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal success envelope for endpoints whose only meaningful output is a
 * user-facing status line — currently {@code POST /api/auth/reset-password}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageResponse {

    private boolean success;
    private String message;

    public MessageResponse() {}

    public MessageResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
