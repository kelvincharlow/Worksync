package com.eclectics.worksync.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RateLimiterService {

    private final int maxRequestsPerIp;
    private final int windowSeconds;

    // Stores timestamps of recent requests per client IP
    private final Map<String, ConcurrentLinkedQueue<Instant>> ipRequestLog = new ConcurrentHashMap<>();

    public record RateLimitResult(boolean isAllowed, long retryAfterSeconds) {}

    public RateLimiterService(
            @Value("${app.rate-limit.ip.max-requests:5}") int maxRequestsPerIp,
            @Value("${app.rate-limit.ip.window-seconds:900}") int windowSeconds) {
        this.maxRequestsPerIp = maxRequestsPerIp;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Checks if the IP is allowed under the sliding-window limit.
     * If blocked, returns the exact seconds the caller must wait (Retry-After).
     */
    public synchronized RateLimitResult checkIpRateLimit(String ipAddress) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);

        ConcurrentLinkedQueue<Instant> timestamps =
                ipRequestLog.computeIfAbsent(ipAddress, k -> new ConcurrentLinkedQueue<>());

        // Evict timestamps older than the sliding window
        while (!timestamps.isEmpty() && timestamps.peek().isBefore(windowStart)) {
            timestamps.poll();
        }

        // If limit exceeded, calculate how many seconds until the oldest request leaves the window
        if (timestamps.size() >= maxRequestsPerIp) {
            Instant oldest = timestamps.peek();
            long retryAfter = oldest != null 
                    ? Math.max(1, Duration.between(now, oldest.plusSeconds(windowSeconds)).getSeconds())
                    : windowSeconds;
            return new RateLimitResult(false, retryAfter);
        }

        // Record this request
        timestamps.add(now);
        return new RateLimitResult(true, 0);
    }
}