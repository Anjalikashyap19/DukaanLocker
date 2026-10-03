package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Step 1 of the forgot-password flow: {@code POST /api/auth/forgot-password}.
 * The client supplies the account's registered mobile number; the server resolves
 * the owner account and sends an OTP to that same number.
 */
public class ForgotPasswordRequest {

    @NotBlank(message = "mobileNumber is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "mobileNumber must be exactly 10 digits")
    private String mobileNumber;

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
}
