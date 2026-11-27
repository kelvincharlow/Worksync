package com.eclectics.worksync.projects.dto;

import com.eclectics.worksync.projects.entity.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.util.UUID;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record AddProjectMemberRequest(@NotNull UUID userId, @NotNull @JsonDeserialize(using=RoleDeserializer.class) ProjectRole role,
        @JsonDeserialize(using=ResponsibilityDeserializer.class) ProjectResponsibility responsibility) {
    @AssertTrue(message = "Invalid role/responsibility combination")
    public boolean isRoleResponsibilityValid() {
        return role != null && (role == ProjectRole.MEMBER ? responsibility != null : responsibility == null);
    }
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) { throw new IllegalArgumentException("Unknown member field"); }

    /** Prevent Jackson ordinal coercion: project roles/responsibilities are JSON string enums. */
    public static class RoleDeserializer extends ValueDeserializer<ProjectRole> {
        @Override public ProjectRole deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) return (ProjectRole) context.handleUnexpectedToken(ProjectRole.class,parser);
            try { return ProjectRole.valueOf(parser.getString()); }
            catch (IllegalArgumentException invalid) { return context.reportInputMismatch(ProjectRole.class,"Invalid project role"); }
        }
    }
    public static class ResponsibilityDeserializer extends ValueDeserializer<ProjectResponsibility> {
        @Override public ProjectResponsibility deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) return (ProjectResponsibility) context.handleUnexpectedToken(ProjectResponsibility.class,parser);
            try { return ProjectResponsibility.valueOf(parser.getString()); }
            catch (IllegalArgumentException invalid) { return context.reportInputMismatch(ProjectResponsibility.class,"Invalid project responsibility"); }
        }
    }
}
