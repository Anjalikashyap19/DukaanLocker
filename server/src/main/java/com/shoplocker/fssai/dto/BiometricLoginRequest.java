package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Biometric login request payload.
 * Sent after successful biometric authentication on the client.
 * The backend validates the userId exists and issues a fresh JWT token.
 */
public class BiometricLoginRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotBlank(message = "emailId is required")
    @Email(message = "emailId must be a valid email address")
    private String emailId;

    @NotBlank(message = "token is required")
    private String token;

    public BiometricLoginRequest() {}

    public BiometricLoginRequest(Long userId, String emailId, String token) {
        this.userId = userId;
        this.emailId = emailId;
        this.token = token;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
