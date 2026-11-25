package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.workitems.dto.CreateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.UpdateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.WorkItemResponse;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;

import java.util.List;
import java.util.UUID;

public interface WorkItemService {

    /**
     * Creates a new EPIC, STORY, or TASK within a project.
     */
    WorkItemResponse createWorkItem(
            UUID projectId, 
            UUID actorId, 
            CreateWorkItemRequest request
    );

    /**
     * Retrieves an authorized work item by its ID and project ID.
     */
    WorkItemResponse getWorkItem(UUID projectId, UUID workItemId);

    /**
     * Lists all work items in a project with optional status and type filters.
     */
    List<WorkItemResponse> getWorkItems(
            UUID projectId, 
            WorkItemStatus status, 
            WorkItemType type
    );

    /**
     * Updates permitted fields (title, description, status, priority, milestone).
     */
    WorkItemResponse updateWorkItem(
            UUID projectId, 
            UUID workItemId, 
            UUID actorId, 
            UpdateWorkItemRequest request
    );
}