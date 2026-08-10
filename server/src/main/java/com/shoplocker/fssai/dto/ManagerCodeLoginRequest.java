package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for manager login using access code.
 * Manager enters the 6-character code assigned by the owner.
 */
public class ManagerCodeLoginRequest {

    @NotBlank(message = "managerCode is required")
    @Size(min = 6, max = 6, message = "managerCode must be exactly 6 characters")
    @Pattern(regexp = "^[A-Z0-9]{6}$", message = "managerCode must be 6 uppercase alphanumeric characters")
    private String managerCode;

    public ManagerCodeLoginRequest() {}

    public ManagerCodeLoginRequest(String managerCode) {
        this.managerCode = managerCode;
    }

    public String getManagerCode() { return managerCode; }
    public void setManagerCode(String managerCode) { this.managerCode = managerCode; }
}
