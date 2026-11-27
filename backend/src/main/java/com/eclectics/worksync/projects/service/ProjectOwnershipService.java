package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.ProjectRole;
import com.eclectics.worksync.projects.entity.ProjectStatus;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.service.UserDirectoryService;
import jakarta.persistence.EntityManager;
import jakarta.validation.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ProjectOwnershipService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectAccessService access;
    private final UserDirectoryService users;
    private final EntityManager entityManager;
    private final Validator validator;
    public ProjectOwnershipService(ProjectRepository projects,ProjectMemberRepository members,ProjectAccessService access,
            UserDirectoryService users,EntityManager entityManager,Validator validator) {
        this.projects=projects;this.members=members;this.access=access;this.users=users;this.entityManager=entityManager;this.validator=validator;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ProjectResponse transfer(UUID projectId,UUID actorId,TransferOwnershipRequest request) {
        Objects.requireNonNull(request,"Request required");
        var violations=validator.validate(request);
        if(!violations.isEmpty())throw new ConstraintViolationException(violations);
        // Same ordering as creation, updates and membership additions. Keep all locks through outer commit.
        try { users.lockActiveActorForMembershipWrite(actorId); }
        catch(UserNotFoundException inactive) { throw new ProjectAccessDeniedException(); }
        var project=projects.lockById(projectId).orElseThrow(ProjectNotFoundException::new);
        entityManager.refresh(project);
        ProjectMemberAccess actor;
        try { actor=access.getMemberAccess(projectId,actorId); }
        catch(ProjectAccessDeniedException hidden) { throw new ProjectNotFoundException(); }
        if(!actor.isOwner())throw new ProjectAccessDeniedException();
        if(project.getStatus()==ProjectStatus.ARCHIVED)throw new ProjectArchivedException();
        UUID targetId=request.newOwnerUserId();
        if(targetId.equals(actorId))throw new ProjectOwnershipConflictException();
        // Deterministic membership-row order after the Project lock; neither membership is changed.
        var locked=members.lockParticipants(projectId,List.of(actorId,targetId));
        locked.forEach(entityManager::refresh);
        if(locked.size()!=2 || locked.stream().anyMatch(m -> m.getRole()!=ProjectRole.MANAGER))throw new ProjectOwnershipConflictException();
        if(users.findActive(targetId).isEmpty())throw new ProjectOwnershipConflictException();
        project.transferOwnership(targetId);
        var saved=projects.saveAndFlush(project);
        // The response belongs to the acting previous Owner, now an additional MANAGER.
        return ProjectResponse.from(saved,ProjectPermissionsResponse.from(access.getMemberAccess(projectId,actorId)));
    }
}
