package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.OtpChallenge;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.OtpChallengeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Generates, stores and verifies OTP challenges for the MSME login flow.
 *
 * <p>OTP lifecycle:</p>
 * <ul>
 *   <li>Request: any prior challenge for the mobile/purpose is deleted, a fresh
 *       OTP is generated, hashed (BCrypt) and stored, then delivered via SMS.</li>
 *   <li>Verify: checks expiry, attempt ceiling, and hash match; on success the
 *       challenge is marked consumed (single-use).</li>
 * </ul>
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository otpRepository;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.expiry-seconds:300}")
    private int expirySeconds;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    public OtpService(OtpChallengeRepository otpRepository,
                      SmsService smsService,
                      PasswordEncoder passwordEncoder) {
        this.otpRepository = otpRepository;
        this.smsService = smsService;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates a fresh OTP for the mobile and delivers it via SMS. Returns the challenge id. */
    @Transactional
    public String requestOtp(String msmeNumber, String mobile) {
        otpRepository.deleteByMobileAndPurpose(mobile, OtpChallenge.PURPOSE_MSME_LOGIN);

        String otp = generateOtp();
        OtpChallenge challenge = new OtpChallenge();
        challenge.setMsmeNumber(msmeNumber);
        challenge.setMobile(mobile);
        challenge.setOtpHash(passwordEncoder.encode(otp));
        challenge.setPurpose(OtpChallenge.PURPOSE_MSME_LOGIN);
        challenge.setExpiresAt(LocalDateTime.now().plusSeconds(expirySeconds));
        challenge.setAttempts(0);
        challenge.setVerified(false);
        OtpChallenge saved = otpRepository.save(challenge);

        smsService.sendOtp(mobile, otp);
        return saved.getId().toString();
    }

    /**
     * Verifies the OTP for the given mobile. Throws on missing/expired/used/wrong
     * OTP or when the attempt ceiling is hit. On success the challenge is consumed.
     */
    @Transactional
    public void verifyOtp(String mobile, String otp) {
        OtpChallenge challenge = otpRepository
                .findTopByMobileAndPurposeOrderByCreatedAtDesc(mobile, OtpChallenge.PURPOSE_MSME_LOGIN)
                .orElseThrow(() -> new FssaiException(
                        "Invalid or expired OTP. Please request a new one.",
                        FailureCode.INVALID_OTP));

        if (challenge.isVerified()) {
            otpRepository.delete(challenge);
            throw new FssaiException(
                    "This OTP has already been used. Please request a new one.",
                    FailureCode.INVALID_OTP);
        }

        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.delete(challenge);
            throw new FssaiException(
                    "This OTP has expired. Please request a new one.",
                    FailureCode.OTP_EXPIRED);
        }

        if (challenge.getAttempts() >= maxAttempts) {
            otpRepository.delete(challenge);
            throw new FssaiException(
                    "Too many incorrect attempts. Please request a new OTP.",
                    FailureCode.TOO_MANY_ATTEMPTS);
        }

        if (!passwordEncoder.matches(otp, challenge.getOtpHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            otpRepository.save(challenge);
            throw new FssaiException(
                    "Incorrect OTP. Please try again.",
                    FailureCode.INVALID_OTP);
        }

        // Consume the challenge (single-use).
        challenge.setVerified(true);
        otpRepository.save(challenge);
        log.info("OTP verified for mobile {}", mobile);
    }

    private String generateOtp() {
        int bound = (int) Math.pow(10, otpLength);
        int value = RANDOM.nextInt(bound);
        return String.format("%0" + otpLength + "d", value);
    }
}
