package com.shoplocker.fssai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force guard for public login endpoints.
 * <p>Tracks recent failed attempts per key (manager code and/or client IP)
 * and rejects the key once the failure window is exhausted.
 * State is per-instance; fine for a single-node deployment and an early
 * safety net until a shared store (Redis) is wired in.</p>
 */
@Service
public class LoginAttemptService {

    /** Max failed attempts allowed within {@link #WINDOW_MILLIS}. */
    private static final int MAX_FAILURES = 5;
    /** Sliding window length for the failure count. */
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /**
     * @return {@code true} if the key has hit the failure ceiling within the window.
     */
    public boolean isLocked(String key) {
        return isOverLimit(key, MAX_FAILURES);
    }

    /**
     * Generic windowed ceiling, for callers that count ordinary uses rather than
     * failures (public lookups that must stay throttled but never "fail").
     *
     * @param max how many recorded uses within {@link #WINDOW_MILLIS} before the
     *            key is considered over limit.
     */
    public boolean isOverLimit(String key, int max) {
        if (key == null || key.isBlank()) return false;
        Deque<Long> deque = failures.get(key);
        if (deque == null) return false;
        synchronized (deque) {
            prune(deque);
            return deque.size() >= max;
        }
    }

    /** Records a failed attempt for the given key (best-effort, bounded in size). */
    public void registerFailure(String key) {
        record(key);
    }

    /**
     * Records a successful use of the key against the same sliding window as
     * failures. Used by throttled public endpoints that return a normal response
     * every time and therefore have no "failure" to count.
     */
    public void recordUse(String key) {
        record(key);
    }

    private void record(String key) {
        if (key == null || key.isBlank()) return;
        failures.compute(key, (k, deque) -> {
            Deque<Long> d = deque != null ? deque : new ArrayDeque<>();
            synchronized (d) {
                prune(d);
                d.addLast(System.currentTimeMillis());
            }
            return d;
        });
    }

    /** Clears all tracked failures for the key (called after a successful login). */
    public void reset(String key) {
        if (key == null || key.isBlank()) return;
        failures.remove(key);
    }

    private void prune(Deque<Long> deque) {
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
            deque.pollFirst();
        }
    }
}
