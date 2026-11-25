package com.eclectics.worksync.workitems.dto;

import com.eclectics.worksync.workitems.entity.WorkItemPriority;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWorkItemRequest(
    @NotNull(message = "Work item type is required")
    WorkItemType type,

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    String title,

    String description,

    WorkItemPriority priority,

    UUID assigneeId,

    UUID parentId,

    UUID milestoneId
) {}