package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for {@code POST /api/drug-license/fetch}.
 * Verifies a drug license number against the State Drugs Department API and persists it as a document.
 */
public class DrugLicenseFetchRequest {

    @NotBlank(message = "shopId is required")
    private String shopId;

    @NotBlank(message = "Drug license number is required")
    private String licenseNumber;

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
}
