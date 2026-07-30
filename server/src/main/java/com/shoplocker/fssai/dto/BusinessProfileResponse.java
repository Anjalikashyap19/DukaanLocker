package com.shoplocker.fssai.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for business profile data.
 */
public class BusinessProfileResponse {

    private Long id;
    private Long userId;
    private String businessCount;
    private boolean crossCategory;
    private boolean multipleBranches;
    private String operationScope;
    private String businessPresence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BusinessProfileResponse() {}

    public BusinessProfileResponse(Long id, Long userId, String businessCount,
                                    boolean crossCategory, boolean multipleBranches,
                                    String operationScope, String businessPresence,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.businessCount = businessCount;
        this.crossCategory = crossCategory;
        this.multipleBranches = multipleBranches;
        this.operationScope = operationScope;
        this.businessPresence = businessPresence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
