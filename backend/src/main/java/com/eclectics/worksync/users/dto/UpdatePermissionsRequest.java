package com.eclectics.worksync.users.dto;

import com.eclectics.worksync.users.entity.OrganizationPermission;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete replacement. A List intentionally preserves duplicates so Users can reject them with 400. */
public record UpdatePermissionsRequest(@NotNull List<@NotNull OrganizationPermission> permissions) {
    public UpdatePermissionsRequest {
        if (permissions != null) permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
    }
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported permission request field");
    }
}
