package com.shoplocker.fssai.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import com.shoplocker.fssai.entity.Role;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "users")
@Data


public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message ="user name is required")
    private String userName;

    @NotBlank(message ="mobile number is required ")
    @Pattern(
            regexp = "^[0-9]{10}$",
            message="mobile number must be 10 digits"
    )
    @Column(unique=true)
    private String mobileNumber;


    @Email
    @Column(unique=true)
    private String emailId;


    @Enumerated(EnumType.STRING)
    private Role role=Role.MANAGER;

    // BCrypt-encoded password. Required by AuthService.register() and CustomUserDetailsService
    // (the latter reads it to build Spring Security's UserDetails). Never serialized to the
    // API client — AuthResponse explicitly does not include this field. Length is intentionally
    // generous (BCrypt hashes are ~60 chars; we leave headroom for future algorithm changes).
    @Column(length = 100)
    private String password;

    // Whether the account is active. Defaulted to true so any pre-existing/new user is enabled
    // out of the box; AuthService.register() sets this to true explicitly. CustomUserDetailsService
    // maps !enabled to Spring's UserDetails.disabled so the DaoAuthenticationProvider refuses
    // signing-in BEFORE running the BCrypt compare (no timing-attack probe on stale hashes).
    @Column(nullable = false)
    private boolean enabled = true;


    //getters and setters


    public Long getId() {
        return id;
    }

    public String getEmailId() {
        return emailId;
    }
    public void setEmailId(String emailId){
        this.emailId=emailId;
    }


    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName){
        this.userName =userName;
    }

    public Role getRole(){
        return role;
    }
    public  void setRole(Role role){
        this.role=role;
    }
}



