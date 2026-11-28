package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MobileLoginSetPasswordRequest(
        @NotBlank(message = "mobileNumber is required") String mobileNumber,
        @NotBlank(message = "otp is required") String otp,
        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters") String password,
        @NotBlank(message = "confirm password is required") String confirmPassword
) {}
