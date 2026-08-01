package com.shoplocker.fssai.dto;

/**
 * Response from {@code POST /api/udyam/init}.
 * <p>
 * The {@code sessionId} must be passed back in the subsequent
 * {@code POST /api/udyam/verify} call so the server can match
 * the CAPTCHA and ASP.NET view-state to the correct government portal session.
 */
public class UdyamInitResponse {

    private String sessionId;
    private String captchaBase64;   // data:image/png;base64,...
    private String message;

    public UdyamInitResponse() {}

    public UdyamInitResponse(String sessionId, String captchaBase64) {
        this.sessionId = sessionId;
        this.captchaBase64 = captchaBase64;
        this.message = "Captcha loaded successfully";
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCaptchaBase64() { return captchaBase64; }
    public void setCaptchaBase64(String captchaBase64) { this.captchaBase64 = captchaBase64; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
