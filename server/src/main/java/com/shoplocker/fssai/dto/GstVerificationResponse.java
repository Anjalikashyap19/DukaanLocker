package com.shoplocker.fssai.dto;

/**
 * Response from {@code GET /api/gst/verify/{gstNumber}} or {@code POST /api/gst/fetch}.
 * On success, contains the taxpayer details from the GSTN API, the generated PDF URL,
 * and the raw HTML certificate.
 */
public class GstVerificationResponse {

    private boolean success;
    private String gstin;
    private String legalName;
    private String tradeName;
    private String registrationDate;
    private String status;
    private String state;
    private String constitutionOfBusiness;
    private String principalPlaceAddress;
    private String centralJurisdiction;
    private String periodOfValidity;
    private String typeOfRegistration;
    private String pdfUrl;
    private String certificateHtml;
    private String errorMessage;

    public GstVerificationResponse() {}

    public static GstVerificationResponse ok(String gstin, String legalName, String tradeName,
                                               String registrationDate, String status, String state,
                                               String constitutionOfBusiness, String principalPlaceAddress,
                                               String centralJurisdiction, String periodOfValidity,
                                               String typeOfRegistration,
                                               String pdfUrl, String certificateHtml) {
        GstVerificationResponse r = new GstVerificationResponse();
        r.success = true;
        r.gstin = gstin;
        r.legalName = legalName;
        r.tradeName = tradeName;
        r.registrationDate = registrationDate;
        r.status = status;
        r.state = state;
        r.constitutionOfBusiness = constitutionOfBusiness;
        r.principalPlaceAddress = principalPlaceAddress;
        r.centralJurisdiction = centralJurisdiction;
        r.periodOfValidity = periodOfValidity;
        r.typeOfRegistration = typeOfRegistration;
        r.pdfUrl = pdfUrl;
        r.certificateHtml = certificateHtml;
        return r;
    }

    public static GstVerificationResponse error(String message) {
        GstVerificationResponse r = new GstVerificationResponse();
        r.success = false;
        r.errorMessage = message;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }

    public String getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(String registrationDate) { this.registrationDate = registrationDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getConstitutionOfBusiness() { return constitutionOfBusiness; }
    public void setConstitutionOfBusiness(String constitutionOfBusiness) { this.constitutionOfBusiness = constitutionOfBusiness; }

    public String getPrincipalPlaceAddress() { return principalPlaceAddress; }
    public void setPrincipalPlaceAddress(String principalPlaceAddress) { this.principalPlaceAddress = principalPlaceAddress; }

    public String getCentralJurisdiction() { return centralJurisdiction; }
    public void setCentralJurisdiction(String centralJurisdiction) { this.centralJurisdiction = centralJurisdiction; }

    public String getPeriodOfValidity() { return periodOfValidity; }
    public void setPeriodOfValidity(String periodOfValidity) { this.periodOfValidity = periodOfValidity; }

    public String getTypeOfRegistration() { return typeOfRegistration; }
    public void setTypeOfRegistration(String typeOfRegistration) { this.typeOfRegistration = typeOfRegistration; }

    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public String getCertificateHtml() { return certificateHtml; }
    public void setCertificateHtml(String certificateHtml) { this.certificateHtml = certificateHtml; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
