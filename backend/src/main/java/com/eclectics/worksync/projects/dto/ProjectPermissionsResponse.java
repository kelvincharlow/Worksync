package com.eclectics.worksync.projects.dto;

import com.eclectics.worksync.projects.entity.ProjectResponsibility;
import com.eclectics.worksync.projects.entity.ProjectRole;
import java.util.List;

/** Creation-only Owner projection, not a general project authorization evaluator. */
public record ProjectPermissionsResponse(ProjectRole role, ProjectResponsibility responsibility,
        boolean isOwner, List<String> actions, InitialProjectConfiguration configuration) {
    public static ProjectPermissionsResponse initialOwner() {
        // No Work Item actions are exposed by these Projects slices; team APIs do not enable work features.
        return new ProjectPermissionsResponse(ProjectRole.MANAGER, null, true, List.of(),
                new InitialProjectConfiguration(List.of(), List.of()));
    }

    /** Temporary response defaults only: Overview/Planning are mandatory; optional features disabled.
     * Persisted feature configuration and its defaults require a later reviewed implementation.
     */
    public record InitialProjectConfiguration(List<String> enabledViews, List<String> enabledWorkTypes) { }
}
