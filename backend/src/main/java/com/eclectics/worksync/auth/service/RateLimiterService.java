package com.eclectics.worksync.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    public RateLimiterService(
            @Value("${app.rate-limit.ip.max-requests:5}") int maxRequestsPerIp,
            @Value("${app.rate-limit.ip.window-seconds:900}") int windowSeconds) {
        this.maxRequestsPerIp = maxRequestsPerIp;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Checks if the given IP address has exceeded the allowed request limit.
     * Sliding-window algorithm.
     */
    public boolean isIpAllowed(String ipAddress) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);

        ConcurrentLinkedQueue<Instant> timestamps =
                ipRequestLog.computeIfAbsent(ipAddress, k -> new ConcurrentLinkedQueue<>());

        // Evict timestamps older than the sliding window
        while (!timestamps.isEmpty() && timestamps.peek().isBefore(windowStart)) {
            timestamps.poll();
        }

        // Reject if exceeded limit
        if (timestamps.size() >= maxRequestsPerIp) {
            return false;
        }

        timestamps.add(now);
        return true;
    }
}