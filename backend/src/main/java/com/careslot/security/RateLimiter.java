package com.careslot.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory token-bucket rate limiter for auth endpoints.
 * Equivalent in purpose to Bucket4j for this demo's needs; kept dependency-free
 * and deterministic for tests.
 */
@Component
public class RateLimiter {

    private record Bucket(double tokens, long lastRefillNanos) {}

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Atomically checks and consumes one token.
     *
     * @param key            bucket key (e.g. client IP + endpoint)
     * @param capacity       max burst tokens
     * @param refillPerMinute sustained rate
     * @return true if the request is allowed
     */
    public boolean tryConsume(String key, int capacity, int refillPerMinute) {
        long now = System.nanoTime();
        double refillPerNanos = (double) refillPerMinute / 60_000_000_000L;
        final boolean[] allowed = {false};
        buckets.compute(key, (k, b) -> {
            double tokens = (b == null) ? capacity : b.tokens();
            long last = (b == null) ? now : b.lastRefillNanos();
            tokens = Math.min(capacity, tokens + (now - last) * refillPerNanos);
            if (tokens >= 1.0) {
                allowed[0] = true;
                return new Bucket(tokens - 1.0, now);
            }
            allowed[0] = false;
            return new Bucket(tokens, now);
        });
        return allowed[0];
    }

    /** Test/reset hook. */
    public void reset(String key) {
        buckets.remove(key);
    }
}
