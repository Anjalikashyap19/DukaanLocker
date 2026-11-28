package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.ChangePasswordSendOtpRequest;
import com.shoplocker.fssai.dto.ChangePasswordVerifyOtpRequest;
import com.shoplocker.fssai.dto.ForgotPasswordRequest;
import com.shoplocker.fssai.dto.MessageResponse;
import com.shoplocker.fssai.dto.OtpSendResponse;
import com.shoplocker.fssai.dto.ResetPasswordRequest;
import com.shoplocker.fssai.entity.OtpChallenge;
import com.shoplocker.fssai.service.AuthService;
import com.shoplocker.fssai.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Change-password flow shown to auto-created FSSAI owners on first login.
 * Steps: send OTP to registered mobile → verify OTP → set new password.
 * All endpoints bypass JWT auth ({@code /api/auth/**} is public).
 */
@RestController
@RequestMapping("/api/auth/change-password")
public class PasswordChangeController {

    private final AuthService authService;
    private final OtpService otpService;

    public PasswordChangeController(AuthService authService, OtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
    }

    /**
     * Step 1: validate email+mobile belong to the same account, then send an OTP
     * to the registered mobile.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<OtpSendResponse> sendOtp(@Valid @RequestBody ChangePasswordSendOtpRequest request) {
        ForgotPasswordRequest fp = new ForgotPasswordRequest();
        fp.setEmailId(request.emailId());
        fp.setMobileNumber(request.mobileNumber());
        OtpSendResponse response = authService.forgotPassword(fp);
        return ResponseEntity.ok(response);
    }

    /**
     * Step 2: verify the OTP sent to the registered mobile.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<MessageResponse> verifyOtp(
            @Valid @RequestBody ChangePasswordVerifyOtpRequest request) {
        otpService.verifyOtp(
                OtpChallenge.PURPOSE_PASSWORD_RESET, request.mobileNumber(), null, request.otp());
        return ResponseEntity.ok(new MessageResponse(true, "OTP verified"));
    }

    /**
     * Step 3: verify OTP again and set the new password. Marks the account's
     * password as changed so the first-login popup stops showing.
     */
    @PostMapping("/set-password")
    public ResponseEntity<MessageResponse> setPassword(@Valid @RequestBody ResetPasswordRequest request) {
        MessageResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }
}
