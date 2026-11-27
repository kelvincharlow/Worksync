package com.eclectics.worksync.projects.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

public record TransferOwnershipRequest(@NotNull @JsonDeserialize(using=StrictProjectUuidDeserializer.class) UUID newOwnerUserId) {
    @JsonAnySetter
    public void rejectUnknownField(String name,Object value) { throw new IllegalArgumentException("Unsupported transfer field"); }
}
