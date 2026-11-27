package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.service.ProjectTeamService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@SecurityRequirement(name="bearerAuth")
public class ProjectTeamController {
    private final ProjectTeamService teams;
    public ProjectTeamController(ProjectTeamService teams) { this.teams=teams; }
    @GetMapping("/eligible-users")
    public EligibleUserPageResponse eligible(@PathVariable UUID projectId, @AuthenticationPrincipal WorkSyncPrincipal principal,
            @RequestParam(required=false) @Size(max=100) String search,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return EligibleUserPageResponse.from(teams.eligibleUsers(projectId,actor(principal),search,page,size));
    }
    @PostMapping("/members") @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse add(@PathVariable UUID projectId, @AuthenticationPrincipal WorkSyncPrincipal principal,
            @Valid @RequestBody AddProjectMemberRequest request) {
        return teams.addMember(projectId,actor(principal),request);
    }
    @GetMapping("/members")
    public ProjectMemberPageResponse list(@PathVariable UUID projectId, @AuthenticationPrincipal WorkSyncPrincipal principal,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ProjectMemberPageResponse.from(teams.listMembers(projectId,actor(principal),page,size));
    }
    private UUID actor(WorkSyncPrincipal principal) {
        if (principal==null || principal.userId()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return principal.userId();
    }
}
