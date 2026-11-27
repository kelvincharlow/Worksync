package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemLinkType;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.exception.InvalidWorkItemRelationshipException;
import com.eclectics.worksync.workitems.exception.WorkItemNotFoundException;
import com.eclectics.worksync.workitems.repository.WorkItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemRelationshipServiceTests {

    @Mock
    private WorkItemRepository workItemRepository;

    @InjectMocks
    private WorkItemRelationshipService relationshipService;

    private final UUID projectId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    @Test
    @DisplayName("Valid Epic -> Story -> Task hierarchy succeeds")
    void validHierarchySucceeds() {
        UUID epicId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        WorkItem epic = new WorkItem(
                epicId, projectId, WorkItemType.EPIC, "Epic", null, null, creatorId, null, null
        );
        WorkItem story = new WorkItem(
                storyId, projectId, WorkItemType.STORY, "Story", null, null, creatorId, null, epicId
        );

        when(workItemRepository.findByIdAndProjectId(epicId, projectId))
                .thenReturn(Optional.of(epic));
        when(workItemRepository.findByIdAndProjectId(storyId, projectId))
                .thenReturn(Optional.of(story));

        assertThatCode(() -> relationshipService.validateParentRelationship(
                projectId, null, WorkItemType.STORY, epicId
        )).doesNotThrowAnyException();

        assertThatCode(() -> relationshipService.validateParentRelationship(
                projectId, null, WorkItemType.TASK, storyId
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Standalone items without a parent are accepted")
    void standaloneItemsSucceed() {
        assertThatCode(() -> relationshipService.validateParentRelationship(
                projectId, null, WorkItemType.TASK, null
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Cross-project parent reference fails with WorkItemNotFoundException")
    void crossProjectParentFails() {
        UUID parentId = UUID.randomUUID();
        when(workItemRepository.findByIdAndProjectId(parentId, projectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.validateParentRelationship(
                projectId, null, WorkItemType.STORY, parentId
        )).isInstanceOf(WorkItemNotFoundException.class)
          .hasMessageContaining("Parent work item not found in project");
    }

    @Test
    @DisplayName("Invalid parent-child type combination fails (e.g., Story under Task)")
    void invalidHierarchyTypeFails() {
        UUID parentTaskId = UUID.randomUUID();
        WorkItem parentTask = new WorkItem(
                parentTaskId, projectId, WorkItemType.TASK, "Task", null, null, creatorId, null, null
        );

        when(workItemRepository.findByIdAndProjectId(parentTaskId, projectId))
                .thenReturn(Optional.of(parentTask));

        assertThatThrownBy(() -> relationshipService.validateParentRelationship(
                projectId, null, WorkItemType.STORY, parentTaskId
        )).isInstanceOf(InvalidWorkItemRelationshipException.class)
          .hasMessageContaining("A STORY can only belong under an EPIC");
    }

    @Test
    @DisplayName("Epic cannot have a parent work item")
    void epicWithParentFails() {
        UUID parentId = UUID.randomUUID();

        assertThatThrownBy(() -> relationshipService.validateParentRelationship(
                projectId, null, WorkItemType.EPIC, parentId
        )).isInstanceOf(InvalidWorkItemRelationshipException.class)
          .hasMessageContaining("An EPIC cannot have a parent work item");
    }

    @Test
    @DisplayName("Subtask nesting beyond one level deep is rejected (parent is already subtask)")
    void subtaskNestingBeyondLimitFails() {
        UUID grandParentTaskId = UUID.randomUUID();
        UUID parentSubtaskId = UUID.randomUUID();

        WorkItem grandParentTask = new WorkItem(
                grandParentTaskId, projectId, WorkItemType.TASK, "Parent Task",
                null, null, creatorId, null, null
        );
        WorkItem parentSubtask = new WorkItem(
                parentSubtaskId, projectId, WorkItemType.TASK, "Subtask 1",
                null, null, creatorId, null, grandParentTaskId
        );

        when(workItemRepository.findByIdAndProjectId(parentSubtaskId, projectId))
                .thenReturn(Optional.of(parentSubtask));
        when(workItemRepository.findByIdAndProjectId(grandParentTaskId, projectId))
                .thenReturn(Optional.of(grandParentTask));

        assertThatThrownBy(() -> relationshipService.validateParentRelationship(
                projectId, null, WorkItemType.TASK, parentSubtaskId
        )).isInstanceOf(InvalidWorkItemRelationshipException.class)
          .hasMessageContaining("Subtasks cannot be nested more than one level deep");
    }

    @Test
    @DisplayName("Reparenting task fails if it already has child subtasks of its own")
    void reparentingTaskWithExistingSubtasksFails() {
        UUID taskAId = UUID.randomUUID();
        UUID newParentTaskId = UUID.randomUUID();

        WorkItem newParentTask = new WorkItem(
                newParentTaskId, projectId, WorkItemType.TASK, "New Parent Task",
                null, null, creatorId, null, null
        );

        when(workItemRepository.findByIdAndProjectId(newParentTaskId, projectId))
                .thenReturn(Optional.of(newParentTask));

        // Task A already has child subtasks in the database
        when(workItemRepository.existsByProjectIdAndParentId(projectId, taskAId))
                .thenReturn(true);

        // Reparenting Task A under New Parent Task must be rejected to prevent 2-level nesting
        assertThatThrownBy(() -> relationshipService.validateParentRelationship(
                projectId, taskAId, WorkItemType.TASK, newParentTaskId
        )).isInstanceOf(InvalidWorkItemRelationshipException.class)
          .hasMessageContaining("Cannot make task a subtask because it already has child subtasks");
    }

    @Test
    @DisplayName("Direct self-parent relationship fails")
    void directCycleFails() {
        UUID itemId = UUID.randomUUID();

        assertThatThrownBy(() -> relationshipService.validateParentRelationship(
                projectId, itemId, WorkItemType.TASK, itemId
        )).isInstanceOf(InvalidWorkItemRelationshipException.class)
          .hasMessageContaining("Work item cannot reference itself as parent");
    }

    @Test
    @DisplayName("Indirect cycle fails (A -> B -> A)")
    void indirectCycleFails() {
        UUID taskAId = UUID.randomUUID();
        UUID taskBId = UUID.randomUUID();

        WorkItem taskB = new WorkItem(
                taskBId, projectId, WorkItemType.TASK, "Task B", null, null, creatorId, null, taskAId
        );

        when(workItemRepository.findByIdAndProjectId(taskBId, projectId))
                .thenReturn(Optional.of(taskB));

        assertThatThrownBy(() -> relationshipService.validateParentRelationship(
                projectId, taskAId, WorkItemType.TASK, taskBId
        )).isInstanceOf(InvalidWorkItemRelationshipException.class)
          .hasMessageContaining("Circular parent hierarchy detected");
    }

    @Test
    @DisplayName("Work item link self-reference fails")
    void linkSelfReferenceFails() {
        UUID itemId = UUID.randomUUID();

        assertThatThrownBy(() -> relationshipService.validateLinkRelationship(
                projectId, itemId, itemId, WorkItemLinkType.BLOCKS
        )).isInstanceOf(InvalidWorkItemRelationshipException.class)
          .hasMessageContaining("Work item cannot link to itself");
    }

    @Test
    @DisplayName("Work item link with valid items in same project succeeds")
    void validLinkSucceeds() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(workItemRepository.existsByIdAndProjectId(sourceId, projectId)).thenReturn(true);
        when(workItemRepository.existsByIdAndProjectId(targetId, projectId)).thenReturn(true);

        assertThatCode(() -> relationshipService.validateLinkRelationship(
                projectId, sourceId, targetId, WorkItemLinkType.BLOCKS
        )).doesNotThrowAnyException();
    }
}