package com.eclectics.worksync.projects.dto;

import java.time.Instant;

/** Safe API error; excludes rejected values, SQL and exception internals. */
public record ProjectErrorResponse(String code, String message, Instant timestamp) { }
