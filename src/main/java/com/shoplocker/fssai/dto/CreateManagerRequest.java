package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for admin to create a manager.
 * Role is always set to MANAGER server-side. Password must follow strong password rules.
 */
public class CreateManagerRequest {

    @NotBlank(message = "userName is required")
    private String userName;

    @NotBlank(message = "mobileNumber is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "mobileNumber must be exactly 10 digits")
    private String mobileNumber;

    @NotBlank(message = "emailId is required")
    @Email(message = "emailId must be a valid email address")
    private String emailId;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 64, message = "password must be at least 8 characters long")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$",
            message = "password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;

    // Getters and Setters

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
