package com.eclectics.worksync.users.entity;

import com.eclectics.worksync.users.exception.InvalidAccountStatusTransitionException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Persistence model only. Future HTTP endpoints must return explicit, safe DTOs. */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 80)
    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @NotBlank
    @Size(max = 80)
    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 254)
    @Column(nullable = false, length = 254)
    private String email;

    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$")
    @Column(name = "phone_number", nullable = false, length = 16)
    private String phoneNumber;

    @NotBlank
    @Size(max = 512)
    @Column(name = "password_hash", nullable = false, length = 512)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SystemRole role = SystemRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status = AccountStatus.PENDING_APPROVAL;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // Required by JPA.
    }

    /** Auth must supply an already-encoded password hash, never a raw password. */
    public User(String firstName, String lastName, String email,
            String phoneNumber, String passwordHash) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = normalizeEmail(email);
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
    }

    private static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email is required").trim().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    private void beforeInsert() {
        email = normalizeEmail(email);
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    // Timestamps change when JPA persists an update, not when a DTO is constructed.
    @PreUpdate
    private void beforeUpdate() {
        email = normalizeEmail(email);
        updatedAt = Instant.now();
    }

    /** Replaces only the stored hash; trusted Auth code must supply an already-encoded value. */
    public void changePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank() || newPasswordHash.length() > 512) {
            throw new IllegalArgumentException("Password hash must be nonblank and at most 512 characters");
        }
        passwordHash = newPasswordHash;
    }

    /** PENDING_APPROVAL -> ACTIVE. Authorization belongs to the calling workflow. */
    public void approve() {
        transitionStatus(AccountStatus.PENDING_APPROVAL, AccountStatus.ACTIVE);
    }

    /** PENDING_APPROVAL -> REJECTED; active accounts must use block() instead. */
    public void reject() {
        transitionStatus(AccountStatus.PENDING_APPROVAL, AccountStatus.REJECTED);
    }

    /** ACTIVE -> BLOCKED. */
    public void block() {
        transitionStatus(AccountStatus.ACTIVE, AccountStatus.BLOCKED);
    }

    /** BLOCKED -> ACTIVE. */
    public void unblock() {
        transitionStatus(AccountStatus.BLOCKED, AccountStatus.ACTIVE);
    }

    // Keep one guard for every operation; never expose an unrestricted status setter.
    private void transitionStatus(AccountStatus requiredStatus, AccountStatus nextStatus) {
        if (status != requiredStatus) {
            throw new InvalidAccountStatusTransitionException();
        }
        status = nextStatus;
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }

    /** Trusted backend access only; never put this value into a response or log. */
    @JsonIgnore
    public String getPasswordHash() { return passwordHash; }

    public SystemRole getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
