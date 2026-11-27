package com.eclectics.worksync.workitems.dto;

import java.time.Instant;

public record WorkItemErrorResponse(
    String code,
    String message,
    Instant timestamp
) {
    public WorkItemErrorResponse(String code, String message) {
        this(code, message, Instant.now());
    }
}