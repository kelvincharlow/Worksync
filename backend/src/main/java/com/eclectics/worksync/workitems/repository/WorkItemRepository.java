package com.eclectics.worksync.workitems.repository;

import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {

    // Enforce project scoping on all lookups
    Optional<WorkItem> findByIdAndProjectId(UUID id, UUID projectId);

    List<WorkItem> findAllByProjectId(UUID projectId);

    List<WorkItem> findAllByProjectIdAndType(UUID projectId, WorkItemType type);

    List<WorkItem> findAllByProjectIdAndStatus(UUID projectId, WorkItemStatus status);

    List<WorkItem> findAllByProjectIdAndAssigneeId(UUID projectId, UUID assigneeId);

    List<WorkItem> findAllByProjectIdAndParentId(UUID projectId, UUID parentId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, WorkItemStatus status);
}