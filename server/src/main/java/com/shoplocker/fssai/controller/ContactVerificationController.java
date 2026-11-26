package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.EmailUpdateRequest;
import com.shoplocker.fssai.dto.EmailVerificationRequest;
import com.shoplocker.fssai.dto.MobileUpdateRequest;
import com.shoplocker.fssai.dto.MobileVerificationRequest;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.ContactUpdateService;
import com.shoplocker.fssai.service.ShopAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Contact verification", description = "Verified self-service email and mobile updates")
@RestController
@RequestMapping("/api/users/me")
public class ContactVerificationController {

    private final ContactUpdateService contactUpdateService;
    private final ShopAccessService shopAccessService;

    public ContactVerificationController(ContactUpdateService contactUpdateService,
                                         ShopAccessService shopAccessService) {
        this.contactUpdateService = contactUpdateService;
        this.shopAccessService = shopAccessService;
    }

    @Operation(summary = "Send a verification code to a new email address")
    @PostMapping("/email/code")
    public ResponseEntity<Map<String, Object>> requestEmailCode(
            @Valid @RequestBody EmailVerificationRequest request) {
        return ResponseEntity.ok(contactUpdateService.requestEmailCode(currentUser(), request.email()));
    }

    @Operation(summary = "Change email after verifying the code sent to it")
    @PutMapping("/email")
    public ResponseEntity<Map<String, Object>> updateEmail(
            @Valid @RequestBody EmailUpdateRequest request) {
        return ResponseEntity.ok(contactUpdateService.updateEmail(currentUser(), request));
    }

    @Operation(summary = "Send a Fast2SMS OTP to a new mobile number")
    @PostMapping("/mobile/code")
    public ResponseEntity<Map<String, Object>> requestMobileOtp(
            @Valid @RequestBody MobileVerificationRequest request) {
        return ResponseEntity.ok(contactUpdateService.requestMobileOtp(currentUser(), request.mobile()));
    }

    @Operation(summary = "Change mobile number after verifying the Fast2SMS OTP")
    @PutMapping("/mobile")
    public ResponseEntity<Map<String, Object>> updateMobile(
            @Valid @RequestBody MobileUpdateRequest request) {
        return ResponseEntity.ok(contactUpdateService.updateMobile(currentUser(), request));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return shopAccessService.getAuthenticatedUser(auth);
    }
}
