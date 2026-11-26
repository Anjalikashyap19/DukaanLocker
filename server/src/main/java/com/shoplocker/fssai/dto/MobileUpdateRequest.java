package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MobileUpdateRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{10}$") String mobile,
        @NotBlank @Pattern(regexp = "^[0-9]{4,8}$") String otp) {
}
