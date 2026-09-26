package com.shoplocker.fssai.service;

import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.exception.OtpRateLimitedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed rate limiter for OTP sends on the MSME login flow.
 *
 * <p>Every SMS costs money and lands on a real handset, so the send endpoint is the
 * most attractive target in the auth flow: it is public, unauthenticated, and each
 * accepted call bills a message. Three independent limits are enforced per destination
 * mobile number:</p>
 * <ol>
 *   <li><b>Resend cooldown</b> — after a successful send, no further send to the same
 *       mobile for {@code resendCooldownSeconds}. Stops rapid tapping and accidental
 *       message floods.</li>
 *   <li><b>Send cap</b> — at most {@code maxSends} sends inside the rolling window.
 *       Bounds the damage from a script that spaces its requests to respect the
 *       cooldown.</li>
 *   <li><b>Lockout</b> — once the cap is exceeded, sends are refused outright for
 *       {@code lockoutSeconds} and the client is told how long is left so it can show
 *       a countdown instead of a dead button.</li>
 * </ol>
 *
 * <p>State lives in Redis rather than in-process memory (as {@link LoginAttemptService}
 * does) so the limits hold across replicas and survive a restart — a server-side limit
 * that a restart clears is not a limit.</p>
 *
 * <h3>Keys</h3>
 * <ul>
 *   <li>{@code otp:rl:cooldown:{mobile}} — presence marker, TTL = cooldown</li>
 *   <li>{@code otcode counter}: {@code otp:rl:count:{mobile}} — sends in the window,
 *       TTL refreshed on every increment so an actively-resending user always trips
 *       the cap</li>
 *   <li>{@code otp:rl:lock:{mobile}} — lockout marker, TTL = lockout</li>
 * </ul>
 *
 * <h3>Failure policy</h3>
 * <p><b>Fails open.</b> If Redis is unreachable the limits are skipped and logged at
 * ERROR rather than propagated. Rationale: the alternative is that a Redis outage
 * locks every user out of MSME login, and the cost of under-enforcing here is bounded
 * (one extra SMS during the outage, still subject to the durable DB-side cooldown in
 * {@link OtpService#persistChallenge} and to the gateway's own spend limits), whereas
 * the cost of over-enforcing is a total login outage. This mirrors the existing
 * fail-open precedent in {@code JwtAuthenticationFilter}.</p>
 */
@Service
public class OtpRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(OtpRateLimitService.class);

    private static final String COOLDOWN_PREFIX = "otp:rl:cooldown:";
    private static final String COUNT_PREFIX = "otp:rl:count:";
    private static final String LOCK_PREFIX = "otp:rl:lock:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${otp.resend-cooldown-seconds:120}")
    private int cooldownSeconds;

    @Value("${otp.max-sends:5}")
    private int maxSends;

    @Value("${otp.lockout-seconds:600}")
    private int lockoutSeconds;

    public OtpRateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Outcome of a rate-limit check, as surfaced to the client.
     *
     * @param degraded {@code true} when the decision came from the fail-open path
     *                 because Redis was unreachable, meaning no counter was actually
     *                 recorded. Callers use this to decide whether the durable database
     *                 cooldown still needs to be applied as a fallback.
     */
    public record Decision(boolean allowed, boolean locked, int retryAfterSeconds,
                           int remainingSends, boolean degraded) {

        public static Decision allowed(int remainingSends, int cooldownSeconds) {
            return new Decision(true, false, cooldownSeconds, remainingSends, false);
        }

        /**
         * @param locked {@code true} for the hard lockout, {@code false} for the short
         *               resend cooldown. Explicit rather than inferred from the duration,
         *               so the client can label a 10-minute block differently from a
         *               2-minute wait without magic thresholds.
         */
        public static Decision blocked(boolean locked, int retryAfterSeconds, int remainingSends) {
            return new Decision(false, locked, retryAfterSeconds, remainingSends, false);
        }

        /** Allow, but record that Redis never enforced anything for this request. */
        public static Decision failOpen(int remainingSends, int cooldownSeconds) {
            return new Decision(true, false, cooldownSeconds, remainingSends, true);
        }
    }

    /**
     * Evaluates the rate limit for a send to {@code mobile} and, when allowed, records
     * the send against the cap.
     *
     * <p>Must be called <i>before</i> the SMS is dispatched, so a rejected request
     * never bills a message.</p>
     *
     * @throws OtpRateLimitedException (HTTP 429, {@code too_many_attempts}) when
     *         the caller is inside the cooldown or the lockout window.
     */
    public Decision acquireSendSlot(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return Decision.allowed(maxSends, cooldownSeconds);
        }
        try {
            Decision decision = evaluate(mobile);
            if (!decision.allowed()) {
                throw new OtpRateLimitedException(
                        decision.locked()
                                ? "Too many OTP requests. Please try again in "
                                    + formatDuration(decision.retryAfterSeconds()) + "."
                                : "Please wait " + decision.retryAfterSeconds()
                                    + " seconds before requesting a new OTP.",
                        decision.retryAfterSeconds(),
                        decision.remainingSends(),
                        decision.locked());
            }
            return decision;
        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            // Fail open: a Redis outage must not take MSME login down with it. The
            // degraded flag tells the caller to still apply the durable DB cooldown,
            // which is what actually protects the endpoint while Redis is down.
            log.error("OTP rate limiter unavailable, allowing send for mobile {}: {}",
                    mask(mobile), e.getMessage(), e);
            return Decision.failOpen(maxSends, cooldownSeconds);
        }
    }

    private Decision evaluate(String mobile) {
        String lockKey = LOCK_PREFIX + mobile;
        String cooldownKey = COOLDOWN_PREFIX + mobile;
        String countKey = COUNT_PREFIX + mobile;

        // Hard lockout wins over everything else.
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            long remaining = ttlSeconds(lockKey);
            if (remaining > 0) {
                return Decision.blocked(true, (int) remaining, 0);
            }
            // Expired between checks — clean up and treat as unlocked.
            redisTemplate.delete(lockKey);
        }

        // Resend cooldown. Still report the true remaining send budget so the client
        // can warn "last send" rather than implying the cap has already been hit.
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            long remaining = ttlSeconds(cooldownKey);
            if (remaining > 0) {
                return Decision.blocked(false, (int) remaining, remainingSends(countKey));
            }
            redisTemplate.delete(cooldownKey);
        }

        // Count this send against the cap. The TTL is refreshed on every increment so
        // a user who keeps resending inside the cooldown still trips the cap instead of
        // sliding past it on a stale window.
        Long count = redisTemplate.opsForValue().increment(countKey);
        long sendCount = count == null ? 1L : count;
        redisTemplate.expire(countKey, lockoutSeconds, TimeUnit.SECONDS);

        if (sendCount > maxSends) {
            // Cap exceeded: lock the number out for the full lockout window and start
            // the window over so the next send after it clears starts from a clean slate.
            redisTemplate.opsForValue().set(lockKey, "1", lockoutSeconds, TimeUnit.SECONDS);
            redisTemplate.delete(countKey);
            return Decision.blocked(true, lockoutSeconds, 0);
        }

        redisTemplate.opsForValue().set(cooldownKey, "1", cooldownSeconds, TimeUnit.SECONDS);
        return Decision.allowed((int) (maxSends - sendCount), cooldownSeconds);
    }

    private int remainingSends(String countKey) {
        Object count = redisTemplate.opsForValue().get(countKey);
        if (count == null) return maxSends;
        int used = count instanceof Number n ? n.intValue() : parseInt(String.valueOf(count));
        return Math.max(0, maxSends - used);
    }

    /**
     * Sends still available for this mobile, without consuming a send credit.
     *
     * <p>Read-only by design: {@link OtpService#assertDbCooldown} reports remaining
     * sends when the durable backstop rejects a request, and it must not mutate the
     * counter on a path that never delivers an SMS.</p>
     */
    public int peekRemainingSends(String mobile) {
        if (mobile == null || mobile.isBlank()) return maxSends;
        try {
            return remainingSends(COUNT_PREFIX + mobile);
        } catch (Exception e) {
            return maxSends;
        }
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long ttlSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? -1L : ttl;
    }

    /**
     * Returns a send credit taken by {@link #acquireSendSlot} when the gateway then
     * refused to deliver.
     *
     * <p>Used only for the gateway's own resend throttle, where the request was valid and
     * the user did nothing wrong. Without this, a send that never produced an SMS would
     * still count toward the 5-send cap, and a user unlucky enough to hit the small race
     * between our cooldown window and the gateway's could be locked out for 10 minutes
     * without ever receiving 5 messages. Hard gateway failures deliberately do
     * <i>not</i> refund, so a failing gateway still cannot be spammed.</p>
     */
    public void releaseSendSlot(String mobile) {
        if (mobile == null || mobile.isBlank()) return;
        try {
            String countKey = COUNT_PREFIX + mobile;
            Long used = redisTemplate.opsForValue().increment(countKey, -1);
            if (used != null && used <= 0) {
                redisTemplate.delete(countKey);
            }
            redisTemplate.delete(COOLDOWN_PREFIX + mobile);
        } catch (Exception e) {
            log.warn("Could not release OTP send slot for {}: {}", mask(mobile), e.getMessage());
        }
    }

    /** Clears all limit state for a mobile. Called after a successful OTP verification. */
    public void reset(String mobile) {        if (mobile == null || mobile.isBlank()) return;
        try {
            redisTemplate.delete(List.of(
                    COOLDOWN_PREFIX + mobile,
                    COUNT_PREFIX + mobile,
                    LOCK_PREFIX + mobile));
        } catch (Exception e) {
            log.warn("Could not reset OTP rate limit for mobile {}: {}", mask(mobile), e.getMessage());
        }
    }

    private static String formatDuration(int totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + " seconds";
        int minutes = (totalSeconds + 59) / 60;
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    private static String mask(String mobile) {
        if (mobile == null || mobile.length() < 4) return "****";
        return "*******" + mobile.substring(mobile.length() - 3);
    }
}
