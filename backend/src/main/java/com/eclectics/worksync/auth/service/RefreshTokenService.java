package com.eclectics.worksync.auth.service;

import com.eclectics.worksync.auth.entity.RefreshToken;
import com.eclectics.worksync.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final int refreshExpirationDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public record RotatedToken(UUID userId, String newRawToken) {}

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-expiration-days:7}") int refreshExpirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    /**
     * Creates a new cryptographically secure refresh token, stores its SHA-256 hash in PostgreSQL,
     * and returns the unhashed raw token to the client.
     */
    @Transactional
    public String createRefreshToken(UUID userId) {
        // 1. Generate 64 secure random bytes and encode as Base64 URL-safe string
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // 2. Hash before storing in the database
        String tokenHash = hash(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(refreshExpirationDays * 86400L);

        RefreshToken token = new RefreshToken(UUID.randomUUID(), userId, tokenHash, expiresAt);
        refreshTokenRepository.save(token);

        return rawToken;
    }

    /**
     * Validates the presented token, immediately revokes it (Rotation),
     * and issues a new replacement refresh token.
     */
    @Transactional
    public RotatedToken rotateRefreshToken(String rawToken) {
        String tokenHash = hash(rawToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token."));

        // If the token was already revoked, someone might be attempting a replay attack!
        if (token.isRevoked()) {
            log.warn("Attempted reuse of revoked refresh token for user {}. Revoking all sessions.", token.getUserId());
            refreshTokenRepository.revokeAllByUserId(token.getUserId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Revoked refresh token. Please log in again.");
        }

        // Check if expired
        if (token.isExpired()) {
            token.revoke();
            refreshTokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired. Please log in again.");
        }

        // 1. Invalidate current token (single-use rotation)
        token.revoke();
        refreshTokenRepository.save(token);

        // 2. Issue a brand new refresh token
        String newRawToken = createRefreshToken(token.getUserId());

        return new RotatedToken(token.getUserId(), newRawToken);
    }

    /**
     * Revokes all active refresh tokens for a user (used during logout or password changes).
     */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}