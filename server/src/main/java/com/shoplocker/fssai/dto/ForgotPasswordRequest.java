package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Step 1 of the forgot-password flow: {@code POST /api/auth/forgot-password}.
 * The client supplies the email typed on the sign-in form and the account's
 * registered mobile number; the server only proceeds when both belong to the same
 * owner account, so an arbitrary (unregistered) email cannot start a reset.
 */
public class ForgotPasswordRequest {

    @NotBlank(message = "emailId is required")
    @Email(message = "emailId must be a valid email address")
    @Size(max = 255, message = "emailId must be at most 255 characters")
    private String emailId;

    @NotBlank(message = "mobileNumber is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "mobileNumber must be exactly 10 digits")
    private String mobileNumber;

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
}
