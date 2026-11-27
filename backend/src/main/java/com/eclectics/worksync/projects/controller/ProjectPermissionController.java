package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.service.ProjectPermissionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/projects/{projectId}/permissions/me")
@SecurityRequirement(name="bearerAuth")
public class ProjectPermissionController {
    private final ProjectPermissionService permissions;
    public ProjectPermissionController(ProjectPermissionService permissions) {this.permissions=permissions;}
    @GetMapping
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(oneOf={ProjectPermissionsResponse.class,ViewerProjectPermissionsResponse.class})))
    public Object get(@PathVariable UUID projectId,@AuthenticationPrincipal WorkSyncPrincipal principal) {
        if(principal==null || principal.userId()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return permissions.get(projectId,principal.userId());
    }
}
