package com.shoplocker.fssai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request object containing the FSSAI license number to fetch")
public class FssaiRequest {

    @NotBlank(message = "License number is required")
    @Pattern(regexp = "\\d{14}", message = "License number must be exactly 14 digits")
    @Schema(description = "14-digit FSSAI license number", example = "12345678901234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String licenseNumber;

    public FssaiRequest() {}

    public FssaiRequest(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
}
