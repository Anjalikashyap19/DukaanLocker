package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input for {@code POST /api/auth/mobile-hint}: the email the user typed on the
 * owner sign-in form. Used only to recognise which account's mobile tail can be
 * shown as a memory aid while asking for that mobile number.
 */
public class MobileHintRequest {

    @NotBlank(message = "emailId is required")
    @Email(message = "emailId must be a valid email address")
    @Size(max = 255, message = "emailId must be at most 255 characters")
    private String emailId;

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }
}
