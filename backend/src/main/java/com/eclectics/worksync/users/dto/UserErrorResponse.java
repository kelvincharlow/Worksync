package com.eclectics.worksync.users.dto;

import java.time.Instant;

/** Safe Users MVC error; no rejected values, internal causes or credentials. */
public record UserErrorResponse(String code, String message, Instant timestamp) {
}
