package com.eclectics.worksync.projects.dto;
import com.eclectics.worksync.projects.entity.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
/** Internal Manager/Member projection; never expose this private brief to a Viewer. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectResponse(UUID id, UUID creatorUserId, UUID ownerUserId, String name,
        String description, String requirements, String scope, String deliverables, String successCriteria,
        LocalDate startDate, LocalDate dueDate, ProjectStatus status, Instant createdAt, Instant updatedAt, ProjectPermissionsResponse permissions) {
    public static ProjectResponse from(Project project, ProjectPermissionsResponse permissions) {
        return new ProjectResponse(project.getId(), project.getCreatorUserId(), project.getOwnerUserId(),
                project.getName(), project.getDescription(), project.getRequirements(), project.getScope(),
                project.getDeliverables(), project.getSuccessCriteria(), project.getStartDate(), project.getDueDate(),
                project.getStatus(), project.getCreatedAt(), project.getUpdatedAt(), permissions);
    }
}
