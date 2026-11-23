package com.eclectics.worksync.projects.dto;
import com.eclectics.worksync.projects.entity.*;
import java.util.UUID;
/** Internal, current ACTIVE participant snapshot. Not a substitute for per-record work authorization. */
public record ProjectMemberAccess(UUID projectId, UUID userId, ProjectRole role,
        ProjectResponsibility responsibility, boolean isOwner, ProjectStatus projectStatus) { }
