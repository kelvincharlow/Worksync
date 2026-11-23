package com.eclectics.worksync.projects.dto;
import com.eclectics.worksync.projects.entity.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
/** Internal foundation projection, not the complete REST contract (permissions/configuration come later).
 * Never expose this internal brief to VIEWER; future controllers must compose role-specific responses. */
public record ProjectResponse(UUID id, UUID creatorUserId, UUID ownerUserId, String name,
        String description, String requirements, String scope, String deliverables, String successCriteria,
        LocalDate startDate, LocalDate dueDate, ProjectStatus status, Instant createdAt, Instant updatedAt) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getCreatorUserId(), project.getOwnerUserId(),
                project.getName(), project.getDescription(), project.getRequirements(), project.getScope(),
                project.getDeliverables(), project.getSuccessCriteria(), project.getStartDate(), project.getDueDate(),
                project.getStatus(), project.getCreatedAt(), project.getUpdatedAt());
    }
}
