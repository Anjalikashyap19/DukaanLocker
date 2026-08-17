package com.shoplocker.fssai.dto;

/**
 * Response for {@code POST /api/auth/msme-login-request}. The {@code requestId}
 * is the challenge id (safe to return — it is not the OTP). {@code message} is
 * a user-facing status string.
 */
public class MsmeOtpResponse {

    private String requestId;
    private String message;

    public MsmeOtpResponse() {}

    public MsmeOtpResponse(String requestId, String message) {
        this.requestId = requestId;
        this.message = message;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
