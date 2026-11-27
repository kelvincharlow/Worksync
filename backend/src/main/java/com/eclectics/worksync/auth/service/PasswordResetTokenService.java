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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetTokenService {

    private static final Logger log = 
            LoggerFactory.getLogger(PasswordResetTokenService.class);
    private static final int EXPIRATION_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;

    // Field name 'resetStore' and record 'ResetEntry' required for tests
    private final Map<String, ResetEntry> resetStore = new ConcurrentHashMap<>();

    private record ResetEntry(String hashedCode, Instant expiresAt, int attempts) {
        ResetEntry incrementAttempts() {
            return new ResetEntry(this.hashedCode, this.expiresAt, this.attempts + 1);
        }
    }

    private final PasswordResetTokenRepository tokenRepository;
    private final byte[] hmacKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public PasswordResetTokenService(
            PasswordResetTokenRepository tokenRepository,
            @Value("${app.reset.secret:${app.jwt.secret:default-secret-key-32-chars-minimum!}}") 
            String hmacSecret) {
        this.tokenRepository = tokenRepository;
        this.hmacKeyBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    // No-arg constructor required by PasswordResetTokenServiceTests
    public PasswordResetTokenService() {
        this(null, "default-secret-key-32-chars-minimum!");
    }

    @Transactional
    public String createResetCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        String rawCode = String.valueOf(100000 + secureRandom.nextInt(900000));
        String tokenHash = calculateHmac(rawCode);
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_MINUTES * 60L);

        resetStore.put(normalizedEmail, new ResetEntry(tokenHash, expiresAt, 0));

        if (tokenRepository != null) {
            tokenRepository.findTopByEmailAndConsumedFalseOrderByCreatedAtDesc(normalizedEmail)
                    .ifPresent(existing -> {
                        existing.markConsumed();
                        tokenRepository.save(existing);
                    });

            PasswordResetToken token = new PasswordResetToken(
                    UUID.randomUUID(), normalizedEmail, tokenHash, expiresAt
            );
            tokenRepository.save(token);
        }

        return rawCode;
    }

    // Method required by PasswordResetTokenServiceTests
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

    // Method required by PasswordResetTokenServiceTests
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