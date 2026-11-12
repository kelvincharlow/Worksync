package com.eclectics.worksync.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetTokenService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);
    private static final int EXPIRATION_MINUTES = 15;

    private final SecureRandom random = new SecureRandom();

    // In-memory store: normalized email -> ResetEntry
    private final Map<String, ResetEntry> resetStore = new ConcurrentHashMap<>();

    private record ResetEntry(String code, Instant expiresAt) {}

    /**
     * Generates a 6-digit code, stores it for 15 minutes, and logs it for testing.
     */
    public String createResetCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        // Generate 6-digit code (100000 - 999999)
        String code = String.valueOf(100000 + random.nextInt(900000));
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_MINUTES * 60L);

        resetStore.put(normalizedEmail, new ResetEntry(code, expiresAt));

        // Log for manual testing//
        log.info("==================================================");
        log.info("PASSWORD RESET CODE FOR {}: {}", normalizedEmail, code);
        log.info("EXPIRES AT: {}", expiresAt);
        log.info("==================================================");

        return code;
    }

    /**
     * Verifies the code. If valid, removes it so it cannot be used twice.
     */
    public boolean verifyAndConsumeCode(String email, String inputCode) {
        String normalizedEmail = email.trim().toLowerCase();
        ResetEntry entry = resetStore.get(normalizedEmail);

        if (entry == null) {
            return false;
        }

        // Check expiration
        if (Instant.now().isAfter(entry.expiresAt())) {
            resetStore.remove(normalizedEmail);
            return false;
        }

        // Check code match
        if (entry.code().equals(inputCode.trim())) {
            // Invalidate single-use code immediately
            resetStore.remove(normalizedEmail);
            return true;
        }

        return false;
    }
}