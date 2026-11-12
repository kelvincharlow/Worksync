package com.eclectics.worksync.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Service
public class JwtService {

    private final SecretKey key;
    private final int expirationSeconds;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds:900}") int expirationSeconds,
            @Value("${app.jwt.issuer:worksync-api}") String issuer) {

        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be configured and at least 256 bits (32 bytes) long.");
        }

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
    }
    /**
     * Generates a signed JWT access token with user details as claims.
     */
    public String generateToken(String userId, String email, String role, String status, Set<String> permissions) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(userId)
                .issuer(issuer)
                .claim("email", email)
                .claim("role", role)
                .claim("status", status)
                .claim("permissions", permissions != null ? permissions : Set.of())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies if the token signature is valid and not expired.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    /**
     * Extracts the user ID (subject) from the token.
     */
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public int getExpirationSeconds() {
        return expirationSeconds;
    }
}