package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.MobileLoginSendOtpResponse;
import com.shoplocker.fssai.dto.MobileLoginSetPasswordResponse;
import com.shoplocker.fssai.entity.OtpChallenge;
import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobileLoginService {

    private static final Logger log = LoggerFactory.getLogger(MobileLoginService.class);

    private static final String PASSWORD_RESET_REQUEST_MESSAGE =
            "If this mobile number is registered, an OTP has been sent to it.";

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public MobileLoginService(UserRepository userRepository,
                              OtpService otpService,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Sends an OTP to the given mobile if an account exists with it.
     * Returns whether the account needs a password set on first use.
     * The response is identical whether or not the account exists, so the
     * endpoint cannot be used to enumerate registered mobiles.
     */
    @Transactional(readOnly = true)
    public MobileLoginSendOtpResponse sendOtp(String mobileNumber) {
        String mobile = mobileNumber == null ? null : mobileNumber.trim();
        User user = mobile == null ? null : userRepository.findByMobileNumber(mobile).orElse(null);

        if (user == null || user.isMsmeUser() || !user.isEnabled()) {
            log.info("Mobile login OTP rejected: no eligible account for mobile {}", maskMobile(mobile));
            return new MobileLoginSendOtpResponse(
                    false, false, 0, 0, null, PASSWORD_RESET_REQUEST_MESSAGE);
        }

        OtpService.OtpRequestResult result = otpService.requestOtp(
                OtpChallenge.PURPOSE_MOBILE_LOGIN, null, mobile);

        boolean passwordChangeRequired = !user.isPasswordChanged();
        log.info("Mobile login OTP sent to user {} (mobile {})", user.getId(), maskMobile(mobile));

        return new MobileLoginSendOtpResponse(
                true,
                passwordChangeRequired,
                result.remainingSends(),
                result.resendAvailableInSeconds(),
                result.devOtp(),
                PASSWORD_RESET_REQUEST_MESSAGE);
    }

    /**
     * Verifies the OTP and sets a new password for the account bound to the
     * mobile. Marks {@code passwordChanged = true} so the first-login popup
     * stops showing, then issues a JWT.
     */
    @Transactional
    public MobileLoginSetPasswordResponse verifyAndSetPassword(String mobileNumber, String otp,
                                                               String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new FssaiException("Password and confirmation do not match", FailureCode.INVALID_REQUEST);
        }
        if (password == null || password.length() < 8) {
            throw new FssaiException("Password must be at least 8 characters", FailureCode.INVALID_REQUEST);
        }

        String mobile = mobileNumber == null ? null : mobileNumber.trim();

        // Verify OTP before touching the account — proves control of the mobile.
        otpService.verifyOtp(OtpChallenge.PURPOSE_MOBILE_LOGIN, mobile, null, otp);

        User user = userRepository.findByMobileNumber(mobile)
                .orElseThrow(() -> {
                    log.warn("Mobile login: OTP verified but no user for mobile {}", maskMobile(mobile));
                    return new FssaiException("Account not found.", FailureCode.USER_NOT_FOUND);
                });

        if (user.isMsmeUser() || !user.isEnabled()) {
            throw new FssaiException(
                    "This account cannot log in with a mobile number and password.",
                    FailureCode.INVALID_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordChanged(true);
        userRepository.save(user);

        // Clear the OTP send budget so a legitimate owner is not locked out.
        otpService.clearRateLimit(mobile);

        String token = jwtService.generateToken(user);
        log.info("Mobile login password set and JWT issued for userId={} mobile={}",
                user.getId(), maskMobile(mobile));

        return new MobileLoginSetPasswordResponse(
                true,
                "Password set successfully",
                token,
                "Bearer",
                user.getId(),
                user.getUserName(),
                user.getMobileNumber(),
                user.getEmailId(),
                user.getRole().name());
    }

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "***";
        }
        return mobile.substring(0, 2) + "***" + mobile.substring(mobile.length() - 3);
    }
}
