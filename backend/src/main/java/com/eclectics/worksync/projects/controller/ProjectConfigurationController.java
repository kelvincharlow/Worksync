package com.eclectics.worksync.projects.controller;
import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.service.ProjectConfigurationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/projects/{projectId}/configuration")
@SecurityRequirement(name="bearerAuth")
public class ProjectConfigurationController {
    private final ProjectConfigurationService service;
    public ProjectConfigurationController(ProjectConfigurationService service){this.service=service;}
    @GetMapping public ProjectConfigurationResponse get(@PathVariable UUID projectId,@AuthenticationPrincipal WorkSyncPrincipal actor){
        return service.get(projectId,actor.userId());
    }
    @PatchMapping public ProjectConfigurationResponse update(@PathVariable UUID projectId,@AuthenticationPrincipal WorkSyncPrincipal actor,
            @RequestBody UpdateProjectConfigurationRequest request){return service.update(projectId,actor.userId(),request);}
}
