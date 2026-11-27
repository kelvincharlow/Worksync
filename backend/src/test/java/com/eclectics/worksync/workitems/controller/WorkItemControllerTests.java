package com.eclectics.worksync.workitems.controller;

import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import com.eclectics.worksync.workitems.dto.CreateWorkItemRequest;
import com.eclectics.worksync.workitems.dto.WorkItemResponse;
import com.eclectics.worksync.workitems.entity.WorkItemPriority;
import com.eclectics.worksync.workitems.entity.WorkItemStatus;
import com.eclectics.worksync.workitems.entity.WorkItemType;
import com.eclectics.worksync.workitems.exception.WorkItemNotFoundException;
import com.eclectics.worksync.workitems.service.WorkItemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkItemControllerTests {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WorkItemService workItemService;

    @InjectMocks
    private WorkItemController workItemController;

    private final UUID actorId = UUID.randomUUID();
    private Authentication auth;

    @BeforeEach
    void setUp() {
        WorkSyncPrincipal principal = new WorkSyncPrincipal(actorId, "user@example.com");
        auth = new UsernamePasswordAuthenticationToken(principal, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc = MockMvcBuilders
                .standaloneSetup(workItemController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new WorkItemExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST returns 201 Created when authenticated and request is valid")
    void createWorkItem_validRequest_returns201() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();

        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.STORY,
                "Valid Title",
                "Description",
                WorkItemPriority.HIGH,
                null, null, null
        );

        WorkItemResponse mockResponse = new WorkItemResponse(
                workItemId, projectId, WorkItemType.STORY,
                "Valid Title", "Description",
                WorkItemStatus.TODO, WorkItemPriority.HIGH,
                actorId, null, null,
                Instant.now(), Instant.now()
        );

        when(workItemService.createWorkItem(eq(projectId), eq(actorId), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(workItemId.toString()))
                .andExpect(jsonPath("$.title").value("Valid Title"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    @DisplayName("POST returns 401 Unauthorized when caller is unauthenticated")
    void createWorkItem_unauthenticated_returns401() throws Exception {
        // Clear security context to simulate an unauthenticated request
        SecurityContextHolder.clearContext();

        UUID projectId = UUID.randomUUID();
        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.TASK, "Some Task", null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST returns 400 Bad Request when title is blank")
    void createWorkItem_blankTitle_returns400() throws Exception {
        UUID projectId = UUID.randomUUID();

        CreateWorkItemRequest request = new CreateWorkItemRequest(
                WorkItemType.TASK,
                "   ",
                "Details",
                WorkItemPriority.LOW,
                null, null, null
        );

        mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORK_ITEM_REQUEST"));
    }

    @Test
    @DisplayName("GET returns 200 OK with work item details")
    void getWorkItem_found_returns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();

        WorkItemResponse mockResponse = new WorkItemResponse(
                workItemId, projectId, WorkItemType.TASK,
                "Task 1", "Desc",
                WorkItemStatus.TODO, WorkItemPriority.MEDIUM,
                actorId, null, null,
                Instant.now(), Instant.now()
        );

        when(workItemService.getWorkItem(projectId, workItemId))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-items/{workItemId}",
                projectId, workItemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workItemId.toString()))
                .andExpect(jsonPath("$.title").value("Task 1"));
    }

    @Test
    @DisplayName("GET returns 404 Not Found when work item does not exist")
    void getWorkItem_notFound_returns404() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();

        when(workItemService.getWorkItem(projectId, nonExistentId))
                .thenThrow(new WorkItemNotFoundException(nonExistentId));

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-items/{workItemId}",
                projectId, nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORK_ITEM_NOT_FOUND"));
    }
}