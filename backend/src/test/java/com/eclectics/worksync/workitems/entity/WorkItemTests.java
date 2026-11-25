package com.eclectics.worksync.workitems.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemTests {

    @Test
    @DisplayName("Initializes with default status TODO and fallback priority MEDIUM")
    void initializesWithSafeDefaults() {
        UUID projectId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        WorkItem item = new WorkItem(
                null, projectId, WorkItemType.TASK,
                "New Task", "Description", null,
                creatorId, null, null, null
        );

        assertThat(item.getId()).isNotNull();
        assertThat(item.getStatus()).isEqualTo(WorkItemStatus.TODO);
        assertThat(item.getPriority()).isEqualTo(WorkItemPriority.MEDIUM);
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Updates title, description and priority while refreshing updatedAt")
    void updatesDetailsCorrectly() {
        WorkItem item = createSampleTask();
        Instant beforeUpdate = item.getUpdatedAt();

        item.updateDetails("Updated Title", "New Desc", WorkItemPriority.HIGH);

        assertThat(item.getTitle()).isEqualTo("Updated Title");
        assertThat(item.getDescription()).isEqualTo("New Desc");
        assertThat(item.getPriority()).isEqualTo(WorkItemPriority.HIGH);
        assertThat(item.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    @DisplayName("Changes status and updates assignee and milestone properly")
    void mutatesStateAndRelationships() {
        WorkItem item = createSampleTask();
        UUID assigneeId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();

        item.changeStatus(WorkItemStatus.IN_PROGRESS);
        item.assignTo(assigneeId);
        item.setMilestone(milestoneId);

        assertThat(item.getStatus()).isEqualTo(WorkItemStatus.IN_PROGRESS);
        assertThat(item.getAssigneeId()).isEqualTo(assigneeId);
        assertThat(item.getMilestoneId()).isEqualTo(milestoneId);
    }

    private WorkItem createSampleTask() {
        return new WorkItem(
                UUID.randomUUID(), UUID.randomUUID(), WorkItemType.TASK,
                "Initial Title", "Initial Desc", WorkItemPriority.LOW,
                UUID.randomUUID(), null, null, null
        );
    }
}