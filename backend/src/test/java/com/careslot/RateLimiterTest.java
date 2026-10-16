package com.careslot;

import com.careslot.security.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for the token-bucket rate limiter (no Spring context needed). */
class RateLimiterTest {

    @Test
    void capacityEnforced_thenIndependentPerKey() {
        RateLimiter limiter = new RateLimiter();
        assertThat(limiter.tryConsume("k1", 3, 60)).isTrue();
        assertThat(limiter.tryConsume("k1", 3, 60)).isTrue();
        assertThat(limiter.tryConsume("k1", 3, 60)).isTrue();
        assertThat(limiter.tryConsume("k1", 3, 60)).isFalse();
        // Different key unaffected.
        assertThat(limiter.tryConsume("k2", 3, 60)).isTrue();
    }
}
