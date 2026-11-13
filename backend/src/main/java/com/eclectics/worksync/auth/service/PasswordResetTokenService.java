package com.eclectics.worksync.auth.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetTokenService {

    private static final int EXPIRATION_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();

    // In-memory store: normalized email -> ResetEntry
    private final Map<String, ResetEntry> resetStore = new ConcurrentHashMap<>();

    // Stores SHA-256 hashed code, expiration, and failed attempt count
    private record ResetEntry(String hashedCode, Instant expiresAt, int attempts) {
        ResetEntry incrementAttempts() {
            return new ResetEntry(this.hashedCode, this.expiresAt, this.attempts + 1);
        }
    }

    /**
     * Generates a 6-digit code, stores its SHA-256 hash for 15 minutes,
     * and returns the raw code so it can be sent via email.
     * Never logs sensitive codes to application logs.
     */
    public String createResetCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        String rawCode = String.valueOf(100000 + random.nextInt(900000));
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_MINUTES * 60L);

        // Store SHA-256 hash, not the plaintext code
        resetStore.put(normalizedEmail, new ResetEntry(hash(rawCode), expiresAt, 0));
        return rawCode;
    }

    /**
     * Checks if the code is valid, unexpired, and within attempt limits without consuming it.
     */
    public boolean validateCode(String email, String inputCode) {
        String normalizedEmail = email.trim().toLowerCase();
        ResetEntry entry = resetStore.get(normalizedEmail);

        if (entry == null) {
            return false;
        }

        // Expired check
        if (Instant.now().isAfter(entry.expiresAt())) {
            resetStore.remove(normalizedEmail);
            return false;
        }

        // Rate limiting check: invalidate if exceeded max attempts
        if (entry.attempts() >= MAX_ATTEMPTS) {
            resetStore.remove(normalizedEmail);
            return false;
        }

        // Constant-time comparison between hashed input and stored hash
        boolean matches = MessageDigest.isEqual(
                hash(inputCode.trim()).getBytes(StandardCharsets.UTF_8),
                entry.hashedCode().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            // Increment attempt count on wrong guess
            resetStore.computeIfPresent(normalizedEmail, (k, old) -> old.incrementAttempts());
            return false;
        }

        return true;
    }

    /**
     * Consumes (deletes) the code only AFTER the database password update has succeeded.
     */
    public void consumeCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        resetStore.remove(normalizedEmail);
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}