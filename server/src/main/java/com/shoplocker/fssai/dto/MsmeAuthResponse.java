package com.shoplocker.fssai.dto;

import com.shoplocker.fssai.entity.Role;

/**
 * Extended auth response for MSME registration that includes the
 * generated PDF certificate URL alongside the standard JWT fields.
 */
public class MsmeAuthResponse extends AuthResponse {

    private String certificatePdfUrl;
    private String udyamNumber;

    public MsmeAuthResponse() {}

    public MsmeAuthResponse(AuthResponse base, String certificatePdfUrl, String udyamNumber) {
        super(base.getToken(), base.getUserId(), base.getUserName(),
              base.getMobileNumber(), base.getEmailId(), base.getRole());
        this.certificatePdfUrl = certificatePdfUrl;
        this.udyamNumber = udyamNumber;
    }

    public String getCertificatePdfUrl() { return certificatePdfUrl; }
    public void setCertificatePdfUrl(String certificatePdfUrl) { this.certificatePdfUrl = certificatePdfUrl; }

    public String getUdyamNumber() { return udyamNumber; }
    public void setUdyamNumber(String udyamNumber) { this.udyamNumber = udyamNumber; }
}
