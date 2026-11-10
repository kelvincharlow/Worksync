package com.eclectics.worksync.auth;

public record AuthResponse(
    String accessToken,
    String tokenType,
    int expiresIn,
    Object user //  connect this to UserDto 
) {}