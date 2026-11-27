package com.eclectics.worksync.users.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "organization_permissions", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission"}))
public class UserPermission {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false) private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 64)
    private OrganizationPermission permission;
    @Column(name = "granted_by_user_id", nullable = false, updatable = false) private UUID grantedByUserId;
    @Column(name = "granted_at", nullable = false, updatable = false) private Instant grantedAt;
    protected UserPermission() { }
    public UserPermission(UUID userId, OrganizationPermission permission, UUID grantedByUserId) {
        this.userId = Objects.requireNonNull(userId);
        this.permission = Objects.requireNonNull(permission);
        this.grantedByUserId = Objects.requireNonNull(grantedByUserId);
    }
    @PrePersist private void timestamp() { grantedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public OrganizationPermission getPermission() { return permission; }
    public UUID getGrantedByUserId() { return grantedByUserId; }
    public Instant getGrantedAt() { return grantedAt; }
}
