package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.workitems.dto.ClientWorkProgressSummary;
import com.eclectics.worksync.workitems.dto.WorkItemSummaryResponse;
import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemPriority;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.repository.WorkItemRepository;
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

        WorkItem item1 = new WorkItem(
                UUID.randomUUID(), projectId, WorkItemType.STORY,
                "Story 1", "Desc", WorkItemPriority.HIGH,
                creatorId, null, null, null
        );
        item1.changeStatus(WorkItemStatus.DONE);

        WorkItem item2 = new WorkItem(
                UUID.randomUUID(), projectId, WorkItemType.BUG,
                "Bug 1", "Desc", WorkItemPriority.MEDIUM,
                creatorId, null, null, null
        );
        item2.changeStatus(WorkItemStatus.IN_PROGRESS);

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
    @DisplayName("Computes client-safe deliverable progress percentage correctly")
    void getClientSafeSummary_computesProgressPercentage() {
        UUID projectId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        WorkItem doneItem = new WorkItem(
                UUID.randomUUID(), projectId, WorkItemType.TASK,
                "Task 1", "Desc", WorkItemPriority.LOW,
                creatorId, null, null, null
        );
        doneItem.changeStatus(WorkItemStatus.DONE);

        WorkItem inProgressItem = new WorkItem(
                UUID.randomUUID(), projectId, WorkItemType.TASK,
                "Task 2", "Desc", WorkItemPriority.LOW,
                creatorId, null, null, null
        );
        inProgressItem.changeStatus(WorkItemStatus.IN_PROGRESS);

        when(workItemRepository.findAllByProjectId(projectId))
                .thenReturn(List.of(doneItem, inProgressItem));

        ClientWorkProgressSummary clientSummary = summaryService.getClientSafeSummary(projectId);

        assertThat(clientSummary.totalDeliverables()).isEqualTo(2);
        assertThat(clientSummary.completedDeliverables()).isEqualTo(1);
        assertThat(clientSummary.inProgressDeliverables()).isEqualTo(1);
        assertThat(clientSummary.progressPercentage()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Returns zero percentage when project has no work items without division by zero")
    void getClientSafeSummary_handlesEmptyProject() {
        UUID projectId = UUID.randomUUID();
        when(workItemRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        ClientWorkProgressSummary clientSummary = summaryService.getClientSafeSummary(projectId);

        assertThat(clientSummary.totalDeliverables()).isEqualTo(0);
        assertThat(clientSummary.completedDeliverables()).isEqualTo(0);
        assertThat(clientSummary.progressPercentage()).isEqualTo(0.0);
    }
}