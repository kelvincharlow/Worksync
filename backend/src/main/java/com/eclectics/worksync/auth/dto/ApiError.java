package com.eclectics.worksync.auth.dto;

import java.time.Instant;
import java.util.List;

public record ApiError(
    String code,
    String message,
    Instant timestamp,
    List<FieldError> fieldErrors
) {
    public ApiError(String code, String message) {
        this(code, message, Instant.now(), null);
    }
}