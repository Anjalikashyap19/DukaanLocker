package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for {@code POST /api/auth/msme-login-request}. The {@code requestId}
 * is the challenge id (safe to return — it is not the OTP). {@code message} is
 * a user-facing status string.
 *
 * <p>The remaining fields carry the post-send rate-limit state so the client can
 * drive its own countdown instead of guessing. They are advisory: the server
 * re-checks every limit on each request, so a stale or tampered client value can
 * only ever make the UI wrong, never bypass a limit.</p>
 *
 * <ul>
 *   <li>{@code resendAvailableInSeconds} — seconds until the next send is allowed.
 *       {@code 0} means "you can send again now".</li>
 *   <li>{@code remainingSends} — sends still available before the cap trips and the
 *       lockout begins.</li>
 * </ul>
 *
 * <p>There is deliberately no "locked" field here: a locked request is rejected with
 * HTTP 429 and carries {@code otpLocked} / {@code retryAfterSeconds} on
 * {@link FssaiErrorResponse} instead.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MsmeOtpResponse {

    private String requestId;
    private String message;
    private int resendAvailableInSeconds;
    private int remainingSends;

    public MsmeOtpResponse() {}

    public MsmeOtpResponse(String requestId, String message) {
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
