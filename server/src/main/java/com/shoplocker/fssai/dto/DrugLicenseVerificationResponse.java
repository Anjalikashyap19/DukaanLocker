package com.shoplocker.fssai.dto;

/**
 * Response from {@code GET /api/drug-license/verify/{licenseNumber}} or
 * {@code POST /api/drug-license/fetch}.
 * On success, contains the drug license details from the State Drugs Department
 * API, the generated PDF URL and the raw HTML certificate.
 */
public class DrugLicenseVerificationResponse {

    private boolean success;
    private String licenseNumber;
    private String licenseHolderName;
    private String kindOfBusiness;
    private String issueDate;
    private String expiryDate;
    private String state;
    private String district;
    private String flatDoor;
    private String premises;
    private String contactPerson;
    private String registrationNumber;
    private String pdfUrl;
    private String certificateHtml;
    private String errorMessage;

    public DrugLicenseVerificationResponse() {}

    public static DrugLicenseVerificationResponse ok(String licenseNumber, String licenseHolderName,
                                                     String kindOfBusiness, String issueDate,
                                                     String expiryDate, String state,
                                                     String district, String flatDoor,
                                                     String premises, String contactPerson,
                                                     String registrationNumber, String pdfUrl,
                                                     String certificateHtml) {
        DrugLicenseVerificationResponse r = new DrugLicenseVerificationResponse();
        r.success = true;
        r.licenseNumber = licenseNumber;
        r.licenseHolderName = licenseHolderName;
        r.kindOfBusiness = kindOfBusiness;
        r.issueDate = issueDate;
        r.expiryDate = expiryDate;
        r.state = state;
        r.district = district;
        r.flatDoor = flatDoor;
        r.premises = premises;
        r.contactPerson = contactPerson;
        r.registrationNumber = registrationNumber;
        r.pdfUrl = pdfUrl;
        r.certificateHtml = certificateHtml;
        return r;
    }

    public static DrugLicenseVerificationResponse error(String message) {
        DrugLicenseVerificationResponse r = new DrugLicenseVerificationResponse();
        r.success = false;
        r.errorMessage = message;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getLicenseHolderName() { return licenseHolderName; }
    public void setLicenseHolderName(String licenseHolderName) { this.licenseHolderName = licenseHolderName; }

    public String getKindOfBusiness() { return kindOfBusiness; }
    public void setKindOfBusiness(String kindOfBusiness) { this.kindOfBusiness = kindOfBusiness; }

    public String getIssueDate() { return issueDate; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getFlatDoor() { return flatDoor; }
    public void setFlatDoor(String flatDoor) { this.flatDoor = flatDoor; }

    public String getPremises() { return premises; }
    public void setPremises(String premises) { this.premises = premises; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public String getCertificateHtml() { return certificateHtml; }
    public void setCertificateHtml(String certificateHtml) { this.certificateHtml = certificateHtml; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}