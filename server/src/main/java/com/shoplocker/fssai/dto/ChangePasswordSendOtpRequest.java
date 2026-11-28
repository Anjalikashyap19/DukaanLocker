package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordSendOtpRequest(
        @NotBlank(message = "emailId is required")
        @Email(message = "emailId must be a valid email address") String emailId,
        @NotBlank(message = "mobileNumber is required") String mobileNumber
) {}
