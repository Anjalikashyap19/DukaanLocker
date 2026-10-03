package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Step 2 of the forgot-password flow: {@code POST /api/auth/reset-password}.
 * The OTP is verified against the active challenge for this mobile before the
 * password is touched, and the new password is held to the same rules as
 * {@link RegisterRequest#getPassword()}.
 */
public class ResetPasswordRequest {

    @NotBlank(message = "mobileNumber is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "mobileNumber must be exactly 10 digits")
    private String mobileNumber;

    @NotBlank(message = "otp is required")
    @Size(min = 6, max = 8, message = "otp must be 6 digits")
    @Pattern(regexp = "^[0-9]{6,8}$", message = "otp must be 6 digits")
    private String otp;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 64, message = "password must be at least 8 characters long")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$",
            message = "password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
