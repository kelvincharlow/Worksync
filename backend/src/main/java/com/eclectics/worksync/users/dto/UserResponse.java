package com.eclectics.worksync.users.dto;

import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.entity.User;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import com.eclectics.worksync.users.entity.OrganizationPermission;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Safe user data for future service/controller responses; contains no credential fields. */
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        SystemRole role,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        Set<OrganizationPermission> permissions
) {
    public UserResponse {
        permissions = Set.copyOf(permissions);
    }

    /** Source compatibility for existing internal callers/tests constructing accounts with no extra grants. */
    public UserResponse(UUID id, String firstName, String lastName, String email, String phoneNumber,
            SystemRole role, AccountStatus status, Instant createdAt, Instant updatedAt) {
        this(id, firstName, lastName, email, phoneNumber, role, status, createdAt, updatedAt, Set.of());
    }

    /** Stored grants can survive blocking; effective access cannot. Never authorize a write from this snapshot. */
    @JsonProperty("effectivePermissions")
    public Set<OrganizationPermission> effectivePermissions() {
        return status == AccountStatus.ACTIVE ? permissions : Set.of();
    }

    public static UserResponse from(User user) {
        return from(user, Set.of());
    }

    public static UserResponse from(User user, Set<OrganizationPermission> permissions) {
        Objects.requireNonNull(user, "user is required");
        return new UserResponse(
                user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhoneNumber(), user.getRole(),
                user.getStatus(), user.getCreatedAt(), user.getUpdatedAt(), permissions);
    }
}
