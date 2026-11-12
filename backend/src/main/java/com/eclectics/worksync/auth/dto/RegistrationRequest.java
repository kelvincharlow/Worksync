package com.eclectics.worksync.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 80, message = "First name must be between 1 and 80 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 80, message = "Last name must be between 1 and 80 characters")
    String lastName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    String email,

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Phone number must be in international E.164 format, e.g. +254712345678")
    String phoneNumber,

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
    String password
) {}