package com.querymind.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.stereotype.Service;

import com.querymind.exception.RateLimitExceededException;

/**
 * In-memory per-IP sliding window rate limiter. 20 requests per 60 seconds.
 * Not distributed — single JVM only. Fine for MVP; swap for Redis/Bucket4j later if needed.
 */
@Service
public class RateLimiterService {

    static final int MAX_REQUESTS_PER_WINDOW = 20;
    static final long WINDOW_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RateLimiterService() {
        this(System::currentTimeMillis);
    }

    RateLimiterService(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Records a request from the given key (typically IP). Throws if the window is exhausted.
     */
    public void check(String key) {
        long now = clock.getAsLong();
        Deque<Long> timestamps = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            // Drop timestamps older than the window
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= now - WINDOW_MILLIS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                throw new RateLimitExceededException(
                        "Too many requests. Please wait a moment.");
            }
            timestamps.addLast(now);
        }
    }
}
