package com.eclectics.worksync.users.dto;

import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.entity.User;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        Objects.requireNonNull(user, "user is required");
        return new UserResponse(
                user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhoneNumber(), user.getRole(),
                user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
