package com.eclectics.worksync.auth.service;

import com.eclectics.worksync.auth.entity.PasswordResetToken;
import com.eclectics.worksync.auth.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetTokenService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);
    private static final int EXPIRATION_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;

    private final PasswordResetTokenRepository tokenRepository;
    private final byte[] hmacKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetTokenService(
            PasswordResetTokenRepository tokenRepository,
            @Value("${app.reset.secret:${app.jwt.secret}}") String hmacSecret) {
        this.tokenRepository = tokenRepository;
        this.hmacKeyBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generates a 6-digit code, calculates its keyed HMAC-SHA256, and persists it in PostgreSQL.
     * Invalidates any previously active reset codes for this account.
     */
    @Transactional
    public String createResetCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        // Invalidate any previously issued active codes for this user
        tokenRepository.findTopByEmailAndConsumedFalseOrderByCreatedAtDesc(normalizedEmail)
                .ifPresent(existingToken -> {
                    existingToken.markConsumed();
                    tokenRepository.save(existingToken);
                });

        // Generate 6-digit numeric code
        String rawCode = String.valueOf(100000 + secureRandom.nextInt(900000));
        String tokenHash = calculateHmac(rawCode);

        // Persist to PostgreSQL
        PasswordResetToken token = new PasswordResetToken(
                UUID.randomUUID(),
                normalizedEmail,
                tokenHash,
                Instant.now().plusSeconds(EXPIRATION_MINUTES * 60L)
        );
        tokenRepository.save(token);

        log.info("Password reset token generated and persisted to database.");
        return rawCode;
    }

    /**
     * Atomically validates and consumes the reset code.
     * When executed within a @Transactional method, if any subsequent database operation
     * (like userService.resetPassword) fails, the consumption rolls back automatically.
     */
    @Transactional
    public boolean validateAndConsume(String email, String inputCode) {
        String normalizedEmail = email.trim().toLowerCase();

        Optional<PasswordResetToken> tokenOpt =
                tokenRepository.findTopByEmailAndConsumedFalseOrderByCreatedAtDesc(normalizedEmail);

        if (tokenOpt.isEmpty()) {
            return false;
        }

        PasswordResetToken token = tokenOpt.get();

        // 1. Expiry check
        if (Instant.now().isAfter(token.getExpiresAt())) {
            token.markConsumed();
            tokenRepository.save(token);
            return false;
        }

        // 2. Failed attempts limit check (max 5)
        if (token.getFailedAttempts() >= MAX_ATTEMPTS) {
            token.markConsumed();
            tokenRepository.save(token);
            log.warn("Password reset token for {} invalidated due to too many attempts.", normalizedEmail);
            return false;
        }

        // 3. Constant-time HMAC comparison (mitigates timing attacks)
        String inputHmac = calculateHmac(inputCode.trim());
        boolean matches = MessageDigest.isEqual(
                inputHmac.getBytes(StandardCharsets.UTF_8),
                token.getTokenHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            token.incrementAttempts();
            tokenRepository.save(token);
            return false;
        }

        // 4. Mark consumed atomically
        token.markConsumed();
        tokenRepository.save(token);
        return true;
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