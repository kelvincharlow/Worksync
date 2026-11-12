package com.eclectics.worksync.users.dto;

import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

/**
 * Credential-bearing data for trusted internal Java calls from Auth only.
 * Never return this record from a REST controller or include it in a public response.
 * Auth owns password verification, account-status decisions and token issuance.
 */
public record AuthUserDetails(
        UUID id,
        String email,
        @JsonIgnore String passwordHash,
        SystemRole role,
        AccountStatus status
) {
    @Override
    public String toString() {
        return "AuthUserDetails[redacted]";
    }
}
