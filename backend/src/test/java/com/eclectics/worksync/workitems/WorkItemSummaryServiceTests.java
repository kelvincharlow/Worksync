package com.eclectics.worksync.workitems;

import com.eclectics.worksync.workitems.dto.ClientWorkProgressSummary;
import com.eclectics.worksync.workitems.dto.WorkItemSummaryResponse;
import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemPriority;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.repository.WorkItemRepository;
import com.eclectics.worksync.workitems.service.WorkItemSummaryServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemSummaryServiceTests {

    @Mock
    private WorkItemRepository workItemRepository;

    @InjectMocks
    private WorkItemSummaryServiceImpl summaryService;

    @Test
    @DisplayName("Aggregates internal status and type counts correctly")
    void getProjectSummary_aggregatesCountsCorrectly() {
        UUID projectId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        // Story in DONE status
        WorkItem item1 = new WorkItem(
                UUID.randomUUID(), projectId, WorkItemType.STORY,
                "Story 1", "Desc", WorkItemPriority.HIGH,
                creatorId, null, null
        );
        item1.changeStatus(WorkItemStatus.DONE);

        // Bug in ASSIGNED status (maps to in-progress delivery bucket)
        WorkItem item2 = new WorkItem(
                UUID.randomUUID(), projectId, WorkItemType.BUG,
                "Bug 1", "Desc", WorkItemPriority.MEDIUM,
                creatorId, null, null
        );
        item2.changeStatus(WorkItemStatus.ASSIGNED);

        when(workItemRepository.findAllByProjectId(projectId))
                .thenReturn(List.of(item1, item2));

        WorkItemSummaryResponse summary = summaryService.getProjectSummary(projectId);

        assertThat(summary.projectId()).isEqualTo(projectId);
        assertThat(summary.totalItems()).isEqualTo(2);
        assertThat(summary.doneCount()).isEqualTo(1);
        assertThat(summary.inProgressCount()).isEqualTo(1);
        assertThat(summary.storyCount()).isEqualTo(1);
        assertThat(summary.bugCount()).isEqualTo(1);
        assertThat(summary.todoCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Computes client progress excluding bugs and subtasks to prevent double-counting")
    void getClientSafeSummary_excludesBugsAndSubtasks() {
        UUID projectId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID parentStoryId = UUID.randomUUID();

        // 1. Deliverable Story (DONE) -> counts
        WorkItem story = new WorkItem(
                parentStoryId, projectId, WorkItemType.STORY,
                "Payment Gateway", null, null,
                creatorId, null, null
        );
        story.changeStatus(WorkItemStatus.DONE);

        // 2. Standalone Task (IN_PROGRESS) -> counts
        WorkItem standaloneTask = new WorkItem(
                UUID.randomUUID(), projectId, WorkItemType.TASK,
                "Setup CDN", null, null,
                creatorId, null, null
        );
        standaloneTask.changeStatus(WorkItemStatus.IN_PROGRESS);

        // 3. Child Subtask under story (DONE) -> excluded from deliverable totals
        WorkItem subtask = new WorkItem(
                UUID.randomUUID(), projectId, WorkItemType.TASK,
                "Integrate Stripe SDK", null, null,
                creatorId, null, parentStoryId
        );
        subtask.changeStatus(WorkItemStatus.DONE);

        // 4. Internal Bug (REPORTED) -> excluded from client progress
        WorkItem bug = new WorkItem(
                UUID.randomUUID(), projectId, WorkItemType.BUG,
                "Button alignment issue", null, null,
                creatorId, null, null
        );

        when(workItemRepository.findAllByProjectId(projectId))
                .thenReturn(List.of(story, standaloneTask, subtask, bug));

        ClientWorkProgressSummary clientSummary = summaryService.getClientSafeSummary(projectId);

        // Only the story and the standalone task count as deliverables (2 total)
        assertThat(clientSummary.totalDeliverables()).isEqualTo(2);
        // Only the story is completed (1 completed)
        assertThat(clientSummary.completedDeliverables()).isEqualTo(1);
        // Standalone task is in progress (1 in-progress)
        assertThat(clientSummary.inProgressDeliverables()).isEqualTo(1);
        // 1 out of 2 = 50.0%
        assertThat(clientSummary.progressPercentage()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Returns 0.0 percentage when project has no work items without division by zero")
    void getClientSafeSummary_handlesEmptyProject() {
        UUID projectId = UUID.randomUUID();
        when(workItemRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        ClientWorkProgressSummary clientSummary = summaryService.getClientSafeSummary(projectId);

        assertThat(clientSummary.totalDeliverables()).isEqualTo(0);
        assertThat(clientSummary.completedDeliverables()).isEqualTo(0);
        assertThat(clientSummary.progressPercentage()).isEqualTo(0.0);
    }
}