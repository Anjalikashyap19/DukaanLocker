package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/auth/msme-login-request}.
 * The client supplies the Udyam (MSME) number; the server resolves the linked
 * mobile and sends an OTP to it.
 */
public class MsmeOtpRequest {

    @NotBlank(message = "msmeNumber is required")
    @Pattern(regexp = "^UDYAM-[A-Z]{2}-\\d{2}-\\d{7}$",
            message = "MSME number must match format UDYAM-XX-XX-XXXXXXX")
    private String msmeNumber;

    public String getMsmeNumber() { return msmeNumber; }
    public void setMsmeNumber(String msmeNumber) { this.msmeNumber = msmeNumber; }
}
