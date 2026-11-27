package com.eclectics.worksync.workitems.dto;

import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemPriority;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;

import java.time.Instant;
import java.util.UUID;

public record WorkItemResponse(
    UUID id,
    UUID projectId,
    WorkItemType type,
    String title,
    String description,
    WorkItemStatus status,
    WorkItemPriority priority,
    UUID creatorId,
    UUID assigneeId,
    UUID parentId,
    Instant createdAt,
    Instant updatedAt
) {
    public static WorkItemResponse fromEntity(WorkItem item) {
        return new WorkItemResponse(
            item.getId(),
            item.getProjectId(),
            item.getType(),
            item.getTitle(),
            item.getDescription(),
            item.getStatus(),
            item.getPriority(),
            item.getCreatorId(),
            item.getAssigneeId(),
            item.getParentId(),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }
}