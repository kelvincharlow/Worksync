package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.projects.dto.ProjectMemberAccess;
import com.eclectics.worksync.projects.entity.ProjectAction;
import com.eclectics.worksync.projects.entity.ProjectResponsibility;
import com.eclectics.worksync.projects.exception.ProjectAccessDeniedException;
import com.eclectics.worksync.projects.service.ProjectAccessService;
import com.eclectics.worksync.workitems.dto.CreateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.UpdateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.WorkItemResponse;
import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemPriority;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.exception.InvalidAssigneeException;
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
    private final WorkItemRelationshipService relationshipService;
    private final ProjectAccessService projectAccessService;

    public WorkItemServiceImpl(
            WorkItemRepository workItemRepository,
            WorkItemRelationshipService relationshipService,
            ProjectAccessService projectAccessService) {
        this.workItemRepository = workItemRepository;
        this.relationshipService = relationshipService;
        this.projectAccessService = projectAccessService;
    }

    @Override
    public WorkItemResponse createWorkItem(
            UUID projectId, 
            UUID actorId, 
            CreateWorkItemRequest request) {

        // 1. Guard: Reject writes if project is archived
        projectAccessService.requireWritable(projectId);

        // 2. Authorize action by type
        authorizeCreationAction(projectId, actorId, request.type());

        // 3. Enforce developer self-assignment and validate assignees
        UUID effectiveAssigneeId = resolveAndValidateAssignee(projectId, actorId, request);

        // 4. Validate hierarchy, depth limits, and cycle prevention
        if (request.parentId() != null) {
            relationshipService.validateParentRelationship(
                    projectId, null, request.type(), request.parentId());
        }

        // 5. Persist with 9-parameter constructor
        WorkItem workItem = new WorkItem(
                UUID.randomUUID(),
                projectId,
                request.type(),
                request.title(),
                request.description(),
                request.priority(),
                actorId,
                effectiveAssigneeId,
                request.parentId()
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

        // 1. Guard: Reject writes if project is archived
        projectAccessService.requireWritable(projectId);

        WorkItem item = workItemRepository.findByIdAndProjectId(workItemId, projectId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));

        // 2. Update details
        if (request.title() != null || request.description() != null 
                || request.priority() != null) {
            item.updateDetails(request.title(), request.description(), request.priority());
        }

        // 3. Update status
        if (request.status() != null) {
            item.changeStatus(request.status());
        }

        WorkItem updated = workItemRepository.save(item);
        return WorkItemResponse.fromEntity(updated);
    }

    private void authorizeCreationAction(UUID projectId, UUID actorId, WorkItemType type) {
        switch (type) {
            case EPIC -> projectAccessService.requireAction(
                    projectId, actorId, ProjectAction.CREATE_EPIC);
            case STORY -> projectAccessService.requireAction(
                    projectId, actorId, ProjectAction.CREATE_STORY);
            case TASK -> projectAccessService.requireAction(
                    projectId, actorId, ProjectAction.CREATE_TASK);
            case BUG -> projectAccessService.requireAction(
                    projectId, actorId, ProjectAction.REPORT_BUG);
        }
    }

    private UUID resolveAndValidateAssignee(
            UUID projectId, UUID actorId, CreateWorkItemRequest request) {

        ProjectMemberAccess access = projectAccessService.getMemberAccess(projectId, actorId);

        // Developers can only create self-assigned Tasks
        if (access.responsibility() == ProjectResponsibility.DEVELOPER) {
           if (request.type() != WorkItemType.TASK) {
                throw new ProjectAccessDeniedException();
            }
            if (request.assigneeId() != null && !request.assigneeId().equals(actorId)) {
                throw new InvalidAssigneeException(
                        "Developers can only create tasks assigned to themselves");
            }
            return actorId;
        }

        // For Managers and Tech Leads, validate assignee if provided
        if (request.assigneeId() != null) {
            projectAccessService.requireAssignableMember(projectId, request.assigneeId());
        }

        return request.assigneeId();
    }
}