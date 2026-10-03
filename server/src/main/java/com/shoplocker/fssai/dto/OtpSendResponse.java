package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for {@code POST /api/auth/forgot-password}. Same shape as
 * {@link MsmeOtpResponse} so the client can drive the resend countdown with one
 * code path; kept as its own type because the two flows are separate API contracts.
 *
 * <p>{@code requestId} is the challenge id (safe to return — it is not the OTP).
 * {@code message} is a user-facing status string and is deliberately identical
 * whether or not the mobile number exists, so the endpoint cannot be used to
 * enumerate registered accounts.</p>
 *
 * <ul>
 *   <li>{@code resendAvailableInSeconds} — seconds until the next send is allowed.
 *       {@code 0} means "you can send again now".</li>
 *   <li>{@code remainingSends} — sends still available before the cap trips and the
 *       lockout begins.</li>
 * </ul>
 *
 * <p>A locked request is rejected with HTTP 429 and carries {@code otpLocked} /
 * {@code retryAfterSeconds} on {@link FssaiErrorResponse} instead.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OtpSendResponse {

    private String requestId;
    private String message;
    private int resendAvailableInSeconds;
    private int remainingSends;

    public OtpSendResponse() {}

    public OtpSendResponse(String requestId, String message) {
        this.requestId = requestId;
        this.message = message;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getResendAvailableInSeconds() { return resendAvailableInSeconds; }
    public void setResendAvailableInSeconds(int resendAvailableInSeconds) {
        this.resendAvailableInSeconds = resendAvailableInSeconds;
    }

    public int getRemainingSends() { return remainingSends; }
    public void setRemainingSends(int remainingSends) { this.remainingSends = remainingSends; }
}
