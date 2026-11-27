package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.service.ProjectOwnershipService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/projects/{projectId}/ownership-transfer")
@SecurityRequirement(name="bearerAuth")
public class ProjectOwnershipController {
    private final ProjectOwnershipService ownership;
    public ProjectOwnershipController(ProjectOwnershipService ownership) {this.ownership=ownership;}
    @PostMapping
    public ProjectResponse transfer(@PathVariable UUID projectId,@AuthenticationPrincipal WorkSyncPrincipal principal,
            @Valid @RequestBody TransferOwnershipRequest request) {
        if(principal==null||principal.userId()==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ownership.transfer(projectId,principal.userId(),request);
    }
}
