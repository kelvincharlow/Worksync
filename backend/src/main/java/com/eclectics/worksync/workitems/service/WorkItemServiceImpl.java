package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.workitems.dto.CreateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.UpdateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.WorkItemResponse;
import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.exception.WorkItemNotFoundException;
import com.eclectics.worksync.workitems.repository.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WorkItemServiceImpl implements WorkItemService {

    private final WorkItemRepository workItemRepository;

    public WorkItemServiceImpl(WorkItemRepository workItemRepository) {
        this.workItemRepository = workItemRepository;
    }

    @Override
    public WorkItemResponse createWorkItem(
            UUID projectId, 
            UUID actorId, 
            CreateWorkItemRequest request) {

        // Validate same-project parent relationship if parentId is provided
        if (request.parentId() != null) {
            workItemRepository.findByIdAndProjectId(request.parentId(), projectId)
                    .orElseThrow(() -> new WorkItemNotFoundException(
                            "Parent work item not found in this project: " + request.parentId()));
        }

        WorkItem workItem = new WorkItem(
                UUID.randomUUID(),
                projectId,
                request.type(),
                request.title(),
                request.description(),
                request.priority(),
                actorId,
                request.assigneeId(),
                request.parentId(),
                request.milestoneId()
        );

        WorkItem saved = workItemRepository.save(workItem);
        return WorkItemResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkItemResponse getWorkItem(UUID projectId, UUID workItemId) {
        WorkItem item = workItemRepository.findByIdAndProjectId(workItemId, projectId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));
        return WorkItemResponse.fromEntity(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkItemResponse> getWorkItems(
            UUID projectId, 
            WorkItemStatus status, 
            WorkItemType type) {

        List<WorkItem> items = workItemRepository.findAllByProjectId(projectId);

        // Apply optional status and type filters
        return items.stream()
                .filter(item -> status == null || item.getStatus() == status)
                .filter(item -> type == null || item.getType() == type)
                .map(WorkItemResponse::fromEntity)
                .toList();
    }

    @Override
    public WorkItemResponse updateWorkItem(
            UUID projectId, 
            UUID workItemId, 
            UUID actorId, 
            UpdateWorkItemRequest request) {

        WorkItem item = workItemRepository.findByIdAndProjectId(workItemId, projectId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));

        if (request.title() != null || request.description() != null || request.priority() != null) {
            item.updateDetails(request.title(), request.description(), request.priority());
        }

        if (request.status() != null) {
            item.changeStatus(request.status());
        }

        if (request.milestoneId() != null) {
            item.setMilestone(request.milestoneId());
        }

        WorkItem updated = workItemRepository.save(item);
        return WorkItemResponse.fromEntity(updated);
    }
}