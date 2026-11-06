package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request to create a renewal payment for a document.
 */
public class PaymentCreateRequest {

    @NotNull(message = "shopId is required")
    private Long shopId;

    @NotNull(message = "documentType is required")
    private String documentType;

    @NotNull(message = "durationYears is required")
    @Min(value = 1, message = "durationYears must be between 1 and 5")
    @jakarta.validation.constraints.Max(value = 5, message = "durationYears must be between 1 and 5")
    private Integer durationYears;

    private String kob;

    public PaymentCreateRequest() {}

    public PaymentCreateRequest(Long shopId, String documentType, Integer durationYears) {
        this.shopId = shopId;
        this.documentType = documentType;
        this.durationYears = durationYears;
    }

    public PaymentCreateRequest(Long shopId, String documentType, Integer durationYears, String kob) {
        this.shopId = shopId;
        this.documentType = documentType;
        this.durationYears = durationYears;
        this.kob = kob;
    }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public Integer getDurationYears() { return durationYears; }
    public void setDurationYears(Integer durationYears) { this.durationYears = durationYears; }

    public String getKob() { return kob; }
    public void setKob(String kob) { this.kob = kob; }
}
