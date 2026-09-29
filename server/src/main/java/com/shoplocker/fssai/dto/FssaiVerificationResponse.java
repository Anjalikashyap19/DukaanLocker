package com.shoplocker.fssai.dto;

/**
 * Response from {@code GET /api/fssai/verify/{licenseNumber}} or {@code POST /api/fssai/fetch}.
 * On success, contains the food business operator details merged from the FSSAI
 * license lookup APIs, the generated PDF URL, and the raw HTML certificate.
 */
public class FssaiVerificationResponse {

    private boolean success;
    private String licenseNumber;
    private String companyName;
    private String contactPerson;
    private String kindOfBusiness;
    private String licenseCategory;
    private String status;
    private Boolean licenseActive;
    private String state;
    private String district;
    private String taluk;
    private String village;
    private String address;
    private String pincode;
    private String contactEmail;
    private String panNo;
    private String expiryDate;
    private String fboId;
    private String refId;
    private String pdfUrl;
    private String certificateHtml;
    private String errorMessage;

    public FssaiVerificationResponse() {}

    public static FssaiVerificationResponse ok(String licenseNumber, String companyName, String contactPerson,
                                               String kindOfBusiness, String licenseCategory, String status,
                                               Boolean licenseActive, String state, String district, String taluk,
                                               String village, String address, String pincode, String contactEmail,
                                               String panNo, String expiryDate, String fboId, String refId,
                                               String pdfUrl, String certificateHtml) {
        FssaiVerificationResponse r = new FssaiVerificationResponse();
        r.success = true;
        r.licenseNumber = licenseNumber;
        r.companyName = companyName;
        r.contactPerson = contactPerson;
        r.kindOfBusiness = kindOfBusiness;
        r.licenseCategory = licenseCategory;
        r.status = status;
        r.licenseActive = licenseActive;
        r.state = state;
        r.district = district;
        r.taluk = taluk;
        r.village = village;
        r.address = address;
        r.pincode = pincode;
        r.contactEmail = contactEmail;
        r.panNo = panNo;
        r.expiryDate = expiryDate;
        r.fboId = fboId;
        r.refId = refId;
        r.pdfUrl = pdfUrl;
        r.certificateHtml = certificateHtml;
        return r;
    }

    public static FssaiVerificationResponse error(String message) {
        FssaiVerificationResponse r = new FssaiVerificationResponse();
        r.success = false;
        r.errorMessage = message;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getKindOfBusiness() { return kindOfBusiness; }
    public void setKindOfBusiness(String kindOfBusiness) { this.kindOfBusiness = kindOfBusiness; }

    public String getLicenseCategory() { return licenseCategory; }
    public void setLicenseCategory(String licenseCategory) { this.licenseCategory = licenseCategory; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getLicenseActive() { return licenseActive; }
    public void setLicenseActive(Boolean licenseActive) { this.licenseActive = licenseActive; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getTaluk() { return taluk; }
    public void setTaluk(String taluk) { this.taluk = taluk; }

    public String getVillage() { return village; }
    public void setVillage(String village) { this.village = village; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getPanNo() { return panNo; }
    public void setPanNo(String panNo) { this.panNo = panNo; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public String getFboId() { return fboId; }
    public void setFboId(String fboId) { this.fboId = fboId; }

    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }

    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public String getCertificateHtml() { return certificateHtml; }
    public void setCertificateHtml(String certificateHtml) { this.certificateHtml = certificateHtml; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
