package com.eclectics.worksync.auth.dto;

import com.eclectics.worksync.users.dto.UserResponse;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    int expiresIn,
    UserResponse user
) {
    // Backwards-compatible constructor for existing tests that don't pass a refreshToken
    public AuthResponse(String accessToken, String tokenType, int expiresIn, UserResponse user) {
        this(accessToken, null, tokenType, expiresIn, user);
    }
}