package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.ProjectStatus;
import com.eclectics.worksync.projects.service.ProjectDetailsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/projects") @SecurityRequirement(name="bearerAuth")
public class ProjectDetailsController {
    private final ProjectDetailsService projects;
    public ProjectDetailsController(ProjectDetailsService projects) {this.projects=projects;}
    @GetMapping
    public ProjectPageResponse list(jakarta.servlet.http.HttpServletRequest request,@AuthenticationPrincipal WorkSyncPrincipal principal,
            @RequestParam(required=false) ProjectStatus status,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        if (!java.util.Set.of("status","page","size").containsAll(request.getParameterMap().keySet())) {
            throw new IllegalArgumentException("Unsupported project filter");
        }
        return ProjectPageResponse.from(projects.list(actor(principal),status,page,size));
    }
    @GetMapping("/{projectId}")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(oneOf={ProjectResponse.class,ProjectSummaryResponse.class})))
    public Object get(@PathVariable UUID projectId,@AuthenticationPrincipal WorkSyncPrincipal principal) {
        return projects.get(projectId,actor(principal));
    }
    @PatchMapping("/{projectId}")
    public ProjectResponse update(@PathVariable UUID projectId,@AuthenticationPrincipal WorkSyncPrincipal principal,
            @Valid @RequestBody UpdateProjectRequest request) {
        return projects.update(projectId,actor(principal),request);
    }
    private UUID actor(WorkSyncPrincipal principal) {
        if(principal==null||principal.userId()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return principal.userId();
    }
}
