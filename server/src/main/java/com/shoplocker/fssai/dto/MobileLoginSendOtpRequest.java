package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MobileLoginSendOtpRequest(
        @NotBlank(message = "mobileNumber is required")
        @Pattern(regexp = "^[0-9]{10}$", message = "mobileNumber must be exactly 10 digits") String mobileNumber
) {}
