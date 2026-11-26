package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailUpdateRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^[0-9]{4,8}$") String code) {
}
