package com.eclectics.worksync.workitems.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkItemTests {

    @Test
    @DisplayName("Initializes task with default TODO and fallback priority MEDIUM")
    void initializesTaskWithSafeDefaults() {
        UUID projectId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        WorkItem item = new WorkItem(
                null, projectId, WorkItemType.TASK,
                "New Task", "Description", null,
                creatorId, null, null
        );

        assertThat(item.getId()).isNotNull();
        assertThat(item.getStatus()).isEqualTo(WorkItemStatus.TODO);
        assertThat(item.getPriority()).isEqualTo(WorkItemPriority.MEDIUM);
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Initializes bug with default status REPORTED")
    void initializesBugWithReportedStatus() {
        WorkItem bug = new WorkItem(
                null, UUID.randomUUID(), WorkItemType.BUG,
                "NullPointer in auth", "Steps to reproduce", null,
                UUID.randomUUID(), null, null
        );

        assertThat(bug.getStatus()).isEqualTo(WorkItemStatus.REPORTED);
    }

    @Test
    @DisplayName("Throws exception if required fields are missing")
    void rejectsMissingRequiredFields() {
        UUID projectId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        assertThatThrownBy(() -> new WorkItem(
                null, null, WorkItemType.TASK, "Title", null, null, creatorId, null, null
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Project ID is required");

        assertThatThrownBy(() -> new WorkItem(
                null, projectId, null, "Title", null, null, creatorId, null, null
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Work item type is required");

        assertThatThrownBy(() -> new WorkItem(
                null, projectId, WorkItemType.TASK, "   ", null, null, creatorId, null, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Title cannot be blank");
    }

    @Test
    @DisplayName("Prevents an EPIC from having a parent work item")
    void rejectsParentForEpic() {
        UUID parentId = UUID.randomUUID();

        assertThatThrownBy(() -> new WorkItem(
                null, UUID.randomUUID(), WorkItemType.EPIC,
                "Q1 Initiative", "Epic desc", null,
                UUID.randomUUID(), null, parentId
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("An EPIC cannot have a parent work item");
    }

    @Test
    @DisplayName("Prevents a work item from referencing itself as parent")
    void rejectsSelfAsParent() {
        UUID itemId = UUID.randomUUID();
        WorkItem item = new WorkItem(
                itemId, UUID.randomUUID(), WorkItemType.TASK,
                "Self parent test", null, null,
                UUID.randomUUID(), null, null
        );

        assertThatThrownBy(() -> item.setParent(itemId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Work item cannot reference itself as parent");
    }

    @Test
    @DisplayName("Enforces valid sequential status transitions for tasks")
    void enforcesStatusTransitionsForTasks() {
        WorkItem task = new WorkItem(
                null, UUID.randomUUID(), WorkItemType.TASK,
                "Backend Task", null, null,
                UUID.randomUUID(), null, null
        );

        task.changeStatus(WorkItemStatus.IN_PROGRESS);
        task.changeStatus(WorkItemStatus.IN_REVIEW);
        task.changeStatus(WorkItemStatus.DONE);
        assertThat(task.getStatus()).isEqualTo(WorkItemStatus.DONE);

        // Setting a task to a bug-only status must throw
        assertThatThrownBy(() -> task.changeStatus(WorkItemStatus.TRIAGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status TRIAGE is not valid for TASK");
    }

    @Test
    @DisplayName("Enforces full sequential lifecycle for bugs")
    void enforcesSequentialBugLifecycle() {
        WorkItem bug = new WorkItem(
                null, UUID.randomUUID(), WorkItemType.BUG,
                "Broken login", null, null,
                UUID.randomUUID(), null, null
        );

        // REPORTED -> TRIAGE -> ASSIGNED -> IN_PROGRESS -> READY_FOR_VERIFICATION -> CLOSED
        bug.changeStatus(WorkItemStatus.TRIAGE);
        bug.changeStatus(WorkItemStatus.ASSIGNED);
        bug.changeStatus(WorkItemStatus.IN_PROGRESS);
        bug.changeStatus(WorkItemStatus.READY_FOR_VERIFICATION);
        bug.changeStatus(WorkItemStatus.CLOSED);

        assertThat(bug.getStatus()).isEqualTo(WorkItemStatus.CLOSED);
    }

    @Test
    @DisplayName("Rejects illegal status jump from TRIAGE directly to READY_FOR_VERIFICATION")
    void rejectsIllegalBugStatusJump() {
        WorkItem bug = new WorkItem(
                null, UUID.randomUUID(), WorkItemType.BUG,
                "Crash on submit", null, null,
                UUID.randomUUID(), null, null
        );

        bug.changeStatus(WorkItemStatus.TRIAGE);

        // Jumping directly to READY_FOR_VERIFICATION without fixing must fail
        assertThatThrownBy(() -> bug.changeStatus(WorkItemStatus.READY_FOR_VERIFICATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot transition BUG from TRIAGE to READY_FOR_VERIFICATION");

        // Status must remain TRIAGE (not mutated on error)
        assertThat(bug.getStatus()).isEqualTo(WorkItemStatus.TRIAGE);
    }
}