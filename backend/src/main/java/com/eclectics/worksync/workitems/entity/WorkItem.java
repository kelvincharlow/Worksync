package com.eclectics.worksync.workitems.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "work_items")
public class WorkItem {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkItemType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkItemPriority priority;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorkItem() {}

    public WorkItem(UUID id, UUID projectId, WorkItemType type,
                    String title, String description, WorkItemPriority priority,
                    UUID creatorId, UUID assigneeId, UUID parentId) {

        this.id = id != null ? id : UUID.randomUUID();
        this.projectId = Objects.requireNonNull(projectId, "Project ID is required");
        this.type = Objects.requireNonNull(type, "Work item type is required");
        this.creatorId = Objects.requireNonNull(creatorId, "Creator ID is required");

        this.title = validateAndTrimTitle(title);
        this.description = description;
        this.priority = priority != null ? priority : WorkItemPriority.MEDIUM;

        // Default initial status based on type
        this.status = (type == WorkItemType.BUG) ? WorkItemStatus.REPORTED : WorkItemStatus.TODO;

        this.assigneeId = assigneeId;
        this.setParent(parentId);

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Invariant helpers
    private String validateAndTrimTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
        String trimmed = rawTitle.trim();
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("Title cannot exceed 255 characters");
        }
        return trimmed;
    }

    public void setParent(UUID parentId) {
        if (parentId != null) {
            if (this.id != null && parentId.equals(this.id)) {
                throw new IllegalArgumentException("Work item cannot reference itself as parent");
            }
            if (this.type == WorkItemType.EPIC) {
                throw new IllegalArgumentException("An EPIC cannot have a parent work item");
            }
        }
        this.parentId = parentId;
        this.updatedAt = Instant.now();
    }

    public void updateDetails(String newTitle, String newDescription, WorkItemPriority newPriority) {
        if (newTitle != null) {
            this.title = validateAndTrimTitle(newTitle);
        }
        this.description = newDescription;
        if (newPriority != null) {
            this.priority = newPriority;
        }
        this.updatedAt = Instant.now();
    }

    public void changeStatus(WorkItemStatus newStatus) {
        Objects.requireNonNull(newStatus, "Status cannot be null");
        if (!newStatus.isValidFor(this.type)) {
            throw new IllegalArgumentException(
                    "Status " + newStatus + " is not valid for work item type " + this.type);
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public void assignTo(UUID assigneeId) {
        this.assigneeId = assigneeId;
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
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}