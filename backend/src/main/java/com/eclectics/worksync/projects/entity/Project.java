package com.eclectics.worksync.projects.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/** Projects persistence only. User references are UUIDs, never cross-domain JPA entities. */
@Entity
@Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;
    @NotNull @Column(name = "creator_user_id", nullable = false, updatable = false)
    private UUID creatorUserId;
    @NotNull @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;
    @NotBlank @Size(max = 200) @Column(nullable = false, length = 200)
    private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(columnDefinition = "text") private String requirements;
    @Column(columnDefinition = "text") private String scope;
    @Column(columnDefinition = "text") private String deliverables;
    @Column(name = "success_criteria", columnDefinition = "text") private String successCriteria;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "due_date") private LocalDate dueDate;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ProjectStatus status = ProjectStatus.ACTIVE;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @ElementCollection @CollectionTable(name="project_enabled_views", joinColumns=@JoinColumn(name="project_id"))
    @Enumerated(EnumType.STRING) @Column(name="view", nullable=false, length=16)
    private Set<ProjectOptionalView> enabledViews = new HashSet<>();
    @ElementCollection @CollectionTable(name="project_enabled_work_types", joinColumns=@JoinColumn(name="project_id"))
    @Enumerated(EnumType.STRING) @Column(name="work_type", nullable=false, length=16)
    private Set<ProjectWorkType> enabledWorkTypes = new HashSet<>();

    public Set<ProjectOptionalView> getEnabledViews() { return Set.copyOf(enabledViews); }
    public Set<ProjectWorkType> getEnabledWorkTypes() { return Set.copyOf(enabledWorkTypes); }
    /** Optional capabilities are unavailable until frontend/Work Items enforcement is agreed. */
    public void updateConfiguration(Set<ProjectOptionalView> views, Set<ProjectWorkType> types) {
        if ((views != null && !views.isEmpty()) || (types != null && !types.isEmpty()))
            throw new IllegalArgumentException("Unavailable project feature");
        if (views != null) { enabledViews.clear(); enabledViews.addAll(views); }
        if (types != null) { enabledWorkTypes.clear(); enabledWorkTypes.addAll(types); }
    }
    protected Project() { }

    /** Only the authorized creation transaction may pair this with its initial MANAGER membership. */
    public Project(UUID creatorUserId, String name, String description, String requirements,
            String scope, String deliverables, String successCriteria, LocalDate startDate, LocalDate dueDate) {
        this.creatorUserId = Objects.requireNonNull(creatorUserId);
        this.ownerUserId = creatorUserId;
        this.name = name;
        this.description = description;
        this.requirements = requirements;
        this.scope = scope;
        this.deliverables = deliverables;
        this.successCriteria = successCriteria;
        this.startDate = startDate;
        this.dueDate = dueDate;
        if (!isDateRangeValid()) throw new IllegalArgumentException("Invalid project date range");
    }

    /** Called only by the locked transfer service after checking current Owner and target MANAGER eligibility. */
    public void transferOwnership(UUID newOwnerUserId) {
        Objects.requireNonNull(newOwnerUserId,"New Owner is required");
        if(newOwnerUserId.equals(ownerUserId)) throw new IllegalArgumentException("Different Owner required");
        ownerUserId=newOwnerUserId;
    }

    /** Controlled brief update only; validate the complete resulting dates before changing any field. */
    public void updateDetails(String name,String description,String requirements,String scope,String deliverables,
            String successCriteria,LocalDate startDate,LocalDate dueDate) {
        if (name==null || name.isBlank() || name.length()>200) throw new IllegalArgumentException("Invalid project name");
        if (startDate!=null && dueDate!=null && startDate.isAfter(dueDate)) throw new IllegalArgumentException("Invalid project date range");
        this.name=name;this.description=description;this.requirements=requirements;this.scope=scope;
        this.deliverables=deliverables;this.successCriteria=successCriteria;this.startDate=startDate;this.dueDate=dueDate;
    }

    @AssertTrue(message = "startDate must not be after dueDate")
    @Transient
    public boolean isDateRangeValid() {
        return startDate == null || dueDate == null || !startDate.isAfter(dueDate);
    }

    @PrePersist private void insertTimestamps() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate private void updateTimestamp() { updatedAt = Instant.now(); }

    // No general owner/status setters: ownership transfer and archive workflows are later slices.
    public UUID getId() { return id; }
    public UUID getCreatorUserId() { return creatorUserId; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getRequirements() { return requirements; }
    public String getScope() { return scope; }
    public String getDeliverables() { return deliverables; }
    public String getSuccessCriteria() { return successCriteria; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public ProjectStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
