package com.eclectics.worksync.workitems.exception;

import java.util.UUID;

public class WorkItemNotFoundException extends RuntimeException {

    public WorkItemNotFoundException(UUID workItemId) {
        super("Work item not found: " + workItemId);
    }

    public WorkItemNotFoundException(String message) {
        super(message);
    }
}