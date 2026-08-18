package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/auth/register-msme}.
 * Combines user registration with Udyam (MSME) government portal verification.
 * The server verifies the Udyam number, generates a PDF certificate,
 * and creates the user account — all in one request.
 */
public class RegisterWithMsmeRequest {

    @NotBlank(message = "msmeNumber is required")
    @Pattern(regexp = "^UDYAM-[A-Z]{2}-\\d{2}-\\d{7}$",
             message = "MSME number must match format UDYAM-XX-XX-XXXXXXX")
    private String msmeNumber;

    @NotBlank(message = "mobileNumber is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "mobileNumber must be exactly 10 digits")
    private String mobileNumber;

    @NotBlank(message = "sessionId is required — call /api/udyam/init first")
    private String sessionId;

    @NotBlank(message = "captchaText is required")
    private String captchaText;

    /**
     * Optional email address.  If provided it is stored as the user's emailId.
     * If omitted (null / blank) the MSME (Udyam) number is stored instead,
     * so the emailId column is never null for MSME-registered users.
     */
    private String emailId;

    public String getMsmeNumber() { return msmeNumber; }
    public void setMsmeNumber(String msmeNumber) { this.msmeNumber = msmeNumber; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCaptchaText() { return captchaText; }
    public void setCaptchaText(String captchaText) { this.captchaText = captchaText; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }
}
