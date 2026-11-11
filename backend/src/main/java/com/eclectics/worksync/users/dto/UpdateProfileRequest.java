package com.eclectics.worksync.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Full basic-profile update (PUT): all three fields are required. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phoneNumber
) {
}
