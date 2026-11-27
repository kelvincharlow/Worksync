package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.projects.dto.ProjectMemberAccess;
import com.eclectics.worksync.projects.entity.ProjectAction;
import com.eclectics.worksync.projects.entity.ProjectResponsibility;
import com.eclectics.worksync.projects.entity.ProjectRole;
import com.eclectics.worksync.projects.entity.ProjectStatus;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemServiceTests {

    @Mock
    private WorkItemRepository workItemRepository;

    @Mock
    private WorkItemRelationshipService relationshipService;

    @Mock
    private ProjectAccessService projectAccessService;

    @InjectMocks
    private WorkItemServiceImpl workItemService;

    private final UUID projectId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Test
    @DisplayName("Manager creates a story successfully with security and action checks")
    void createWorkItem_managerStory_success() {
        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.STORY, "User Dashboard",
                "Display cards", WorkItemPriority.HIGH,
                null, null, null
        );

        when(projectAccessService.getMemberAccess(projectId, actorId))
                .thenReturn(new ProjectMemberAccess(
                        projectId, actorId, ProjectRole.MANAGER, 
                        null, false, ProjectStatus.ACTIVE));
        when(workItemRepository.save(any(WorkItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkItemResponse response = workItemService.createWorkItem(
                projectId, actorId, request);

        assertThat(response.id()).isNotNull();
        assertThat(response.title()).isEqualTo("User Dashboard");
        assertThat(response.type()).isEqualTo(WorkItemType.STORY);

        verify(projectAccessService).requireWritable(projectId);
        verify(projectAccessService).requireAction(
                projectId, actorId, ProjectAction.CREATE_STORY);
    }

    @Test
    @DisplayName("Developer creating a task is automatically assigned to themselves")
    void createWorkItem_developerSelfAssignedTask_success() {
        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.TASK, "Fix bug in auth",
                "Details", WorkItemPriority.MEDIUM,
                null, null, null
        );

        when(projectAccessService.getMemberAccess(projectId, actorId))
                .thenReturn(new ProjectMemberAccess(
                        projectId, actorId, ProjectRole.MEMBER, 
                        ProjectResponsibility.DEVELOPER, false, ProjectStatus.ACTIVE));
        when(workItemRepository.save(any(WorkItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkItemResponse response = workItemService.createWorkItem(
                projectId, actorId, request);

        assertThat(response.assigneeId()).isEqualTo(actorId);
        verify(projectAccessService).requireAction(
                projectId, actorId, ProjectAction.CREATE_TASK);
    }

    @Test
    @DisplayName("Developer attempting to assign a task to someone else throws InvalidAssigneeException")
    void createWorkItem_developerAssigningToOther_throwsException() {
        UUID otherUserId = UUID.randomUUID();
        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.TASK, "Develop API", null, null,
                otherUserId, null, null
        );

        when(projectAccessService.getMemberAccess(projectId, actorId))
                .thenReturn(new ProjectMemberAccess(
                        projectId, actorId, ProjectRole.MEMBER, 
                        ProjectResponsibility.DEVELOPER, false, ProjectStatus.ACTIVE));

        assertThatThrownBy(() -> workItemService.createWorkItem(
                projectId, actorId, request))
                .isInstanceOf(InvalidAssigneeException.class)
                .hasMessageContaining("Developers can only create tasks assigned to themselves");
    }

    @Test
    @DisplayName("Developer attempting to create an Epic throws ProjectAccessDeniedException")
    void createWorkItem_developerCreatingEpic_throwsException() {
        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.EPIC, "Q1 Roadmap", null, null,
                null, null, null
        );

        when(projectAccessService.getMemberAccess(projectId, actorId))
                .thenReturn(new ProjectMemberAccess(
                        projectId, actorId, ProjectRole.MEMBER, 
                        ProjectResponsibility.DEVELOPER, false, ProjectStatus.ACTIVE));

        assertThatThrownBy(() -> workItemService.createWorkItem(
                projectId, actorId, request))
                .isInstanceOf(ProjectAccessDeniedException.class);
    }

    @Test
    @DisplayName("Validates hierarchy with relationshipService when parentId is provided")
    void createWorkItem_validatesHierarchy() {
        UUID parentId = UUID.randomUUID();
        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.TASK, "Child Task", null, null,
                null, parentId, null
        );

        when(projectAccessService.getMemberAccess(projectId, actorId))
                .thenReturn(new ProjectMemberAccess(
                        projectId, actorId, ProjectRole.MANAGER, 
                        null, false, ProjectStatus.ACTIVE));
        when(workItemRepository.save(any(WorkItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        workItemService.createWorkItem(projectId, actorId, request);

        verify(relationshipService).validateParentRelationship(
                projectId, null, WorkItemType.TASK, parentId);
    }

    @Test
    @DisplayName("Retrieves an existing work item by ID and project ID")
    void getWorkItem_success() {
        UUID workItemId = UUID.randomUUID();
        WorkItem item = new WorkItem(
                workItemId, projectId, WorkItemType.TASK,
                "Existing Task", "Desc", WorkItemPriority.LOW,
                actorId, null, null
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
        UUID nonExistentId = UUID.randomUUID();
        when(workItemRepository.findByIdAndProjectId(nonExistentId, projectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workItemService.getWorkItem(
                projectId, nonExistentId))
                .isInstanceOf(WorkItemNotFoundException.class);
    }

    @Test
    @DisplayName("Updates permitted fields and verifies project is writable")
    void updateWorkItem_success() {
        UUID workItemId = UUID.randomUUID();
        WorkItem item = new WorkItem(
                workItemId, projectId, WorkItemType.TASK,
                "Old Title", "Old Desc", WorkItemPriority.LOW,
                actorId, null, null
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
        verify(projectAccessService).requireWritable(projectId);
    }
}