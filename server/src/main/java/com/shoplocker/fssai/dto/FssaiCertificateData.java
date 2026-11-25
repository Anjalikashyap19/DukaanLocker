package com.shoplocker.fssai.dto;

/**
 * Fields read out of an FSSAI Form C licence PDF.
 *
 * <p>Owner contact details come from the "Person in charge of operations" block
 * on the Non-Form C annexure. Certificates without that block (or with it
 * masked) leave those fields null, and {@link #hasOwnerDetails()} is false so
 * no account is created for them.</p>
 */
public class FssaiCertificateData {

    private String licenseNumber;
    private String shopName;
    private String registeredAddress;
    private String premisesAddress;
    private String kindOfBusiness;
    private String licenseCategory;
    private String issuedOn;
    private String validUpto;

    private String ownerName;
    private String mobileNumber;
    private String emailId;
    private String state;
    private String district;
    private String pincode;

    /**
     * An account can only be created when the certificate identifies the owner:
     * a real name, a 10-digit mobile and an email to send the credentials to.
     * Anything missing means the file is skipped, never guessed.
     */
    public boolean hasOwnerDetails() {
        return isRealValue(ownerName)
                && mobileNumber != null
                && mobileNumber.matches("^[6-9]\\d{9}$")
                && emailId != null
                && emailId.contains("@");
    }

    public static boolean isRealValue(String value) {
        if (value == null) return false;
        String v = value.trim();
        return v.length() > 1
                && !v.equalsIgnoreCase("N/A")
                && !v.equalsIgnoreCase("NA")
                && !v.equals("-");
    }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getRegisteredAddress() { return registeredAddress; }
    public void setRegisteredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; }

    public String getPremisesAddress() { return premisesAddress; }
    public void setPremisesAddress(String premisesAddress) { this.premisesAddress = premisesAddress; }

    public String getKindOfBusiness() { return kindOfBusiness; }
    public void setKindOfBusiness(String kindOfBusiness) { this.kindOfBusiness = kindOfBusiness; }

    public String getLicenseCategory() { return licenseCategory; }
    public void setLicenseCategory(String licenseCategory) { this.licenseCategory = licenseCategory; }

    public String getIssuedOn() { return issuedOn; }
    public void setIssuedOn(String issuedOn) { this.issuedOn = issuedOn; }

    public String getValidUpto() { return validUpto; }
    public void setValidUpto(String validUpto) { this.validUpto = validUpto; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
}
