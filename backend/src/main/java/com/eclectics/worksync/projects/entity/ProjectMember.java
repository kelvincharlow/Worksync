package com.eclectics.worksync.projects.entity;

import com.eclectics.worksync.projects.exception.InvalidProjectMembershipException;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "project_members", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMember {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false) private UUID id;
    @NotNull @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @NotNull @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ProjectRole role;
    @Enumerated(EnumType.STRING) @Column(length = 32) private ProjectResponsibility responsibility;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ProjectMember() { }
    public ProjectMember(UUID projectId, UUID userId, ProjectRole role, ProjectResponsibility responsibility) {
        this.projectId = Objects.requireNonNull(projectId);
        this.userId = Objects.requireNonNull(userId);
        this.role = role;
        this.responsibility = responsibility;
        if (!isRoleResponsibilityValid()) throw new InvalidProjectMembershipException();
    }
    @AssertTrue(message = "MEMBER requires a responsibility; MANAGER/VIEWER must have none")
    @Transient
    public boolean isRoleResponsibilityValid() {
        return role != null && (role == ProjectRole.MEMBER ? responsibility != null : responsibility == null);
    }
    @PrePersist private void insertTimestamps() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate private void updateTimestamp() { updatedAt = Instant.now(); }
    // Membership changes must eventually use an actor-aware, locked service, not unrestricted setters.
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getUserId() { return userId; }
    public ProjectRole getRole() { return role; }
    public ProjectResponsibility getResponsibility() { return responsibility; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
