package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.ProjectRole;
import com.eclectics.worksync.projects.exception.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only presentation hints, never a substitute for each operation's transactional authorization. */
@Service @Transactional(readOnly=true)
public class ProjectPermissionService {
    private final ProjectAccessService access;
    private final com.eclectics.worksync.projects.repository.ProjectRepository projects;
    public ProjectPermissionService(ProjectAccessService access,com.eclectics.worksync.projects.repository.ProjectRepository projects) {this.access=access;this.projects=projects;}
    public Object get(UUID projectId,UUID actorId) {
        ProjectMemberAccess member;
        try { member=access.getMemberAccess(projectId,actorId); }
        catch(ProjectAccessDeniedException hidden) { throw new ProjectNotFoundException(); }
        // The shared helper loads current ACTIVE state, membership, responsibility, Owner and project status.
        if(member.role()==ProjectRole.VIEWER) return ViewerProjectPermissionsResponse.from(member);
        return ProjectPermissionsResponse.from(member,ProjectConfigurationResponse.from(projects.findById(projectId).orElseThrow(ProjectNotFoundException::new)));
    }
}
