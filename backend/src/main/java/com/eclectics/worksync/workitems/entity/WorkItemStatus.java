package com.eclectics.worksync.workitems.entity;

public enum WorkItemStatus {
    // Generic workflow (EPIC, STORY, TASK)
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,

    // Bug lifecycle (BUG)
    REPORTED,
    TRIAGE,
    ASSIGNED,
    READY_FOR_VERIFICATION,
    REOPENED,
    CLOSED;

    /**
     * Checks if this status is allowed for a given work item type.
     */
    public boolean isValidFor(WorkItemType type) {
        if (type == WorkItemType.BUG) {
            return this == REPORTED || this == TRIAGE || this == ASSIGNED
                    || this == IN_PROGRESS || this == READY_FOR_VERIFICATION
                    || this == REOPENED || this == CLOSED;
        }
        return this == TODO || this == IN_PROGRESS 
                || this == IN_REVIEW || this == DONE;
    }
}