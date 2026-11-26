package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.EmailUpdateRequest;
import com.shoplocker.fssai.dto.MobileUpdateRequest;
import com.shoplocker.fssai.entity.OtpChallenge;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Verified self-service changes to the owner's email and mobile number. */
@Service
public class ContactUpdateService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final EmailVerificationCodeService emailCodeService;
    private final OtpService otpService;

    public ContactUpdateService(UserRepository userRepository,
                                ShopRepository shopRepository,
                                EmailVerificationCodeService emailCodeService,
                                OtpService otpService) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.emailCodeService = emailCodeService;
        this.otpService = otpService;
    }

    public Map<String, Object> requestEmailCode(User user, String email) {
        String normalized = normalizeEmail(email);
        assertEmailFree(user, normalized);
        emailCodeService.sendCode(normalized);
        return status(user);
    }

    @Transactional
    public Map<String, Object> updateEmail(User user, EmailUpdateRequest request) {
        String normalized = normalizeEmail(request.email());
        assertEmailFree(user, normalized);
        emailCodeService.verifyCode(normalized, request.code());
        user.setEmailId(normalized);
        user.setContactEmailPending(false);
        userRepository.save(user);
        emailCodeService.consume(normalized);
        return status(user);
    }

    public Map<String, Object> requestMobileOtp(User user, String mobile) {
        assertMobileFree(user, mobile);
        OtpService.OtpRequestResult result = otpService.requestOtp(
                OtpChallenge.PURPOSE_MOBILE_UPDATE, null, mobile);
        Map<String, Object> response = status(user);
        response.put("requestId", result.requestId());
        response.put("resendAvailableInSeconds", result.resendAvailableInSeconds());
        if (result.devOtp() != null) {
            response.put("devOtp", result.devOtp());
        }
        return response;
    }

    @Transactional
    public Map<String, Object> updateMobile(User user, MobileUpdateRequest request) {
        assertMobileFree(user, request.mobile());
        otpService.verifyOtp(OtpChallenge.PURPOSE_MOBILE_UPDATE, request.mobile(), null, request.otp());
        user.setMobileNumber(request.mobile());
        user.setContactMobilePending(false);
        userRepository.save(user);
        List<Shop> shops = shopRepository.findByOwnerId(user.getId());
        for (Shop shop : shops) {
            shop.setMobile(request.mobile());
            shopRepository.save(shop);
        }
        return status(user);
    }

    private void assertEmailFree(User user, String email) {
        User existing = userRepository.findByEmailId(email).orElse(null);
        if (existing != null && !existing.getId().equals(user.getId())) {
            throw new FssaiException("Email already registered", FailureCode.DUPLICATE_EMAIL);
        }
    }

    private void assertMobileFree(User user, String mobile) {
        User existing = userRepository.findByMobileNumber(mobile).orElse(null);
        if (existing != null && !existing.getId().equals(user.getId())) {
            throw new FssaiException("Mobile number already registered", FailureCode.DUPLICATE_MOBILE);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> status(User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("emailId", user.getEmailId());
        response.put("mobileNumber", user.getMobileNumber());
        response.put("emailPending", user.isContactEmailPending());
        response.put("mobilePending", user.isContactMobilePending());
        return response;
    }
}
