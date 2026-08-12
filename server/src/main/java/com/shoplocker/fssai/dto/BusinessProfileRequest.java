package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating/updating a business profile from the wizard screen.
 * The owner is determined from JWT, not from request body.
 */
public class BusinessProfileRequest {

    @NotBlank(message = "businessCount is required")
    private String businessCount;

    private boolean crossCategory = false;

    private boolean multipleBranches = false;

    @NotBlank(message = "operationScope is required")
    private String operationScope;


    private String businessPresence;

    // Getters and Setters

    public String getBusinessCount() { return businessCount; }
    public void setBusinessCount(String businessCount) { this.businessCount = businessCount; }

    public boolean isCrossCategory() { return crossCategory; }
    public void setCrossCategory(boolean crossCategory) { this.crossCategory = crossCategory; }

    public boolean isMultipleBranches() { return multipleBranches; }
    public void setMultipleBranches(boolean multipleBranches) { this.multipleBranches = multipleBranches; }

    public String getOperationScope() { return operationScope; }
    public void setOperationScope(String operationScope) { this.operationScope = operationScope; }

    public String getBusinessPresence() { return businessPresence; }
    public void setBusinessPresence(String businessPresence) { this.businessPresence = businessPresence; }
}
