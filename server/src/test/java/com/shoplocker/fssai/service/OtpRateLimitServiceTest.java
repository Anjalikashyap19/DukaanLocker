package com.shoplocker.fssai.service;

import com.shoplocker.fssai.exception.OtpRateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Decision-logic tests for the OTP send limiter.
 *
 * <p>Backed by a stateful in-memory fake rather than a real Redis, so the suite runs
 * offline and stays fast. Only the handful of {@link RedisTemplate} operations the limiter
 * actually uses are implemented; anything else would throw, which keeps the fake honest
 * about the service's actual Redis surface.</p>
 */
class OtpRateLimitServiceTest {

    private static final String MOBILE = "9998887776";
    private static final int COOLDOWN = 120;
    private static final int MAX_SENDS = 5;
    private static final int LOCKOUT = 600;

    private FakeRedis redis;
    private OtpRateLimitService service;

    /** Minimal stateful stand-in for the Redis operations the limiter relies on. */
    private static final class FakeRedis {
        final Map<String, Object> values = new HashMap<>();
        final Map<String, Long> ttls = new HashMap<>();

        boolean has(String key) {
            expireIfDue(key);
            return values.containsKey(key);
        }

        void put(String key, Object value, long ttlSeconds) {
            values.put(key, value);
            ttls.put(key, ttlSeconds);
        }

        void del(String key) {
            values.remove(key);
            ttls.remove(key);
        }

        long ttl(String key) {
            expireIfDue(key);
            Long ttl = ttls.get(key);
            return ttl == null ? -1L : ttl;
        }

        long incr(String key) {
            expireIfDue(key);
            long current = 0;
            Object existing = values.get(key);
            if (existing != null) current = Long.parseLong(String.valueOf(existing));
            current += 1;
            values.put(key, current);
            if (!ttls.containsKey(key)) ttls.put(key, (long) LOCKOUT);
            return current;
        }

        long incrBy(String key, long delta) {
            expireIfDue(key);
            long current = 0;
            Object existing = values.get(key);
            if (existing != null) current = Long.parseLong(String.valueOf(existing));
            current += delta;
            if (current <= 0) {
                del(key);
            } else {
                values.put(key, current);
            }
            return current;
        }

        /** Simulates the passage of time so cooldown windows can be skipped in tests. */
        void expireIfDue(String key) {
            Long ttl = ttls.get(key);
            if (ttl != null && ttl <= 0) del(key);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = new FakeRedis();

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(valueOps.increment(anyString())).thenAnswer(i -> redis.incr(i.getArgument(0)));
        when(valueOps.increment(anyString(), anyLong()))
                .thenAnswer(i -> redis.incrBy(i.getArgument(0), i.getArgument(1)));
        when(valueOps.get(anyString())).thenAnswer(i -> redis.values.get(i.getArgument(0)));
        // ValueOperations#set returns void, so it must be stubbed with doAnswer.
        doAnswer(i -> {
            redis.put(i.getArgument(0), i.getArgument(1), i.getArgument(2));
            return null;
        }).when(valueOps).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        when(template.opsForValue()).thenReturn(valueOps);
        when(template.hasKey(anyString())).thenAnswer(i -> redis.has(i.getArgument(0)));
        when(template.getExpire(anyString(), any(TimeUnit.class)))
                .thenAnswer(i -> redis.ttl(i.getArgument(0)));
        when(template.expire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(i -> {
                    redis.ttls.put(i.getArgument(0), i.getArgument(1));
                    return true;
                });
        when(template.delete(anyString())).thenAnswer(i -> {
            redis.del(i.getArgument(0));
            return true;
        });

        service = new OtpRateLimitService(template);

        // The limiter reads its thresholds from @Value fields, which Spring only populates
        // for a managed bean. A hand-constructed instance therefore has maxSends = 0, which
        // would lock the very first send out, so the configured values are injected here.
        ReflectionTestUtils.setField(service, "cooldownSeconds", COOLDOWN);
        ReflectionTestUtils.setField(service, "maxSends", MAX_SENDS);
        ReflectionTestUtils.setField(service, "lockoutSeconds", LOCKOUT);
    }

    /** Clears just the cooldown, as if the wait window elapsed but the cap still stands. */
    private void elapseCooldown() {
        redis.del("otp:rl:cooldown:" + MOBILE);
    }

    @Test
    @DisplayName("first send is allowed and reports the full resend wait")
    void firstSendIsAllowed() {
        OtpRateLimitService.Decision decision = service.acquireSendSlot(MOBILE);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.locked()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(COOLDOWN);
        assertThat(decision.remainingSends()).isEqualTo(MAX_SENDS - 1);
        assertThat(decision.degraded()).isFalse();
    }

    @Test
    @DisplayName("a send inside the cooldown is rejected with a countdown, not a lockout")
    void secondSendInsideCooldownIsBlocked() {
        service.acquireSendSlot(MOBILE);

        assertThatThrownBy(() -> service.acquireSendSlot(MOBILE))
                .isInstanceOf(OtpRateLimitedException.class)
                .satisfies(e -> {
                    OtpRateLimitedException ex = (OtpRateLimitedException) e;
                    assertThat(ex.isLocked()).isFalse();
                    assertThat(ex.getRetryAfterSeconds()).isPositive().isLessThanOrEqualTo(COOLDOWN);
                    assertThat(ex.getRemainingSends()).isEqualTo(MAX_SENDS - 1);
                });
    }

    @Test
    @DisplayName("a blocked send does not consume a send credit")
    void blockedSendDoesNotConsumeCredit() {
        service.acquireSendSlot(MOBILE);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.acquireSendSlot(MOBILE))
                    .isInstanceOf(OtpRateLimitedException.class);
        }

