package com.shoplocker.fssai.dto;

import com.shoplocker.fssai.entity.BusinessScale;

import java.time.LocalDateTime;

/**
 * Response DTO for shop data. Does NOT expose password or full User entity details.
 */
public class ShopResponse {

    private Long id;
    private String shopName;
    private String ownerName;
    private String mobile;
    private String category;
    private BusinessScale scale;
    private String state;
    private String city;
    private String branchName;
    private String address;
    private String pincode;
    private Long ownerUserId;
    private String ownerEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ShopResponse() {}

    public ShopResponse(Long id, String shopName, String ownerName, String mobile,
                        String category, BusinessScale scale, String state, String city,
                        String branchName, String address, String pincode,
                        Long ownerUserId, String ownerEmail,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.shopName = shopName;
        this.ownerName = ownerName;
        this.mobile = mobile;
        this.category = category;
        this.scale = scale;
        this.state = state;
        this.city = city;
        this.branchName = branchName;
        this.address = address;
        this.pincode = pincode;
        this.ownerUserId = ownerUserId;
        this.ownerEmail = ownerEmail;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BusinessScale getScale() { return scale; }
    public void setScale(BusinessScale scale) { this.scale = scale; }

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

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
