package com.shoplocker.fssai.dto;

import com.shoplocker.fssai.entity.Role;

/**
 * Extended auth response for MSME registration that includes the
 * generated PDF certificate URL, parsed enterprise data, and shop details
 * alongside the standard JWT fields.
 */
public class MsmeAuthResponse extends AuthResponse {

    private String certificatePdfUrl;
    private String udyamNumber;
    private String enterpriseName;
    private String entrepreneurName;
    private String emailId;
    private Long shopId;
    private String shopName;
    private String shopCategory;
    private String shopState;
    private String shopCity;
    private String shopAddress;

    public MsmeAuthResponse() {}

    public MsmeAuthResponse(AuthResponse base, String certificatePdfUrl, String udyamNumber) {
        super(base.getToken(), base.getUserId(), base.getUserName(),
              base.getMobileNumber(), base.getEmailId(), base.getRole());
        this.certificatePdfUrl = certificatePdfUrl;
        this.udyamNumber = udyamNumber;
    }

    // ─── Getters and Setters ───────────────────────────────────────────

    public String getCertificatePdfUrl() { return certificatePdfUrl; }
    public void setCertificatePdfUrl(String certificatePdfUrl) { this.certificatePdfUrl = certificatePdfUrl; }

    public String getUdyamNumber() { return udyamNumber; }
    public void setUdyamNumber(String udyamNumber) { this.udyamNumber = udyamNumber; }

    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }

    public String getEntrepreneurName() { return entrepreneurName; }
    public void setEntrepreneurName(String entrepreneurName) { this.entrepreneurName = entrepreneurName; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getShopCategory() { return shopCategory; }
    public void setShopCategory(String shopCategory) { this.shopCategory = shopCategory; }

    public String getShopState() { return shopState; }
    public void setShopState(String shopState) { this.shopState = shopState; }

    public String getShopCity() { return shopCity; }
    public void setShopCity(String shopCity) { this.shopCity = shopCity; }

    public String getShopAddress() { return shopAddress; }
    public void setShopAddress(String shopAddress) { this.shopAddress = shopAddress; }
}
