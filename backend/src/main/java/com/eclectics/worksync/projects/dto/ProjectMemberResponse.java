package com.eclectics.worksync.projects.dto;
import com.eclectics.worksync.projects.entity.*;
import java.time.Instant;
import java.util.UUID;
/** Internal team projection; no contact information, credentials or account controls. */
public record ProjectMemberResponse(UUID userId, String displayName, ProjectRole role,
        ProjectResponsibility responsibility, boolean isOwner, Instant createdAt, Instant updatedAt) {
    public static ProjectMemberResponse from(ProjectMember member, String displayName, UUID ownerId) {
        return new ProjectMemberResponse(member.getUserId(), displayName, member.getRole(), member.getResponsibility(),
                member.getUserId().equals(ownerId), member.getCreatedAt(), member.getUpdatedAt());
    }
}
