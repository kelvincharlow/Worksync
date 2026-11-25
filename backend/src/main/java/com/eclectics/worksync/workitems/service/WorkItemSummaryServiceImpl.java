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
        long todo = countStatus(items, WorkItemStatus.TODO);
        long inProgress = countStatus(items, WorkItemStatus.IN_PROGRESS);
        long inReview = countStatus(items, WorkItemStatus.IN_REVIEW);
        long done = countStatus(items, WorkItemStatus.DONE);

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

        long total = items.size();
        long completed = countStatus(items, WorkItemStatus.DONE);
        long inProgress = countStatus(items, WorkItemStatus.IN_PROGRESS);

        double percentage = total == 0 ? 0.0 : ((double) completed / total) * 100.0;
        double roundedPercentage = Math.round(percentage * 10.0) / 10.0;

        return new ClientWorkProgressSummary(
                projectId, total, completed, inProgress, roundedPercentage
        );
    }

    private long countStatus(List<WorkItem> items, WorkItemStatus status) {
        return items.stream()
                .filter(item -> item.getStatus() == status)
                .count();
    }

    private long countType(List<WorkItem> items, WorkItemType type) {
        return items.stream()
                .filter(item -> item.getType() == type)
                .count();
    }
}