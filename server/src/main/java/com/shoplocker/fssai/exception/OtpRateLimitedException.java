package com.shoplocker.fssai.exception;

import com.shoplocker.fssai.dto.FssaiErrorResponse;

/**
 * Rate-limit rejection carrying the structured retry metadata the client needs to
 * render an accurate countdown.
 *
 * <p>A plain {@link FssaiException} with {@link FailureCode#TOO_MANY_ATTEMPTS} can only
 * express the limit in prose, which forces the client to either parse the message or
 * guess. This subclass carries the retry metadata so {@link GlobalExceptionHandler} can
 * emit it as fields.</p>
 *
 * <p>{@code locked} is carried explicitly rather than left for the client to infer from
 * the duration: a 10-minute lockout and a 2-minute resend cooldown are different states
 * with different copy, and inferring one from the other means the UI silently breaks if
 * the configured durations ever change.</p>
 */
public class OtpRateLimitedException extends FssaiException {

    private final int retryAfterSeconds;
    private final int remainingSends;
    private final boolean locked;

    public OtpRateLimitedException(String message, int retryAfterSeconds,
                                   int remainingSends, boolean locked) {
        super(message, FailureCode.TOO_MANY_ATTEMPTS);
        this.retryAfterSeconds = retryAfterSeconds;
        this.remainingSends = remainingSends;
        this.locked = locked;
    }

    /** Seconds the caller must wait before the next attempt. */
    public int getRetryAfterSeconds() { return retryAfterSeconds; }

    /** Sends still available before the hard lockout; {@code 0} while locked out. */
    public int getRemainingSends() { return remainingSends; }

    /** {@code true} for the hard lockout, {@code false} for the short resend cooldown. */
    public boolean isLocked() { return locked; }

    /** Populates the rate-limit metadata on an error body. */
    public void enrich(FssaiErrorResponse.FssaiErrorResponseBuilder builder) {
        builder.retryAfterSeconds(retryAfterSeconds)
                .remainingSends(remainingSends)
                .otpLocked(locked);
    }
}