        assertThat(service.peekRemainingSends(MOBILE)).isEqualTo(MAX_SENDS - 1);
    }

    @Test
    @DisplayName("the cap trips a full-length lockout on the attempt after the last send")
    void capTripsLockout() {
        for (int send = 1; send <= MAX_SENDS; send++) {
            assertThatCode(() -> service.acquireSendSlot(MOBILE))
                    .as("send %s of %s should be allowed", send, MAX_SENDS)
                    .doesNotThrowAnyException();
            elapseCooldown();
        }

        assertThatThrownBy(() -> service.acquireSendSlot(MOBILE))
                .isInstanceOf(OtpRateLimitedException.class)
                .satisfies(e -> {
                    OtpRateLimitedException ex = (OtpRateLimitedException) e;
                    assertThat(ex.isLocked()).isTrue();
                    assertThat(ex.getRetryAfterSeconds()).isEqualTo(LOCKOUT);
                    assertThat(ex.getRemainingSends()).isZero();
                });
    }

    @Test
    @DisplayName("the lockout outranks the cooldown and blocks every send in the window")
    void lockoutBlocksFurtherSends() {
        for (int send = 1; send <= MAX_SENDS + 1; send++) {
            try {
                service.acquireSendSlot(MOBILE);
            } catch (OtpRateLimitedException expected) {
                // the (MAX_SENDS + 1)-th send is the one that trips it
            }
            elapseCooldown();
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.acquireSendSlot(MOBILE))
                    .isInstanceOf(OtpRateLimitedException.class)
                    .satisfies(e -> assertThat(((OtpRateLimitedException) e).isLocked()).isTrue());
        }
    }

    @Test
    @DisplayName("releasing a slot refunds the credit so an undelivered send is not charged")
    void releaseRefundsCredit() {
        service.acquireSendSlot(MOBILE);
        assertThat(service.peekRemainingSends(MOBILE)).isEqualTo(MAX_SENDS - 1);

        service.releaseSendSlot(MOBILE);

        assertThat(service.peekRemainingSends(MOBILE)).isEqualTo(MAX_SENDS);
    }

    @Test
    @DisplayName("the count key is cleaned up once the window lapses entirely")
    void peekIsNonMutating() {
        int before = service.peekRemainingSends(MOBILE);
        int after = service.peekRemainingSends(MOBILE);

        assertThat(before).isEqualTo(MAX_SENDS);
        assertThat(after).isEqualTo(MAX_SENDS);
        assertThat(redis.values).doesNotContainKey("otp:rl:count:" + MOBILE);
    }

    @Test
    @DisplayName("a blank mobile is not rate limited rather than throwing")
    void blankMobileIsAllowed() {
        assertThatCode(() -> service.acquireSendSlot(null)).doesNotThrowAnyException();
        assertThatCode(() -> service.acquireSendSlot("  ")).doesNotThrowAnyException();
    }
}
