package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/auth/msme-login-verify}.
 * The client submits the Udyam number together with the OTP it received.
 */
public class MsmeOtpVerifyRequest {

    @NotBlank(message = "msmeNumber is required")
    @Pattern(regexp = "^UDYAM-[A-Z]{2}-\\d{2}-\\d{7}$",
            message = "MSME number must match format UDYAM-XX-XX-XXXXXXX")
    private String msmeNumber;

    @NotBlank(message = "otp is required")
    @Pattern(regexp = "^[0-9]{4,8}$", message = "otp must be 4-8 digits")
    private String otp;

    public String getMsmeNumber() { return msmeNumber; }
    public void setMsmeNumber(String msmeNumber) { this.msmeNumber = msmeNumber; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
