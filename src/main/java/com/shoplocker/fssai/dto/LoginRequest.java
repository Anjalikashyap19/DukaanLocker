package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login payload. The current authentication flow identifies the user by
 * {@code emailId} — see requirement "Login should currently use emailId
 * and password." {@code CustomUserDetailsService#loadUserByUsername}
 * therefore treats the emailId as the principal name.
 */
public class LoginRequest {

    @NotBlank(message = "emailId is required")
    @Email(message = "emailId must be a valid email address")
    private String emailId;

    @NotBlank(message = "password is required")
    private String password;

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
