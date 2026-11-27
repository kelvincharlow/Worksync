package com.eclectics.worksync.workitems.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "work_item_links",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_work_item_link",
            columnNames = {"source_id", "target_id", "link_type"}
        )
    }
)
public class WorkItemLink {

    @Id
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 32)
    private WorkItemLinkType linkType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WorkItemLink() {}

    public WorkItemLink(UUID id, UUID sourceId, UUID targetId, WorkItemLinkType linkType) {
        this.id = id != null ? id : UUID.randomUUID();
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.linkType = linkType;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public UUID getTargetId() { return targetId; }
    public WorkItemLinkType getLinkType() { return linkType; }
    public Instant getCreatedAt() { return createdAt; }
}