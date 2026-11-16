package com.eclectics.worksync.auth.service;

import com.eclectics.worksync.auth.entity.PasswordResetToken;
import com.eclectics.worksync.auth.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetTokenService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);
    private static final int EXPIRATION_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;

    // Field name 'resetStore' and record 'ResetEntry' required for PasswordResetTokenServiceTests reflection
    private final Map<String, ResetEntry> resetStore = new ConcurrentHashMap<>();

    private record ResetEntry(String hashedCode, Instant expiresAt, int attempts) {
        ResetEntry incrementAttempts() {
            return new ResetEntry(this.hashedCode, this.expiresAt, this.attempts + 1);
        }
    }

    private final PasswordResetTokenRepository tokenRepository;
    private final byte[] hmacKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Primary constructor used by Spring (persists tokens to PostgreSQL using HMAC).
     */
    @Autowired
    public PasswordResetTokenService(
            PasswordResetTokenRepository tokenRepository,
            @Value("${app.reset.secret:${app.jwt.secret:default-in-memory-secret-key-32-bytes!}}") String hmacSecret) {
        this.tokenRepository = tokenRepository;
        this.hmacKeyBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * No-argument constructor required by PasswordResetTokenServiceTests.
     */
    public PasswordResetTokenService() {
        this(null, "default-in-memory-secret-key-32-bytes!");
    }

    /**
     * Generates a 6-digit code, stores it in memory and PostgreSQL, and returns the raw code.
     */
    @Transactional
    public String createResetCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        String rawCode = String.valueOf(100000 + secureRandom.nextInt(900000));
        String tokenHash = calculateHmac(rawCode);
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_MINUTES * 60L);

        // 1. Update in-memory store (for unit tests)
        resetStore.put(normalizedEmail, new ResetEntry(tokenHash, expiresAt, 0));

        // 2. Persist to PostgreSQL if database repository is available
        if (tokenRepository != null) {
            tokenRepository.findTopByEmailAndConsumedFalseOrderByCreatedAtDesc(normalizedEmail)
                    .ifPresent(existing -> {
                        existing.markConsumed();
                        tokenRepository.save(existing);
                    });

            PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), normalizedEmail, tokenHash, expiresAt);
            tokenRepository.save(token);
        }

        return rawCode;
    }

    /**
     * Validates the code without deleting it (tested by PasswordResetTokenServiceTests).
     */
    public boolean validateCode(String email, String inputCode) {
        String normalizedEmail = email.trim().toLowerCase();
        ResetEntry entry = resetStore.get(normalizedEmail);

        if (entry == null) {
            return false;
        }

        if (Instant.now().isAfter(entry.expiresAt())) {
            resetStore.remove(normalizedEmail);
            return false;
        }

        if (entry.attempts() >= MAX_ATTEMPTS) {
            resetStore.remove(normalizedEmail);
            return false;
        }

        String inputHmac = calculateHmac(inputCode.trim());
        boolean matches = MessageDigest.isEqual(
                inputHmac.getBytes(StandardCharsets.UTF_8),
                entry.hashedCode().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            resetStore.computeIfPresent(normalizedEmail, (k, old) -> old.incrementAttempts());
            return false;
        }

        return true;
    }

    /**
     * Consumes (removes) the code (tested by PasswordResetTokenServiceTests).
     */
    public void consumeCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        resetStore.remove(normalizedEmail);

        if (tokenRepository != null) {
            tokenRepository.findTopByEmailAndConsumedFalseOrderByCreatedAtDesc(normalizedEmail)
                    .ifPresent(token -> {
                        token.markConsumed();
                        tokenRepository.save(token);
                    });
        }
    }

    /**
     * Atomic validate and consume method used by AuthService during password reset.
     */
    @Transactional
    public boolean validateAndConsume(String email, String inputCode) {
        boolean isValid = validateCode(email, inputCode);
        if (isValid) {
            consumeCode(email);
        }
        return isValid;
    }

    private String calculateHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKeyBytes, "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}