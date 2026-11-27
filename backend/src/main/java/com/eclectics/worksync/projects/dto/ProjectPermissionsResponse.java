package com.eclectics.worksync.projects.dto;

import com.eclectics.worksync.projects.entity.ProjectResponsibility;
import com.eclectics.worksync.projects.entity.ProjectCapabilities;
import com.eclectics.worksync.projects.entity.ProjectStatus;
import com.eclectics.worksync.projects.entity.ProjectRole;
import java.util.List;

/** Internal participant projection. Never serialize this shape to a Viewer. */
public record ProjectPermissionsResponse(ProjectRole role, ProjectResponsibility responsibility,
        boolean isOwner, List<String> actions, InitialProjectConfiguration configuration) {
    public static ProjectPermissionsResponse initialOwner() {
        // The creation transaction already established current ACTIVE Owner/MANAGER authority.
        return new ProjectPermissionsResponse(ProjectRole.MANAGER, null, true,
                ProjectCapabilities.availableActions(ProjectRole.MANAGER,true,ProjectStatus.ACTIVE).stream().map(Enum::name).toList(),
                new InitialProjectConfiguration(List.of(), List.of()));
    }

    public static ProjectPermissionsResponse from(ProjectMemberAccess member) {
        if (member.role()==ProjectRole.VIEWER) throw new IllegalArgumentException("Viewer requires safe summary");
        return new ProjectPermissionsResponse(member.role(),member.responsibility(),member.isOwner(),
                ProjectCapabilities.availableActions(member.role(),member.isOwner(),member.projectStatus()).stream().map(Enum::name).toList(),
                new InitialProjectConfiguration(List.of(),List.of()));
    }

    /** Temporary response defaults only: Overview/Planning are mandatory; optional features disabled.
     * Persisted feature configuration and its defaults require a later reviewed implementation.
     */
    public record InitialProjectConfiguration(List<String> enabledViews, List<String> enabledWorkTypes) { }
}
