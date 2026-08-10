package com.shoplocker.fssai.dto;

import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.User;

/**
 * Returned by both {@code POST /api/auth/register} and
 * {@code POST /api/auth/login}. The BCrypt-encoded password hash NEVER
 * appears in this DTO — we copy only the public-safe fields off the
 * {@link User} entity.
 */
public class AuthResponse {

    private String token;
    private String tokenType = "Bearer";
    private Long userId;
    private String userName;
    private String mobileNumber;
    private String emailId;
    private Role role;
    private String managerCode;

    public AuthResponse() {}

    public AuthResponse(String token, Long userId, String userName, String mobileNumber,
                        String emailId, Role role) {
        this.token = token;
        this.tokenType = "Bearer";
        this.userId = userId;
        this.userName = userName;
        this.mobileNumber = mobileNumber;
        this.emailId = emailId;
        this.role = role;
    }

    public AuthResponse(String token, Long userId, String userName, String mobileNumber,
                        String emailId, Role role, String managerCode) {
        this.token = token;
        this.tokenType = "Bearer";
        this.userId = userId;
        this.userName = userName;
        this.mobileNumber = mobileNumber;
        this.emailId = emailId;
        this.role = role;
        this.managerCode = managerCode;
    }

    public static AuthResponse from(User user, String token) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getUserName(),
                user.getMobileNumber(),
                user.getEmailId(),
                user.getRole(),
                user.getManagerCode());
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getManagerCode() { return managerCode; }
    public void setManagerCode(String managerCode) { this.managerCode = managerCode; }
}
