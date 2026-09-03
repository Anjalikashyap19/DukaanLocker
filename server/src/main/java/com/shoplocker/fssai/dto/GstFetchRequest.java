package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for {@code POST /api/gst/fetch}.
 * Verifies a GSTIN against the government portal and persists it as a document.
 */
public class GstFetchRequest {

    @NotBlank(message = "shopId is required")
    private String shopId;

    @NotBlank(message = "GSTIN is required")
    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
             message = "GSTIN must be 15 characters (e.g., 27AAPFU0939F1ZV)")
    private String gstin;

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
}
