package com.eclectics.worksync.users.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Internal Auth-to-Users command, not an HTTP registration request.
 * Auth must supply an already-encoded password hash; Users does not hash it.
 * Never log the hash or validation rejected values for this command.
 */
public record CreateUserCommand(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phoneNumber,
        @JsonIgnore @NotBlank @Size(max = 512) String passwordHash
) {
    @Override
    public String toString() {
        return "CreateUserCommand[redacted]";
    }
}
