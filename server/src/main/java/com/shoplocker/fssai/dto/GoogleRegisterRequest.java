package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Google Sign-Up registration payload.
 * Used when a new user registers via Google account.
 * No password required — Firebase handles authentication.
 */
public class GoogleRegisterRequest {

    @NotBlank(message = "firebaseUid is required")
    private String firebaseUid;

    @NotBlank(message = "userName is required")
    private String userName;

    @NotBlank(message = "emailId is required")
    @Email(message = "emailId must be a valid email address")
    private String emailId;

    @NotBlank(message = "idToken is required")
    private String idToken;

    private String mobileNumber;

    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String firebaseUid) { this.firebaseUid = firebaseUid; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
}
