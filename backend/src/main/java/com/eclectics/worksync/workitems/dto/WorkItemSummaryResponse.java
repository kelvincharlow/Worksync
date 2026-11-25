package com.eclectics.worksync.workitems.dto;

import java.util.UUID;

public record WorkItemSummaryResponse(
    UUID projectId,
    long totalItems,
    long todoCount,
    long inProgressCount,
    long inReviewCount,
    long doneCount,
    long epicCount,
    long storyCount,
    long taskCount,
    long bugCount
) {}