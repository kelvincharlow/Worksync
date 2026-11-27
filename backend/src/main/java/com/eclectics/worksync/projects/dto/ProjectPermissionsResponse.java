package com.eclectics.worksync.projects.dto;

import com.eclectics.worksync.projects.entity.ProjectResponsibility;
import com.eclectics.worksync.projects.entity.ProjectCapabilities;
import com.eclectics.worksync.projects.entity.ProjectStatus;
import com.eclectics.worksync.projects.entity.ProjectRole;
import java.util.List;

/** Internal participant projection. Never serialize this shape to a Viewer. */
public record ProjectPermissionsResponse(ProjectRole role, ProjectResponsibility responsibility,
        boolean isOwner, List<String> actions, ProjectConfigurationResponse configuration) {
    public static ProjectPermissionsResponse initialOwner(com.eclectics.worksync.projects.entity.Project project) {
        // The creation transaction already established current ACTIVE Owner/MANAGER authority.
        return new ProjectPermissionsResponse(ProjectRole.MANAGER, null, true,
                ProjectCapabilities.availableActions(ProjectRole.MANAGER,true,ProjectStatus.ACTIVE).stream().map(Enum::name).toList(),
                ProjectConfigurationResponse.from(project));
    }

    public static ProjectPermissionsResponse from(ProjectMemberAccess member, ProjectConfigurationResponse configuration) {
        if (member.role()==ProjectRole.VIEWER) throw new IllegalArgumentException("Viewer requires safe summary");
        return new ProjectPermissionsResponse(member.role(),member.responsibility(),member.isOwner(),
                ProjectCapabilities.availableActions(member.role(),member.isOwner(),member.projectStatus()).stream().map(Enum::name).toList(),
                configuration);
    }

}
