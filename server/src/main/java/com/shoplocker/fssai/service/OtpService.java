package com.shoplocker.fssai.service;

import com.shoplocker.fssai.config.Fast2SmsConfig;
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
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Generates, stores and verifies OTP challenges for the MSME login flow.
 *
 * <p>OTP lifecycle:</p>
 * <ul>
 *   <li>Request: a resend cooldown is enforced (one OTP per window) to prevent
 *       OTP bombing / SMS cost abuse; any prior challenge for the mobile/purpose
 *       is deleted, a fresh OTP is generated, hashed (BCrypt) and stored, then
 *       delivered via SMS.</li>
 *   <li>Verify: checks expiry, attempt ceiling, and hash match; on success the
 *       challenge is marked consumed (single-use). Verification is bound to the
 *       Udyam number as well as the mobile, so an OTP issued for one MSME account
 *       cannot be used to log into a different account that happens to share a
 *       mobile number.</li>
 * </ul>
 *
 * <p>OTP length and expiry are the single source of truth from
 * {@link Fast2SmsConfig} so the gateway's accepted window and the server's
 * stored window can never diverge.</p>
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository otpRepository;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;
    private final Fast2SmsConfig fast2SmsConfig;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${otp.resend-cooldown-seconds:30}")
    private int resendCooldownSeconds;

    public OtpService(OtpChallengeRepository otpRepository,
                      SmsService smsService,
                      PasswordEncoder passwordEncoder,
                      Fast2SmsConfig fast2SmsConfig) {
        this.otpRepository = otpRepository;
        this.smsService = smsService;
        this.passwordEncoder = passwordEncoder;
        this.fast2SmsConfig = fast2SmsConfig;
    }

    /** Creates a fresh OTP for the mobile and delivers it via SMS. Returns the challenge id
     *  and, when running in dev mode (Fast2SMS key/template unconfigured), the plaintext OTP
     *  so the login flow stays completable without a real SMS gateway. */
    @Transactional
    public OtpRequestResult requestOtp(String msmeNumber, String mobile) {
        // Resend cooldown: throttle OTP generation to one per resendCooldownSeconds
        // to prevent OTP bombing / SMS cost abuse.
        otpRepository.findTopByMobileAndPurposeOrderByCreatedAtDesc(mobile, OtpChallenge.PURPOSE_MSME_LOGIN)
                .ifPresent(recent -> {
                    long ageSeconds = Duration.between(recent.getCreatedAt(), LocalDateTime.now()).getSeconds();
                    if (ageSeconds < resendCooldownSeconds) {
                        throw new FssaiException(
                                "Please wait " + (resendCooldownSeconds - ageSeconds) +
                                        " seconds before requesting a new OTP.",
                                FailureCode.TOO_MANY_ATTEMPTS);
                    }
                });

        otpRepository.deleteByMobileAndPurpose(mobile, OtpChallenge.PURPOSE_MSME_LOGIN);

        String otp = generateOtp();
        OtpChallenge challenge = new OtpChallenge();
        challenge.setMsmeNumber(msmeNumber);
        challenge.setMobile(mobile);
        challenge.setOtpHash(passwordEncoder.encode(otp));
        challenge.setPurpose(OtpChallenge.PURPOSE_MSME_LOGIN);
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(fast2SmsConfig.getOtpExpiryMinutes()));
        challenge.setAttempts(0);
        challenge.setVerified(false);
        OtpChallenge saved = otpRepository.save(challenge);

        smsService.sendOtp(mobile, otp);
        return new OtpRequestResult(saved.getId().toString(), isDevMode() ? otp : null);
    }

    private boolean isDevMode() {
        String key = fast2SmsConfig.getApiKey();
        String tpl = fast2SmsConfig.getTemplateId();
        return (key == null || key.isBlank()) || (tpl == null || tpl.isBlank());
    }

    /**
     * Verifies the OTP for the given mobile + Udyam number. Throws on
     * missing/expired/used/wrong OTP or when the attempt ceiling is hit. On
     * success the challenge is consumed.
     */
    @Transactional
    public void verifyOtp(String mobile, String msmeNumber, String otp) {
        OtpChallenge challenge = otpRepository
                .findTopByMobileAndMsmeNumberAndPurposeOrderByCreatedAtDesc(
                        mobile, msmeNumber, OtpChallenge.PURPOSE_MSME_LOGIN)
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
        log.info("OTP verified for mobile {} udyam {}", mobile, msmeNumber);
    }

    private String generateOtp() {
        int length = fast2SmsConfig.getOtpLength();
        int bound = (int) Math.pow(10, length);
        int value = RANDOM.nextInt(bound);
        return String.format("%0" + length + "d", value);
    }

    /** Result of {@link #requestOtp}: challenge id plus the plaintext OTP when in dev mode. */
    public record OtpRequestResult(String requestId, String devOtp) {}
}
