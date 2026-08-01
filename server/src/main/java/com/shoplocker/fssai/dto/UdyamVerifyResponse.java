package com.shoplocker.fssai.dto;

/**
 * Response from {@code POST /api/udyam/verify}.
 * On success, contains the PDF URL of the generated MSME certificate
 * and the raw HTML from the government portal.
 */
public class UdyamVerifyResponse {

    private boolean success;
    private String pdfUrl;
    private String certificateHtml;
    private String udyamNumber;
    private String errorMessage;

    public UdyamVerifyResponse() {}

    public static UdyamVerifyResponse ok(String pdfUrl, String certificateHtml, String udyamNumber) {
        UdyamVerifyResponse r = new UdyamVerifyResponse();
        r.success = true;
        r.pdfUrl = pdfUrl;
        r.certificateHtml = certificateHtml;
        r.udyamNumber = udyamNumber;
        return r;
    }

    public static UdyamVerifyResponse error(String message) {
        UdyamVerifyResponse r = new UdyamVerifyResponse();
        r.success = false;
        r.errorMessage = message;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public String getCertificateHtml() { return certificateHtml; }
    public void setCertificateHtml(String certificateHtml) { this.certificateHtml = certificateHtml; }

    public String getUdyamNumber() { return udyamNumber; }
    public void setUdyamNumber(String udyamNumber) { this.udyamNumber = udyamNumber; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
