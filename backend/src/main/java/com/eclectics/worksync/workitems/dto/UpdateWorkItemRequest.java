package com.eclectics.worksync.workitems.dto;

import com.eclectics.worksync.workitems.entity.WorkItemPriority;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateWorkItemRequest(
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    String title,

    String description,

    WorkItemStatus status,

    WorkItemPriority priority,

    UUID milestoneId
) {}