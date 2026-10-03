package com.shoplocker.fssai.service;

import com.shoplocker.fssai.config.Fast2SmsConfig;
import com.shoplocker.fssai.entity.OtpChallenge;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.exception.OtpRateLimitedException;
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
    private final OtpRateLimitService rateLimitService;

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
                      Fast2SmsConfig fast2SmsConfig,
                      OtpRateLimitService rateLimitService) {
        this.otpRepository = otpRepository;
        this.smsService = smsService;
        this.passwordEncoder = passwordEncoder;
        this.fast2SmsConfig = fast2SmsConfig;
        this.rateLimitService = rateLimitService;
    }

    /** Creates a fresh OTP for the mobile and delivers it via SMS. Returns the challenge id
     *  and, when running in dev mode (Fast2SMS key/template unconfigured), the plaintext OTP
     *  so the login flow stays completable without a real SMS gateway.
     *
     *  <p>Deliberately NOT {@code @Transactional}: the gateway call is external, and a
     *  transaction spanning it would roll the challenge back on failure. The challenge is
     *  committed first by {@link #persistChallenge}, so a rejected send still consumes the
     *  resend cooldown and the endpoint cannot be spammed while the gateway is failing.</p>
     *
     *  <p>{@link OtpRateLimitService} is consulted <i>first</i>, before the challenge row is
     *  written and before the gateway is called, so a throttled request costs nothing and
     *  sends no SMS.</p> */
    public OtpRequestResult requestOtp(String msmeNumber, String mobile) {
        return requestOtp(OtpChallenge.PURPOSE_MSME_LOGIN, msmeNumber, mobile);
    }

    /** Same as {@link #requestOtp(String, String)} for an arbitrary flow ({@code purpose}). */
    public OtpRequestResult requestOtp(String purpose, String msmeNumber, String mobile) {
        OtpRateLimitService.Decision rateLimit = rateLimitService.acquireSendSlot(mobile);
        // Only fall back to the durable cooldown when Redis did not actually enforce
        // anything. Running both unconditionally meant two overlapping 120s windows: the
        // database check rejected the request after the Redis counter had already been
        // incremented, so a user could burn all 5 sends without receiving a single SMS,
        // and the rejection came back without retry metadata the client needs.
        if (rateLimit.degraded()) {
            self.assertDbCooldown(mobile, purpose);
        }

        String otp = generateOtp();
        OtpChallenge challenge = new OtpChallenge();
        challenge.setMsmeNumber(msmeNumber);
        challenge.setMobile(mobile);
        challenge.setOtpHash(passwordEncoder.encode(otp));
        challenge.setPurpose(purpose);
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(fast2SmsConfig.getOtpExpiryMinutes()));
        challenge.setAttempts(0);
        challenge.setVerified(false);

        // Committed in its own transaction, so it survives a failed send below.
        OtpChallenge saved = self.persistChallenge(challenge);

        try {
            if (OtpChallenge.PURPOSE_PASSWORD_RESET.equals(purpose)) {
                smsService.sendPasswordResetOtp(mobile, otp);
            } else {
                smsService.sendOtp(mobile, otp);
            }
        } catch (OtpRateLimitedException e) {
            // The gateway applied its own resend cooldown. The request was valid and no
            // SMS was billed, so hand the send credit back instead of letting a timing
            // race between our window and the gateway's eat into the user's 5-send cap.
            rateLimitService.releaseSendSlot(mobile);
            throw e;
        }
        return new OtpRequestResult(
                saved.getId().toString(),
                isDevMode(purpose) ? otp : null,
                rateLimit.remainingSends(),
                rateLimit.retryAfterSeconds());
    }

    /**
     * Durable fallback for the resend cooldown, used when Redis is unavailable and
     * {@link OtpRateLimitService} fails open.
     *
     * <p>Deliberately evaluated <i>before</i> {@code acquireSendSlot}: the Redis counter
     * models real sends, so a request rejected here must not consume a send credit. When
     * this check used to live inside {@link #persistChallenge} it ran after the counter
     * had already been incremented, which burned the user's whole 5-send budget without
     * ever delivering a single SMS.</p>
     *
     * <p>Throws {@link OtpRateLimitedException} rather than a bare
     * {@link FssaiException} so this path returns the same retry metadata as the Redis
     * path. A 429 without {@code retryAfterSeconds} leaves the client unable to render a
     * countdown, which is precisely the case this feature exists to fix.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void assertDbCooldown(String mobile) {
        assertDbCooldown(mobile, OtpChallenge.PURPOSE_MSME_LOGIN);
    }

    /** Durable resend-cooldown fallback scoped to a single flow. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void assertDbCooldown(String mobile, String purpose) {
        otpRepository.findTopByMobileAndPurposeOrderByCreatedAtDesc(mobile, purpose)
                .ifPresent(recent -> {
                    long ageSeconds = Duration.between(recent.getCreatedAt(), LocalDateTime.now()).getSeconds();
                    if (ageSeconds < resendCooldownSeconds) {
                        int wait = (int) (resendCooldownSeconds - ageSeconds);
                        throw new OtpRateLimitedException(
                                "Please wait " + wait + " seconds before requesting a new OTP.",
                                wait, rateLimitService.peekRemainingSends(mobile), false);
                    }
                });
    }

    /**
     * Atomically enforces the resend cooldown and replaces the active challenge for the
     * mobile/purpose. Runs in its own transaction (invoked via the self-proxy) so the
     * stored challenge is durable before {@link #requestOtp} attempts the SMS send.
     *
     * <p>The cooldown check lives in {@link #assertDbCooldown} and runs before the rate
     * limiter, so this method only needs to delete the previous challenge and insert the
     * new one.</p>
     *
     * <p>{@code REQUIRES_NEW} keeps the challenge durable independently of whatever
     * transaction the caller may hold, so it cannot be rolled back by a later failure
     * in the request path.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OtpChallenge persistChallenge(OtpChallenge challenge) {
        // Scoped to the challenge's own flow so a password-reset send never
        // clobbers an active MSME-login challenge for the same mobile (and vice versa).
        otpRepository.deleteByMobileAndPurpose(challenge.getMobile(), challenge.getPurpose());
        return otpRepository.save(challenge);
    }

    /**
     * Whether the OTP may be echoed back to the client because no SMS can possibly
     * leave the gateway.
     *
     * <p>Deliberately gated on the API key alone. A configured key with a missing
     * template id means a real deployment with a misconfiguration: echoing the OTP
     * there would let anyone read it from the response and take over the account,
     * so that path fails loudly instead (see {@link SmsService#sendPasswordResetOtp}).</p>
     */
    private boolean isDevMode(String purpose) {
        String key = fast2SmsConfig.getApiKey();
        if (key == null || key.isBlank()) return true;
        if (OtpChallenge.PURPOSE_PASSWORD_RESET.equals(purpose)) return false;
        String tpl = fast2SmsConfig.getTemplateId();
        return tpl == null || tpl.isBlank();
    }

    /**
     * Clears cooldown/send-cap/lockout state for a mobile. Called after a successful
     * verification so a legitimate owner is not punished for earlier resends.
     */
    public void clearRateLimit(String mobile) {
        rateLimitService.reset(mobile);
    }

    /** Outcome of applying a single OTP verification attempt. */
    public enum OtpOutcome {
        VERIFIED, ALREADY_USED, EXPIRED, TOO_MANY_ATTEMPTS, INCORRECT
    }

    /**
     * Verifies the MSME-login OTP for the given mobile + Udyam number.
     *
     * @see #verifyOtp(String, String, String, String)
     */
    public void verifyOtp(String mobile, String msmeNumber, String otp) {
        verifyOtp(OtpChallenge.PURPOSE_MSME_LOGIN, mobile, msmeNumber, otp);
    }

    /**
     * Verifies the OTP for the given flow ({@code purpose}). Throws on
     * missing/expired/used/wrong OTP or when the attempt ceiling is hit. On
     * success the challenge is consumed.
     *
     * <p>When {@code msmeNumber} is {@code null} (password reset) the challenge is
     * looked up by mobile + purpose alone; otherwise it is additionally bound to the
     * Udyam number so an OTP issued for one MSME account cannot be used against
     * another account that shares the mobile.</p>
     *
     * <p>Deliberately NOT {@code @Transactional}: the attempt counter must survive
     * the exception that reports the failure. Incrementing it inside a transaction
     * that then throws would roll the increment back, leaving {@code attempts}
     * pinned at 0 and silently disabling the {@code maxAttempts} ceiling. The
     * mutation therefore happens in {@link #applyVerification} (own transaction,
     * always commits) and the exception is raised here, afterwards.</p>
     */
    public void verifyOtp(String purpose, String mobile, String msmeNumber, String otp) {
        Long challengeId = (msmeNumber == null || msmeNumber.isBlank()
                        ? otpRepository.findTopByMobileAndPurposeOrderByCreatedAtDesc(mobile, purpose)
                        : otpRepository.findTopByMobileAndMsmeNumberAndPurposeOrderByCreatedAtDesc(
                                mobile, msmeNumber, purpose))
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
            case VERIFIED -> log.info("OTP verified for mobile {} purpose {} udyam {}", mobile, purpose, msmeNumber);
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

    /** Result of {@link #requestOtp}: challenge id, the plaintext OTP when in dev mode,
     *  and the post-send rate-limit state so the client can start its countdown. */
    public record OtpRequestResult(String requestId, String devOtp,
                                   int remainingSends, int resendAvailableInSeconds) {}
}
