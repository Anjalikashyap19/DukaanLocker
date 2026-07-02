package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response object containing FSSAI license details")
public class FssaiResponse {

    @Schema(description = "Indicates if the operation was successful", example = "true")
    private boolean success;
    @Schema(description = "14-digit FSSAI license number", example = "12345678901234")
    private String licenseNumber;
    @Schema(description = "Business name associated with the license", example = "M/s ABC Traders")
    private String businessName;
    @Schema(description = "Registered business address", example = "123, Main Street, New Delhi - 110001")
    private String address;
    @Schema(description = "Current status of the license", example = "Active")
    private String status;
    @Schema(description = "Validity date of the license", example = "31-03-2027")
    private String validityDate;
    @Schema(description = "Type of FSSAI license", example = "Central")
    private String licenseType;
    @Schema(description = "Path to the downloaded PDF certificate", example = "downloads/fssai/12345678901234.pdf")
    private String pdfPath;
    @Schema(description = "Response message in case of error or additional info", example = "License fetched successfully")
    private String message;

    public FssaiResponse() {}

    public FssaiResponse(boolean success, String licenseNumber, String businessName, String address,
                         String status, String validityDate, String licenseType,
                         String pdfPath, String message) {
        this.success = success;
        this.licenseNumber = licenseNumber;
        this.businessName = businessName;
        this.address = address;
        this.status = status;
        this.validityDate = validityDate;
        this.licenseType = licenseType;
        this.pdfPath = pdfPath;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

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

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public static FssaiResponseBuilder builder() {
        return new FssaiResponseBuilder();
    }

    public static class FssaiResponseBuilder {
        private boolean success;
        private String licenseNumber;
        private String businessName;
        private String address;
        private String status;
        private String validityDate;
        private String licenseType;
        private String pdfPath;
        private String message;

        FssaiResponseBuilder() {}

        public FssaiResponseBuilder success(boolean success) { this.success = success; return this; }
        public FssaiResponseBuilder licenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; return this; }
        public FssaiResponseBuilder businessName(String businessName) { this.businessName = businessName; return this; }
        public FssaiResponseBuilder address(String address) { this.address = address; return this; }
        public FssaiResponseBuilder status(String status) { this.status = status; return this; }
        public FssaiResponseBuilder validityDate(String validityDate) { this.validityDate = validityDate; return this; }
        public FssaiResponseBuilder licenseType(String licenseType) { this.licenseType = licenseType; return this; }
        public FssaiResponseBuilder pdfPath(String pdfPath) { this.pdfPath = pdfPath; return this; }
        public FssaiResponseBuilder message(String message) { this.message = message; return this; }

        public FssaiResponse build() {
            return new FssaiResponse(success, licenseNumber, businessName, address,
                    status, validityDate, licenseType, pdfPath, message);
        }
    }

    public static FssaiResponse success(String licenseNumber, String businessName, String address,
                                        String status, String validityDate, String licenseType, String pdfPath) {
        return FssaiResponse.builder()
                .success(true)
                .licenseNumber(licenseNumber)
                .businessName(businessName)
                .address(address)
                .status(status)
                .validityDate(validityDate)
                .licenseType(licenseType)
                .pdfPath(pdfPath)
                .build();
    }

    public static FssaiResponse error(String message) {
        return FssaiResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
