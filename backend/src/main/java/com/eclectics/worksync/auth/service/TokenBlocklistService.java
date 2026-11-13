package com.eclectics.worksync.auth.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlocklistService {

    // Maps token (or token signature) -> expiration time
    private final Map<String, Instant> blocklist = new ConcurrentHashMap<>();

    /**
     * Blocks an access token until its expiration time.
     */
    public void blockToken(String token, Instant expiresAt) {
        blocklist.put(token, expiresAt);
        cleanExpiredTokens();
    }

    /**
     * Checks if the token has been revoked/logged out.
     */
    public boolean isBlocked(String token) {
        Instant expiry = blocklist.get(token);
        if (expiry == null) {
            return false;
        }
        if (Instant.now().isAfter(expiry)) {
            blocklist.remove(token);
            return false;
        }
        return true;
    }

    private void cleanExpiredTokens() {
        Instant now = Instant.now();
        blocklist.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }
}