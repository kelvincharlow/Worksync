package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemLinkType;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.exception.InvalidWorkItemRelationshipException;
import com.eclectics.worksync.workitems.exception.WorkItemNotFoundException;
import com.eclectics.worksync.workitems.repository.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkItemRelationshipService {

    private final WorkItemRepository workItemRepository;

    public WorkItemRelationshipService(WorkItemRepository workItemRepository) {
        this.workItemRepository = workItemRepository;
    }

    /**
     * Validates parent-child compatibility, nesting depth, subtree reparenting, and cycles.
     */
    public void validateParentRelationship(
            UUID projectId, 
            UUID childId, 
            WorkItemType childType, 
            UUID parentId) {

        if (parentId == null) {
            return; // Standalone items are allowed
        }

        if (childType == WorkItemType.EPIC) {
            throw new InvalidWorkItemRelationshipException(
                    "An EPIC cannot have a parent work item");
        }

        // Direct self-parent check
        if (childId != null && childId.equals(parentId)) {
            throw new InvalidWorkItemRelationshipException(
                    "Work item cannot reference itself as parent");
        }

        // Fetch parent strictly within the same project
        WorkItem parent = workItemRepository.findByIdAndProjectId(parentId, projectId)
                .orElseThrow(() -> new WorkItemNotFoundException(
                        "Parent work item not found in project: " + parentId));

        // Validate type hierarchy (EPIC -> STORY -> TASK)
        validateTypeHierarchy(childType, parent.getType());

        // Enforce maximum 1 subtask level (TASK -> TASK)
        if (childType == WorkItemType.TASK && parent.getType() == WorkItemType.TASK) {
            // Rule A: The parent task cannot already be a subtask
            validateParentNotAlreadySubtask(projectId, parent);

            // Rule B: The child cannot already have subtasks of its own!
            validateChildDoesNotHaveExistingSubtasks(projectId, childId);
        }

        // Prevent indirect circular ancestor loops (A -> B -> A)
        if (childId != null) {
            checkForCycles(projectId, childId, parentId);
        }
    }

    /**
     * Validates that both items in a link belong to the same project and do not self-link.
     */
    public void validateLinkRelationship(
            UUID projectId, 
            UUID sourceId, 
            UUID targetId, 
            WorkItemLinkType linkType) {

        if (sourceId.equals(targetId)) {
            throw new InvalidWorkItemRelationshipException(
                    "Work item cannot link to itself");
        }

        if (!workItemRepository.existsByIdAndProjectId(sourceId, projectId)) {
            throw new WorkItemNotFoundException(
                    "Source work item not found in project: " + sourceId);
        }

        if (!workItemRepository.existsByIdAndProjectId(targetId, projectId)) {
            throw new WorkItemNotFoundException(
                    "Target work item not found in project: " + targetId);
        }
    }

    private void validateTypeHierarchy(WorkItemType childType, WorkItemType parentType) {
        switch (childType) {
            case STORY -> {
                if (parentType != WorkItemType.EPIC) {
                    throw new InvalidWorkItemRelationshipException(
                            "A STORY can only belong under an EPIC, but found: " + parentType);
                }
            }
            case TASK -> {
                if (parentType != WorkItemType.STORY && parentType != WorkItemType.TASK) {
                    throw new InvalidWorkItemRelationshipException(
                            "A TASK can only belong under a STORY or another TASK, but found: " 
                            + parentType);
                }
            }
            case BUG -> {
                // Bugs can attach to Epic, Story, Task, or be standalone
            }
            case EPIC -> throw new InvalidWorkItemRelationshipException(
                    "An EPIC cannot have a parent");
        }
    }

    private void validateParentNotAlreadySubtask(UUID projectId, WorkItem parentTask) {
        if (parentTask.getParentId() != null) {
            Optional<WorkItem> grandParent = workItemRepository.findByIdAndProjectId(
                    parentTask.getParentId(), projectId);

            if (grandParent.isPresent() && grandParent.get().getType() == WorkItemType.TASK) {
                throw new InvalidWorkItemRelationshipException(
                        "Subtasks cannot be nested more than one level deep");
            }
        }
    }

    private void validateChildDoesNotHaveExistingSubtasks(UUID projectId, UUID childId) {
        if (childId != null && workItemRepository.existsByProjectIdAndParentId(projectId, childId)) {
            throw new InvalidWorkItemRelationshipException(
                    "Cannot make task a subtask because it already has child subtasks");
        }
    }

    private void checkForCycles(UUID projectId, UUID childId, UUID parentId) {
        UUID current = parentId;
        Set<UUID> visited = new HashSet<>();

        while (current != null) {
            if (current.equals(childId)) {
                throw new InvalidWorkItemRelationshipException(
                        "Circular parent hierarchy detected: " + childId);
            }
            if (!visited.add(current)) {
                throw new InvalidWorkItemRelationshipException(
                        "Cycle detected among ancestors of: " + parentId);
            }

            Optional<WorkItem> ancestor = workItemRepository.findByIdAndProjectId(
                    current, projectId);
            if (ancestor.isEmpty()) {
                break;
            }
            current = ancestor.get().getParentId();
        }
    }
}