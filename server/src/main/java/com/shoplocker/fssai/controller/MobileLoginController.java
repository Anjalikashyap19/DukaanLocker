package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.MobileLoginSendOtpRequest;
import com.shoplocker.fssai.dto.MobileLoginSendOtpResponse;
import com.shoplocker.fssai.dto.MobileLoginSetPasswordRequest;
import com.shoplocker.fssai.dto.MobileLoginSetPasswordResponse;
import com.shoplocker.fssai.service.MobileLoginService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile-number login flow for auto-created FSSAI owners who never received
 * credentials by email/WhatsApp.
 *
 * <ol>
 *   <li>Client sends mobile number → server sends OTP to that mobile.</li>
 *   <li>Client verifies OTP and sets a new password (+ confirmation).</li>
 *   <li>Server marks {@code passwordChanged = true} and issues a JWT.</li>
 * </ol>
 *
 * <p>All endpoints bypass JWT auth ({@code /api/auth/**} is public).</p>
 */
@RestController
@RequestMapping("/api/auth/mobile-login")
public class MobileLoginController {

    private final MobileLoginService mobileLoginService;

    public MobileLoginController(MobileLoginService mobileLoginService) {
        this.mobileLoginService = mobileLoginService;
    }

    /**
     * Step 1: send an OTP to the registered mobile. Returns whether the account
     * needs a password set on first use.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<MobileLoginSendOtpResponse> sendOtp(
            @Valid @RequestBody MobileLoginSendOtpRequest request) {
        return ResponseEntity.ok(mobileLoginService.sendOtp(request.mobileNumber()));
    }

    /**
     * Step 2: verify the OTP and set a new password. Marks the account's
     * password as changed and issues a JWT on success.
     */
    @PostMapping("/verify-and-set-password")
    public ResponseEntity<MobileLoginSetPasswordResponse> verifyAndSetPassword(
            @Valid @RequestBody MobileLoginSetPasswordRequest request) {
        return ResponseEntity.ok(
                mobileLoginService.verifyAndSetPassword(
                        request.mobileNumber(), request.otp(), request.password(), request.confirmPassword()));
    }
}
