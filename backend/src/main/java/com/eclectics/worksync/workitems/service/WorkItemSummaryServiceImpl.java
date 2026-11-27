package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.workitems.dto.ClientWorkProgressSummary;
import com.eclectics.worksync.workitems.dto.WorkItemSummaryResponse;
import com.eclectics.worksync.workitems.entity.WorkItem;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.repository.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkItemSummaryServiceImpl implements WorkItemSummaryService {

    private final WorkItemRepository workItemRepository;

    public WorkItemSummaryServiceImpl(WorkItemRepository workItemRepository) {
        this.workItemRepository = workItemRepository;
    }

    @Override
    public WorkItemSummaryResponse getProjectSummary(UUID projectId) {
        List<WorkItem> items = workItemRepository.findAllByProjectId(projectId);

        long total = items.size();

        // Map generic and bug statuses into delivery health buckets
        long todo = items.stream().filter(this::isTodoOrReported).count();
        long inProgress = items.stream().filter(this::isInProgress).count();
        long inReview = items.stream().filter(this::isInReviewOrVerifying).count();
        long done = items.stream().filter(this::isDoneOrClosed).count();

        long epics = countType(items, WorkItemType.EPIC);
        long stories = countType(items, WorkItemType.STORY);
        long tasks = countType(items, WorkItemType.TASK);
        long bugs = countType(items, WorkItemType.BUG);

        return new WorkItemSummaryResponse(
                projectId, total, todo, inProgress, inReview, done,
                epics, stories, tasks, bugs
        );
    }

    @Override
    public ClientWorkProgressSummary getClientSafeSummary(UUID projectId) {
        List<WorkItem> items = workItemRepository.findAllByProjectId(projectId);

        // Client deliverables = Stories + standalone Tasks (no double-counting subtasks or Epics)
        List<WorkItem> deliverables = items.stream()
                .filter(this::isClientDeliverable)
                .toList();

        long totalDeliverables = deliverables.size();
        long completed = deliverables.stream()
                .filter(item -> item.getStatus() == WorkItemStatus.DONE)
                .count();

        long inProgress = deliverables.stream()
                .filter(item -> item.getStatus() == WorkItemStatus.IN_PROGRESS 
                        || item.getStatus() == WorkItemStatus.IN_REVIEW)
                .count();

        double percentage = totalDeliverables == 0 ? 0.0 
                : ((double) completed / totalDeliverables) * 100.0;
        double roundedPercentage = Math.round(percentage * 10.0) / 10.0;

        return new ClientWorkProgressSummary(
                projectId, totalDeliverables, completed, inProgress, roundedPercentage
        );
    }

    // Helper: avoids double-counting hierarchy in client delivery progress
    private boolean isClientDeliverable(WorkItem item) {
        if (item.getType() == WorkItemType.STORY) {
            return true;
        }
        // Standalone tasks (not a subtask of another story/task)
        return item.getType() == WorkItemType.TASK && item.getParentId() == null;
    }

    private boolean isTodoOrReported(WorkItem item) {
        WorkItemStatus s = item.getStatus();
        return s == WorkItemStatus.TODO || s == WorkItemStatus.REPORTED 
                || s == WorkItemStatus.TRIAGE || s == WorkItemStatus.REOPENED;
    }

    private boolean isInProgress(WorkItem item) {
        WorkItemStatus s = item.getStatus();
        return s == WorkItemStatus.IN_PROGRESS || s == WorkItemStatus.ASSIGNED;
    }

    private boolean isInReviewOrVerifying(WorkItem item) {
        WorkItemStatus s = item.getStatus();
        return s == WorkItemStatus.IN_REVIEW || s == WorkItemStatus.READY_FOR_VERIFICATION;
    }

    private boolean isDoneOrClosed(WorkItem item) {
        WorkItemStatus s = item.getStatus();
        return s == WorkItemStatus.DONE || s == WorkItemStatus.CLOSED;
    }

    private long countType(List<WorkItem> items, WorkItemType type) {
        return items.stream()
                .filter(item -> item.getType() == type)
                .count();
    }
}