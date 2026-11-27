package com.eclectics.worksync.workitems.service;

import com.eclectics.worksync.workitems.dto.ClientWorkProgressSummary;
import com.eclectics.worksync.workitems.dto.WorkItemSummaryResponse;

import java.util.UUID;

public interface WorkItemSummaryService {

    /**
     * Returns internal aggregate status and type metrics for managers and team members.
     */
    WorkItemSummaryResponse getProjectSummary(UUID projectId);

    /**
     * Returns sanitized progress metrics suitable for external or client-facing viewers.
     */
    ClientWorkProgressSummary getClientSafeSummary(UUID projectId);
}