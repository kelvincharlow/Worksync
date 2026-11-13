package com.eclectics.worksync.auth.security;

import java.util.UUID;

public record WorkSyncPrincipal(
    UUID userId,
    String email
) {}