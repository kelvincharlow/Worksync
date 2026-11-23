package com.eclectics.worksync.projects.dto;
import com.eclectics.worksync.projects.entity.*;
import java.time.Instant;
import java.util.UUID;
/** Foundation membership projection; a future REST mapper must add the authorized displayName. */
public record ProjectMemberResponse(UUID id, UUID projectId, UUID userId, ProjectRole role,
        ProjectResponsibility responsibility, Instant createdAt, Instant updatedAt) {
    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(member.getId(), member.getProjectId(), member.getUserId(),
                member.getRole(), member.getResponsibility(), member.getCreatedAt(), member.getUpdatedAt());
    }
}
