package com.shoplocker.fssai.dto;

/**
 * Holds the MSME enterprise data extracted from the Udyam certificate HTML.
 * Used during MSME auto-registration to populate User, Shop, and Document records.
 */
public class MsmeParsedData {

    private String udyamNumber;
    private String enterpriseName;
    private String entrepreneurName;
    private String mobileNumber;
    private String emailId;
    private String address;
    private String city;
    private String state;
    private String district;
    private String pincode;
    private String majorActivity;
    private String enterpriseType; // Micro / Small / Medium
    private String typeOfOrganization;

    public MsmeParsedData() {}

    // ─── Getters and Setters ───────────────────────────────────────────

    public String getUdyamNumber() { return udyamNumber; }
    public void setUdyamNumber(String udyamNumber) { this.udyamNumber = udyamNumber; }

    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }

    public String getEntrepreneurName() { return entrepreneurName; }
    public void setEntrepreneurName(String entrepreneurName) { this.entrepreneurName = entrepreneurName; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getMajorActivity() { return majorActivity; }
    public void setMajorActivity(String majorActivity) { this.majorActivity = majorActivity; }

    public String getEnterpriseType() { return enterpriseType; }
    public void setEnterpriseType(String enterpriseType) { this.enterpriseType = enterpriseType; }

    public String getTypeOfOrganization() { return typeOfOrganization; }
    public void setTypeOfOrganization(String typeOfOrganization) { this.typeOfOrganization = typeOfOrganization; }

    @Override
    public String toString() {
        return "MsmeParsedData{" +
                "udyamNumber='" + udyamNumber + '\'' +
                ", enterpriseName='" + enterpriseName + '\'' +
                ", entrepreneurName='" + entrepreneurName + '\'' +
                ", mobileNumber='" + mobileNumber + '\'' +
                ", emailId='" + emailId + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                ", district='" + district + '\'' +
                ", pincode='" + pincode + '\'' +
                ", majorActivity='" + majorActivity + '\'' +
                ", enterpriseType='" + enterpriseType + '\'' +
                '}';
    }
}
