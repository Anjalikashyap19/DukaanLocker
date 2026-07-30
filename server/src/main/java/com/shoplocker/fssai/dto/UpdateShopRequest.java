package com.shoplocker.fssai.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for updating an existing shop.
 * All fields are optional — only provided fields will be updated.
 */
public class UpdateShopRequest {

    private String shopName;

    private String ownerName;

    @Pattern(regexp = "^[0-9]{10}$", message = "mobile must be exactly 10 digits")
    private String mobile;

    private String category;

    private String scale;

    private String state;

    private String city;

    private String branchName;

    private String address;

    @Pattern(regexp = "^[0-9]{6}$", message = "pincode must be exactly 6 digits")
    private String pincode;

    // Getters and Setters

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getScale() { return scale; }
    public void setScale(String scale) { this.scale = scale; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
}
