package com.shoplocker.fssai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * One auto-onboarding attempt for a certificate file discovered in the FSSAI
 * feed. The unique {@code file_name} makes re-reading the feed harmless.
 */
@Entity
@Table(name = "onboarding_jobs",
       uniqueConstraints = @UniqueConstraint(columnNames = "file_name"))
public class OnboardingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OnboardingJobStatus status = OnboardingJobStatus.PENDING;

    @Column(length = 500)
    private String detail;

    @Column(name = "license_number", length = 32)
    private String licenseNumber;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "whatsapp_sent", nullable = false)
    private boolean whatsappSent = false;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public OnboardingJob() {
    }

    public OnboardingJob(String fileName) {
        this.fileName = fileName;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public OnboardingJobStatus getStatus() { return status; }
    public void setStatus(OnboardingJobStatus status) { this.status = status; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public boolean isWhatsappSent() { return whatsappSent; }
    public void setWhatsappSent(boolean whatsappSent) { this.whatsappSent = whatsappSent; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
