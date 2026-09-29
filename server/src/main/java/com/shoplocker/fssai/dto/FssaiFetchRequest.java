package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for {@code POST /api/fssai/fetch}.
 * Verifies an FSSAI food license number against the license portal and persists it as a document.
 */
public class FssaiFetchRequest {

    @NotBlank(message = "shopId is required")
    private String shopId;

    @NotBlank(message = "FSSAI license number is required")
    @Pattern(regexp = "^[0-9]{14}$",
             message = "FSSAI license number must be 14 digits (e.g., 21221160000115)")
    private String licenseNumber;

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
}
