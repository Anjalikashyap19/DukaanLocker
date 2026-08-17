package com.shoplocker.fssai.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A single-use OTP challenge for a login flow (currently MSME number + OTP).
 * The OTP itself is stored ONLY as a BCrypt hash — the plaintext is discarded
 * immediately after the SMS is sent. Lookups are keyed by {@code mobile} +
 * {@code purpose} (one active challenge per mobile/purpose at a time).
 */
@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

    public static final String PURPOSE_MSME_LOGIN = "MSME_LOGIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msme_number", nullable = false)
    private String msmeNumber;

    @Column(name = "mobile", nullable = false)
    private String mobile;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMsmeNumber() { return msmeNumber; }
    public void setMsmeNumber(String msmeNumber) { this.msmeNumber = msmeNumber; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
