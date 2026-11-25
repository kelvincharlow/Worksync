package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.workitems.dto.CreateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.UpdateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.WorkItemResponse;
import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemPriority;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.exception.WorkItemNotFoundException;
import com.eclectics.worksync.workitems.repository.WorkItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemServiceTests {

    @Mock
    private WorkItemRepository workItemRepository;

    @InjectMocks
    private WorkItemServiceImpl workItemService;

    @Test
    @DisplayName("Creates a work item successfully when data is valid")
    void createWorkItem_success() {
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.STORY,
                "User Dashboard",
                "Display project cards",
                WorkItemPriority.HIGH,
                null, null, null
        );

        when(workItemRepository.save(any(WorkItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkItemResponse response = workItemService.createWorkItem(
                projectId, actorId, request);

        assertThat(response.id()).isNotNull();
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.title()).isEqualTo("User Dashboard");
        assertThat(response.type()).isEqualTo(WorkItemType.STORY);
        assertThat(response.status()).isEqualTo(WorkItemStatus.TODO);
        assertThat(response.creatorId()).isEqualTo(actorId);
    }

    @Test
    @DisplayName("Throws WorkItemNotFoundException if parentId does not exist in project")
    void createWorkItem_parentNotFound_throwsException() {
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID invalidParentId = UUID.randomUUID();

        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.TASK,
                "Child Task",
                "Subtask details",
                WorkItemPriority.MEDIUM,
                null, invalidParentId, null
        );

        when(workItemRepository.findByIdAndProjectId(invalidParentId, projectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workItemService.createWorkItem(
                projectId, actorId, request))
                .isInstanceOf(WorkItemNotFoundException.class)
                .hasMessageContaining("Parent work item not found in this project");
    }

    @Test
    @DisplayName("Retrieves an existing work item by ID and project ID")
    void getWorkItem_success() {
        UUID projectId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();

        WorkItem item = new WorkItem(
                workItemId, projectId, WorkItemType.TASK,
                "Existing Task", "Desc", WorkItemPriority.LOW,
                UUID.randomUUID(), null, null, null
        );

        when(workItemRepository.findByIdAndProjectId(workItemId, projectId))
                .thenReturn(Optional.of(item));

        WorkItemResponse response = workItemService.getWorkItem(
                projectId, workItemId);

        assertThat(response.id()).isEqualTo(workItemId);
        assertThat(response.title()).isEqualTo("Existing Task");
    }

    @Test
    @DisplayName("Throws WorkItemNotFoundException when retrieving non-existent item")
    void getWorkItem_notFound_throwsException() {
        UUID projectId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();

        when(workItemRepository.findByIdAndProjectId(nonExistentId, projectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workItemService.getWorkItem(
                projectId, nonExistentId))
                .isInstanceOf(WorkItemNotFoundException.class);
    }

    @Test
    @DisplayName("Updates permitted fields on an existing work item")
    void updateWorkItem_success() {
        UUID projectId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        WorkItem item = new WorkItem(
                workItemId, projectId, WorkItemType.TASK,
                "Old Title", "Old Desc", WorkItemPriority.LOW,
                actorId, null, null, null
        );

        when(workItemRepository.findByIdAndProjectId(workItemId, projectId))
                .thenReturn(Optional.of(item));
        when(workItemRepository.save(any(WorkItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateWorkItemRequest updateRequest = new UpdateWorkItemRequest(
                "New Title", "New Desc",
                WorkItemStatus.IN_PROGRESS,
                WorkItemPriority.URGENT,
                null
        );

        WorkItemResponse response = workItemService.updateWorkItem(
                projectId, workItemId, actorId, updateRequest);

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.status()).isEqualTo(WorkItemStatus.IN_PROGRESS);
        assertThat(response.priority()).isEqualTo(WorkItemPriority.URGENT);
    }
}