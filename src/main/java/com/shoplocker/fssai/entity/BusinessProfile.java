package com.shoplocker.fssai.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Stores the business profile data collected from the onboarding wizard screen.
 * Each user has exactly one business profile that captures their business portfolio details.
 */
@Entity
@Table(name = "business_profiles")
@Data
public class BusinessProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One-to-one relationship with User.
     * Each user can have only one business profile.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    @JsonIgnoreProperties({"password", "createdByAdmin", "enabled", "shops"})
    private User user;

    /**
     * Business count: "ONE" or "MULTIPLE"
     */
    @Column(nullable = false)
    private String businessCount;

    /**
     * Whether the user operates businesses across different categories.
     * Only relevant when businessCount = "MULTIPLE"
     */
    @Column(nullable = false)
    private boolean crossCategory = false;

    /**
     * Whether the user operates multiple branches within a category.
     * Only relevant when businessCount = "MULTIPLE"
     */
    @Column(nullable = false)
    private boolean multipleBranches = false;

    /**
     * Operation scope: "CITY", "STATE", or "NATIONAL"
     */
    @Column(nullable = false)
    private String operationScope;

    /**
     * Business presence: "PHYSICAL", "SCATTERED", or "DIGITAL"
     */
    @Column(nullable = false)
    private String digitalReadiness;

    /**
     * Total number of businesses calculated from wizard answers
     */
    @Column(nullable = false)
    private int totalBusinesses = 1;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getBusinessCount() { return businessCount; }
    public void setBusinessCount(String businessCount) { this.businessCount = businessCount; }

    public boolean isCrossCategory() { return crossCategory; }
    public void setCrossCategory(boolean crossCategory) { this.crossCategory = crossCategory; }

    public boolean isMultipleBranches() { return multipleBranches; }
    public void setMultipleBranches(boolean multipleBranches) { this.multipleBranches = multipleBranches; }

    public String getOperationScope() { return operationScope; }
    public void setOperationScope(String operationScope) { this.operationScope = operationScope; }

    public String getDigitalReadiness() { return digitalReadiness; }
    public void setDigitalReadiness(String digitalReadiness) { this.digitalReadiness = digitalReadiness; }

    public int getTotalBusinesses() { return totalBusinesses; }
    public void setTotalBusinesses(int totalBusinesses) { this.totalBusinesses = totalBusinesses; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
