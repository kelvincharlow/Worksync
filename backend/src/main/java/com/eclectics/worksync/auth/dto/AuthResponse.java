package com.eclectics.worksync.auth.dto;
import com.eclectics.worksync.users.dto.UserResponse;
public record AuthResponse(
    String accessToken,
    String tokenType,
    int expiresIn,
    UserResponse user //  connect this to UserDto 
) {}