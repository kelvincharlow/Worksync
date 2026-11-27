package com.eclectics.worksync.projects.dto;

import com.eclectics.worksync.projects.entity.ProjectResponsibility;
import com.eclectics.worksync.projects.entity.ProjectRole;
import java.util.List;

/** Internal participant projection. Never serialize this shape to a Viewer. */
public record ProjectPermissionsResponse(ProjectRole role, ProjectResponsibility responsibility,
        boolean isOwner, List<String> actions, InitialProjectConfiguration configuration) {
    public static ProjectPermissionsResponse initialOwner() {
        // No Work Item actions are exposed by these Projects slices; team APIs do not enable work features.
        return new ProjectPermissionsResponse(ProjectRole.MANAGER, null, true, List.of(),
                new InitialProjectConfiguration(List.of(), List.of()));
    }

    public static ProjectPermissionsResponse from(ProjectMemberAccess member) {
        if (member.role()==ProjectRole.VIEWER) throw new IllegalArgumentException("Viewer requires safe summary");
        return new ProjectPermissionsResponse(member.role(),member.responsibility(),member.isOwner(),List.of(),
                new InitialProjectConfiguration(List.of(),List.of()));
    }

    /** Temporary response defaults only: Overview/Planning are mandatory; optional features disabled.
     * Persisted feature configuration and its defaults require a later reviewed implementation.
     */
    public record InitialProjectConfiguration(List<String> enabledViews, List<String> enabledWorkTypes) { }
}
