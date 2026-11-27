package com.eclectics.worksync.projects.dto;

import com.eclectics.worksync.projects.entity.*;
import java.util.UUID;

/** Safe selector/Viewer projection: no private brief or other participant identifiers. */
public record ProjectSummaryResponse(UUID id, String name, ProjectStatus status, ProjectRole role,
        ProjectResponsibility responsibility, boolean isOwner) {
    public static ProjectSummaryResponse from(Project project, ProjectMemberAccess member) {
        return new ProjectSummaryResponse(project.getId(),project.getName(),project.getStatus(),member.role(),
                member.responsibility(),member.isOwner());
    }
}
