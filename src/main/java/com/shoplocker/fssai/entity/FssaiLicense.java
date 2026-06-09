package com.shoplocker.fssai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "fssai_licenses")
public class FssaiLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String licenseNumber;

    @Column(length = 255)
    private String businessName;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 50)
    private String status;

    @Column(length = 50)
    private String validityDate;

    @Column(length = 100)
    private String licenseType;

    @Column(length = 500)
    private String pdfPath;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public FssaiLicense() {}

    public FssaiLicense(Long id, String licenseNumber, String businessName, String address,
                        String status, String validityDate, String licenseType,
                        String pdfPath, LocalDateTime createdAt) {
        this.id = id;
        this.licenseNumber = licenseNumber;
        this.businessName = businessName;
        this.address = address;
        this.status = status;
        this.validityDate = validityDate;
        this.licenseType = licenseType;
        this.pdfPath = pdfPath;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getValidityDate() { return validityDate; }
    public void setValidityDate(String validityDate) { this.validityDate = validityDate; }

    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }

    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static FssaiLicenseBuilder builder() {
        return new FssaiLicenseBuilder();
    }

    public static class FssaiLicenseBuilder {
        private Long id;
        private String licenseNumber;
        private String businessName;
        private String address;
        private String status;
        private String validityDate;
        private String licenseType;
        private String pdfPath;
        private LocalDateTime createdAt;

        FssaiLicenseBuilder() {}

        public FssaiLicenseBuilder id(Long id) { this.id = id; return this; }
        public FssaiLicenseBuilder licenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; return this; }
        public FssaiLicenseBuilder businessName(String businessName) { this.businessName = businessName; return this; }
        public FssaiLicenseBuilder address(String address) { this.address = address; return this; }
        public FssaiLicenseBuilder status(String status) { this.status = status; return this; }
        public FssaiLicenseBuilder validityDate(String validityDate) { this.validityDate = validityDate; return this; }
        public FssaiLicenseBuilder licenseType(String licenseType) { this.licenseType = licenseType; return this; }
        public FssaiLicenseBuilder pdfPath(String pdfPath) { this.pdfPath = pdfPath; return this; }
        public FssaiLicenseBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public FssaiLicense build() {
            return new FssaiLicense(id, licenseNumber, businessName, address,
                    status, validityDate, licenseType, pdfPath, createdAt);
        }
    }
}
