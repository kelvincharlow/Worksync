package com.eclectics.worksync.workitems.repository;

import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {

    // Single item project-scoped access
    Optional<WorkItem> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);

    // Unbounded queries for summaries and small lists
    List<WorkItem> findAllByProjectId(UUID projectId);

    List<WorkItem> findAllByProjectIdAndType(UUID projectId, WorkItemType type);

    List<WorkItem> findAllByProjectIdAndStatus(UUID projectId, WorkItemStatus status);

    List<WorkItem> findAllByProjectIdAndAssigneeId(UUID projectId, UUID assigneeId);

    // Hierarchy queries (guaranteed within the same project)
    List<WorkItem> findAllByProjectIdAndParentId(UUID projectId, UUID parentId);

    List<WorkItem> findAllByProjectIdAndParentIdIsNull(UUID projectId);

    boolean existsByProjectIdAndParentId(UUID projectId, UUID parentId);

    // Paginated queries for large project backlogs and boards
    Page<WorkItem> findAllByProjectId(UUID projectId, Pageable pageable);

    Page<WorkItem> findAllByProjectIdAndType(
            UUID projectId, WorkItemType type, Pageable pageable);

    Page<WorkItem> findAllByProjectIdAndStatus(
            UUID projectId, WorkItemStatus status, Pageable pageable);

    // Aggregation counts
    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, WorkItemStatus status);

    long countByProjectIdAndType(UUID projectId, WorkItemType type);
}