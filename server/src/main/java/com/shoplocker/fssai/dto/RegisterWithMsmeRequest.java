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

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 64, message = "password must be at least 8 characters long")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\\\|,.<>/?]).{8,}$",
            message = "password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;

    @NotBlank(message = "sessionId is required — call /api/udyam/init first")
    private String sessionId;

    @NotBlank(message = "captchaText is required")
    private String captchaText;

    public String getMsmeNumber() { return msmeNumber; }
    public void setMsmeNumber(String msmeNumber) { this.msmeNumber = msmeNumber; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCaptchaText() { return captchaText; }
    public void setCaptchaText(String captchaText) { this.captchaText = captchaText; }
}
