package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import com.eclectics.worksync.projects.dto.CreateProjectRequest;
import com.eclectics.worksync.projects.dto.ProjectResponse;
import com.eclectics.worksync.projects.service.ProjectService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/projects")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {
    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal WorkSyncPrincipal principal,
            @Valid @RequestBody CreateProjectRequest request) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return projects.createProject(principal.userId(), request);
    }
}
