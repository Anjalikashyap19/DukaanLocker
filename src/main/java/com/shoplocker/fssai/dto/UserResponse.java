package com.shoplocker.fssai.dto;

import com.shoplocker.fssai.entity.Role;
import java.time.LocalDateTime;
import java.util.List;
import com.shoplocker.fssai.dto.ShopResponse;
public class UserResponse {

    private Long id;
    private String userName;
    private String mobileNumber;
    private String emailId;
    private Role role;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ShopResponse> shops;
    public UserResponse() {
    }

    public UserResponse(
            Long id,
            String userName,
            String mobileNumber,
            String emailId,
            Role role,
            boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<ShopResponse> shops
    )
    {
        this.id = id;
        this.userName = userName;
        this.mobileNumber = mobileNumber;
        this.emailId = emailId;
        this.role = role;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.shops = shops;
    }

    public List<ShopResponse> getShops() {
        return shops;
    }

    public void setShops(List<ShopResponse> shops) {
        this.shops = shops;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getEmailId() {
        return emailId;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}