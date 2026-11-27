package com.eclectics.worksync.workitems.dto;

import java.util.UUID;

public record ClientWorkProgressSummary(
    UUID projectId,
    long totalDeliverables,
    long completedDeliverables,
    long inProgressDeliverables,
    double progressPercentage
) {}