package com.eclectics.worksync.workitems.controller;

import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import com.eclectics.worksync.workitems.dto.CreateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.UpdateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.WorkItemResponse;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.service.WorkItemService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/work-items")
@SecurityRequirement(name = "bearerAuth")
public class WorkItemController {

    private final WorkItemService workItemService;

    public WorkItemController(WorkItemService workItemService) {
        this.workItemService = workItemService;
    }

    @PostMapping
    public ResponseEntity<WorkItemResponse> createWorkItem(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal WorkSyncPrincipal principal,
            @Valid @RequestBody CreateWorkItemRequest request) {

        UUID actorId = requireAuthenticatedUserId(principal);
        WorkItemResponse response = workItemService.createWorkItem(
                projectId, actorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{workItemId}")
    public ResponseEntity<WorkItemResponse> getWorkItem(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId) {

        WorkItemResponse response = workItemService.getWorkItem(
                projectId, workItemId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkItemResponse>> getWorkItems(
            @PathVariable UUID projectId,
            @RequestParam(required = false) WorkItemStatus status,
            @RequestParam(required = false) WorkItemType type) {

        List<WorkItemResponse> response = workItemService.getWorkItems(
                projectId, status, type);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{workItemId}")
    public ResponseEntity<WorkItemResponse> updateWorkItem(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId,
            @AuthenticationPrincipal WorkSyncPrincipal principal,
            @Valid @RequestBody UpdateWorkItemRequest request) {

        UUID actorId = requireAuthenticatedUserId(principal);
        WorkItemResponse response = workItemService.updateWorkItem(
                projectId, workItemId, actorId, request);
        return ResponseEntity.ok(response);
    }

    private UUID requireAuthenticatedUserId(WorkSyncPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }
}