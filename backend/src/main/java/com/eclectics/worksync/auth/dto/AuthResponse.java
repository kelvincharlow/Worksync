package com.eclectics.worksync.auth.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    int expiresIn,
    Object user //  connect this to UserDto 
) {}