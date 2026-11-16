package com.eclectics.worksync.auth.dto;

public record FieldError(
    String field,
    String message
) {}