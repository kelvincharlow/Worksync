package com.eclectics.worksync.workitems.entity;

public enum WorkItemStatus {
    // Generic lifecycle (EPIC, STORY, TASK)
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,

    // Bug lifecycle (BUG)
    REPORTED,
    TRIAGE,
    ASSIGNED,
    READY_FOR_VERIFICATION,
    CLOSED,
    REOPENED,
    INVALID,
    DUPLICATE;

    /**
     * Checks if a status is allowed for a given work item type.
     */
    public boolean isValidFor(WorkItemType type) {
        if (type == WorkItemType.BUG) {
            return this == REPORTED || this == TRIAGE || this == ASSIGNED
                    || this == IN_PROGRESS || this == READY_FOR_VERIFICATION
                    || this == CLOSED || this == REOPENED
                    || this == INVALID || this == DUPLICATE;
        }
        return this == TODO || this == IN_PROGRESS || this == IN_REVIEW || this == DONE;
    }
}