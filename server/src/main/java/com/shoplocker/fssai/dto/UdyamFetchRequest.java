package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for {@code POST /api/udyam/fetch}.
 * Verifies a Udyam number against the government portal and persists the MSME certificate as a document.
 */
public class UdyamFetchRequest {

    @NotBlank(message = "shopId is required")
    private String shopId;

    @NotBlank(message = "Udyam number is required")
    @Pattern(regexp = "^UDYAM-[A-Z]{2}-\\d{2}-\\d{7}$",
             message = "Udyam number must match format UDYAM-XX-XX-XXXXXXX")
    private String udyamNumber;

    @NotBlank(message = "sessionId is required")
    private String sessionId;

    @NotBlank(message = "Captcha text is required")
    private String captchaText;

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getUdyamNumber() { return udyamNumber; }
    public void setUdyamNumber(String udyamNumber) { this.udyamNumber = udyamNumber; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCaptchaText() { return captchaText; }
    public void setCaptchaText(String captchaText) { this.captchaText = captchaText; }
}
