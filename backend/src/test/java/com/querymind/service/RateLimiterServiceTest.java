package com.querymind.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.querymind.exception.RateLimitExceededException;

class RateLimiterServiceTest {

    @Test
    void allowsUpToMaxRequestsWithinWindow() {
        long[] now = {1_000_000L};
        RateLimiterService limiter = new RateLimiterService(() -> now[0]);

        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_WINDOW; i++) {
            limiter.check("1.2.3.4");
        }

        // 21st request should fail
        assertThatThrownBy(() -> limiter.check("1.2.3.4"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Too many requests");
    }

    @Test
    void allowsAgainAfterWindowPasses() {
        long[] now = {1_000_000L};
        RateLimiterService limiter = new RateLimiterService(() -> now[0]);

        // Exhaust the window
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_WINDOW; i++) {
            limiter.check("1.2.3.4");
        }
        assertThatThrownBy(() -> limiter.check("1.2.3.4"))
                .isInstanceOf(RateLimitExceededException.class);

        // Advance past window
        now[0] += RateLimiterService.WINDOW_MILLIS + 1;

        // Should succeed again
        limiter.check("1.2.3.4");
    }

    @Test
    void separateKeysHaveSeparateBuckets() {
        long[] now = {1_000_000L};
        RateLimiterService limiter = new RateLimiterService(() -> now[0]);

        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_WINDOW; i++) {
            limiter.check("1.1.1.1");
        }

        // Different key should not be affected
        limiter.check("2.2.2.2");
        assertThat(true).isTrue();
    }
}
