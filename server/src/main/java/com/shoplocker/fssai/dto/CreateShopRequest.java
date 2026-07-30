package com.shoplocker.fssai.dto;

import com.shoplocker.fssai.entity.BusinessScale;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a new shop.
 * Only ADMIN users can create shops. The owner is determined from JWT, not from request body.
 */
public class CreateShopRequest {

    @NotBlank(message = "shopName is required")
    private String shopName;

    @NotBlank(message = "ownerName is required")
    private String ownerName;

    @NotBlank(message = "mobile is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "mobile must be exactly 10 digits")
    private String mobile;

    @NotBlank(message = "category is required")
    private String category;

    @NotBlank(message = "scale is required")
    private String scale;

    @NotBlank(message = "state is required")
    private String state;

    @NotBlank(message = "city is required")
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
