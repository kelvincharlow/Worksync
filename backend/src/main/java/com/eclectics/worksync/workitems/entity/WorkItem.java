package com.eclectics.worksync.workitems.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_items")
public class WorkItem {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkItemType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkItemPriority priority;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "milestone_id")
    private UUID milestoneId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorkItem() {}

    public WorkItem(UUID id, UUID projectId, WorkItemType type, 
                    String title, String description, WorkItemPriority priority, 
                    UUID creatorId, UUID assigneeId, UUID parentId, UUID milestoneId) {
        this.id = id != null ? id : UUID.randomUUID();
        this.projectId = projectId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.status = WorkItemStatus.TODO; // Default initial state
        this.priority = priority != null ? priority : WorkItemPriority.MEDIUM;
        this.creatorId = creatorId;
        this.assigneeId = assigneeId;
        this.parentId = parentId;
        this.milestoneId = milestoneId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public WorkItemType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public WorkItemStatus getStatus() { return status; }
    public WorkItemPriority getPriority() { return priority; }
    public UUID getCreatorId() { return creatorId; }
    public UUID getAssigneeId() { return assigneeId; }
    public UUID getParentId() { return parentId; }
    public UUID getMilestoneId() { return milestoneId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Domain Mutators
    public void updateDetails(String title, String description, WorkItemPriority priority) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        this.description = description;
        if (priority != null) {
            this.priority = priority;
        }
        this.updatedAt = Instant.now();
    }

    public void changeStatus(WorkItemStatus newStatus) {
        if (newStatus != null) {
            this.status = newStatus;
            this.updatedAt = Instant.now();
        }
    }

    public void assignTo(UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.updatedAt = Instant.now();
    }

    public void setMilestone(UUID milestoneId) {
        this.milestoneId = milestoneId;
        this.updatedAt = Instant.now();
    }
}