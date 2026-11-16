package com.eclectics.worksync.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private boolean consumed;

    @Column(nullable = false)
    private Instant createdAt;

    public PasswordResetToken() {}

    public PasswordResetToken(UUID id, String email, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.failedAttempts = 0;
        this.consumed = false;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isConsumed() { return consumed; }
    public Instant getCreatedAt() { return createdAt; }

    public void incrementAttempts() {
        this.failedAttempts++;
    }

    public void markConsumed() {
        this.consumed = true;
    }
}