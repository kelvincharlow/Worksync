package com.eclectics.worksync.users.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Administrator request only. Optional reasons are validated but not persisted or delivered. */
public record ChangeAccountStatusRequest(
        @NotNull @Pattern(regexp = "ACTIVE|BLOCKED") String status,
        @Size(max = 500) String reason
) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported administrator request field");
    }
}
