package com.shoplocker.fssai.service;

import com.shoplocker.fssai.config.Fast2SmsConfig;
import com.shoplocker.fssai.entity.OtpChallenge;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.OtpChallengeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    /**
     * Self-reference (via the Spring proxy) so {@link #persistChallenge} can
     * commit in its own transaction even though {@link #requestOtp} must stay
     * non-transactional (it makes an external HTTP call to the SMS gateway).
     * Without this, a direct {@code this.persistChallenge(...)} call would bypass
     * the transactional proxy and the challenge would be rolled back whenever the
     * gateway rejected the send.
     */
    @Lazy
    @Autowired
    private OtpService self;

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
     *  so the login flow stays completable without a real SMS gateway.
     *
     *  <p>Deliberately NOT {@code @Transactional}: the gateway call is external, and a
     *  transaction spanning it would roll the challenge back on failure. The challenge is
     *  committed first by {@link #persistChallenge}, so a rejected send still consumes the
     *  resend cooldown and the endpoint cannot be spammed while the gateway is failing.</p> */
    public OtpRequestResult requestOtp(String msmeNumber, String mobile) {
        String otp = generateOtp();
        OtpChallenge challenge = new OtpChallenge();
        challenge.setMsmeNumber(msmeNumber);
        challenge.setMobile(mobile);
        challenge.setOtpHash(passwordEncoder.encode(otp));
        challenge.setPurpose(OtpChallenge.PURPOSE_MSME_LOGIN);
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(fast2SmsConfig.getOtpExpiryMinutes()));
        challenge.setAttempts(0);
        challenge.setVerified(false);

        // Committed in its own transaction, so it survives a failed send below.
        OtpChallenge saved = self.persistChallenge(challenge);

        smsService.sendOtp(mobile, otp);
        return new OtpRequestResult(saved.getId().toString(), isDevMode() ? otp : null);
    }

    /**
     * Atomically enforces the resend cooldown and replaces the active challenge for the
     * mobile/purpose. Runs in its own transaction (invoked via the self-proxy) so the
     * stored challenge is durable before {@link #requestOtp} attempts the SMS send.
     *
     * <p>The cooldown lives here rather than in the caller so the read, the delete and
     * the insert are evaluated against one consistent view.</p>
     *
     * <p>{@code REQUIRES_NEW} keeps the challenge durable independently of whatever
     * transaction the caller may hold, so it cannot be rolled back by a later failure
     * in the request path.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OtpChallenge persistChallenge(OtpChallenge challenge) {
        String mobile = challenge.getMobile();

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
        return otpRepository.save(challenge);
    }

    private boolean isDevMode() {
        String key = fast2SmsConfig.getApiKey();
        String tpl = fast2SmsConfig.getTemplateId();
        return (key == null || key.isBlank()) || (tpl == null || tpl.isBlank());
    }

    /** Outcome of applying a single OTP verification attempt. */
    public enum OtpOutcome {
        VERIFIED, ALREADY_USED, EXPIRED, TOO_MANY_ATTEMPTS, INCORRECT
    }

    /**
     * Verifies the OTP for the given mobile + Udyam number. Throws on
     * missing/expired/used/wrong OTP or when the attempt ceiling is hit. On
     * success the challenge is consumed.
     *
     * <p>Deliberately NOT {@code @Transactional}: the attempt counter must survive
     * the exception that reports the failure. Incrementing it inside a transaction
     * that then throws would roll the increment back, leaving {@code attempts}
     * pinned at 0 and silently disabling the {@code maxAttempts} ceiling. The
     * mutation therefore happens in {@link #applyVerification} (own transaction,
     * always commits) and the exception is raised here, afterwards.</p>
     */
    public void verifyOtp(String mobile, String msmeNumber, String otp) {
        Long challengeId = otpRepository
                .findTopByMobileAndMsmeNumberAndPurposeOrderByCreatedAtDesc(
                        mobile, msmeNumber, OtpChallenge.PURPOSE_MSME_LOGIN)
                .map(OtpChallenge::getId)
                .orElseThrow(() -> new FssaiException(
                        "Invalid or expired OTP. Please request a new one.",
                        FailureCode.INVALID_OTP));

        OtpOutcome outcome = self.applyVerification(challengeId, otp);

        switch (outcome) {
            case ALREADY_USED -> throw new FssaiException(
                    "This OTP has already been used. Please request a new one.",
                    FailureCode.INVALID_OTP);
            case EXPIRED -> throw new FssaiException(
                    "This OTP has expired. Please request a new one.",
                    FailureCode.OTP_EXPIRED);
            case TOO_MANY_ATTEMPTS -> throw new FssaiException(
                    "Too many incorrect attempts. Please request a new OTP.",
                    FailureCode.TOO_MANY_ATTEMPTS);
            case INCORRECT -> throw new FssaiException(
                    "Incorrect OTP. Please try again.",
                    FailureCode.INVALID_OTP);
            case VERIFIED -> log.info("OTP verified for mobile {} udyam {}", mobile, msmeNumber);
        }
    }

    /**
     * Applies one verification attempt to the challenge and returns the outcome.
     *
     * <p>Runs in its own transaction (invoked via the self-proxy) and always returns
     * normally, so the attempt increment, the consumption on success, and the cleanup
     * deletes on the terminal failure paths are all committed. The caller raises the
     * user-facing exception afterwards, outside this transaction.</p>
     *
     * <p>{@code REQUIRES_NEW} is required, not merely {@code REQUIRED}: callers
     * ({@code AuthService.msmeLoginVerify}) are themselves transactional, and a
     * {@code REQUIRED} call would join their transaction and be rolled back along with
     * the thrown failure — silently restoring the very bug this guards against.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OtpOutcome applyVerification(Long challengeId, String submittedOtp) {
        OtpChallenge challenge = otpRepository.findById(challengeId)
                .orElseThrow(() -> new FssaiException(
                        "Invalid or expired OTP. Please request a new one.",
                        FailureCode.INVALID_OTP));

        if (challenge.isVerified()) {
            otpRepository.delete(challenge);
            return OtpOutcome.ALREADY_USED;
        }

        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.delete(challenge);
            return OtpOutcome.EXPIRED;
        }

        if (challenge.getAttempts() >= maxAttempts) {
            otpRepository.delete(challenge);
            return OtpOutcome.TOO_MANY_ATTEMPTS;
        }

        if (!passwordEncoder.matches(submittedOtp, challenge.getOtpHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            otpRepository.save(challenge);
            return OtpOutcome.INCORRECT;
        }

        // Consume the challenge (single-use).
        challenge.setVerified(true);
        otpRepository.save(challenge);
        return OtpOutcome.VERIFIED;
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
