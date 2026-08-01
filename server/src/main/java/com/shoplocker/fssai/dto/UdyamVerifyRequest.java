package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for {@code POST /api/udyam/verify}.
 * The user solves the CAPTCHA returned by {@code /api/udyam/init} and
 * submits it along with the Udyam registration number.
 */
public class UdyamVerifyRequest {

    @NotBlank(message = "sessionId is required")
    private String sessionId;

    @NotBlank(message = "Udyam number is required")
    @Pattern(regexp = "^UDYAM-[A-Z]{2}-\\d{2}-\\d{7}$",
             message = "Udyam number must match format UDYAM-XX-XX-XXXXXXX")
    private String udyamNumber;

    @NotBlank(message = "Captcha text is required")
    private String captchaText;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUdyamNumber() { return udyamNumber; }
    public void setUdyamNumber(String udyamNumber) { this.udyamNumber = udyamNumber; }

    public String getCaptchaText() { return captchaText; }
    public void setCaptchaText(String captchaText) { this.captchaText = captchaText; }
}
