package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.dto.UserSelection;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.service.UserDirectoryService;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class ProjectTeamService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectAccessService access;
    private final UserDirectoryService directory;
    private final Validator validator;
    private final EntityManager entityManager;
    public ProjectTeamService(ProjectRepository projects, ProjectMemberRepository members, ProjectAccessService access,
            UserDirectoryService directory, Validator validator, EntityManager entityManager) {
        this.projects=projects; this.members=members; this.access=access;
        this.directory=directory; this.validator=validator; this.entityManager=entityManager;
    }
    public Page<UserSelection> eligibleUsers(UUID projectId, UUID actorId, String search, int page, int size) {
        var pagination = page(page,size,Sort.unsorted());
        var actor = participant(projectId, actorId);
        requireManager(actor);
        if (actor.projectStatus() == ProjectStatus.ARCHIVED) throw new ProjectArchivedException();
        return directory.searchActiveNonMembers(projectId, search, pagination);
    }
    public Page<ProjectMemberResponse> listMembers(UUID projectId, UUID actorId, int page, int size) {
        var pagination = page(page,size,Sort.by(Sort.Direction.DESC,"createdAt","userId"));
        var actor = participant(projectId,actorId);
        if (actor.role() == ProjectRole.VIEWER) throw new ProjectAccessDeniedException();
        var project = projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        var result = members.findByProjectId(projectId, pagination);
        var names = directory.findSelections(result.map(ProjectMember::getUserId).getContent());
        return result.map(m -> ProjectMemberResponse.from(m, names.get(m.getUserId()).displayName(), project.getOwnerUserId()));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectMemberResponse addMember(UUID projectId, UUID actorId, AddProjectMemberRequest request) {
        Objects.requireNonNull(request,"request is required");
        var violations=validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        // Same Users lifecycle lock and lock order as V7. Never acquire a Projects lock first.
        try { directory.lockActiveActorForMembershipWrite(actorId); }
        catch (UserNotFoundException inactive) { throw new ProjectAccessDeniedException(); }
        var project=projects.lockById(projectId).orElseThrow(ProjectNotFoundException::new);
        entityManager.refresh(project);
        var actor=participant(projectId,actorId);
        requireManager(actor);
        if (project.getStatus()==ProjectStatus.ARCHIVED) throw new ProjectArchivedException();
        if (request.role()==ProjectRole.MANAGER && !actor.isOwner()) throw new ProjectAccessDeniedException();
        // Fresh scalar lookup under lifecycle lock: blocking cannot win between this check and commit.
        var target=directory.findActive(request.userId()).orElseThrow(ProjectMembershipConflictException::new);
        if (members.findByProjectIdAndUserId(projectId,request.userId()).isPresent()) throw new ProjectMembershipConflictException();
        var saved=members.saveAndFlush(new ProjectMember(projectId,request.userId(),request.role(),request.responsibility()));
        return ProjectMemberResponse.from(saved,target.displayName(),project.getOwnerUserId());
    }
    private ProjectMemberAccess participant(UUID projectId, UUID actorId) {
        try { return access.getMemberAccess(projectId,actorId); }
        catch (ProjectAccessDeniedException hidden) { throw new ProjectNotFoundException(); }
    }
    private static void requireManager(ProjectMemberAccess actor) {
        if (actor.role()!=ProjectRole.MANAGER) throw new ProjectAccessDeniedException();
    }
    private static Pageable page(int page,int size,Sort sort) {
        if (page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid page");
        // JPA setFirstResult accepts an int. Cast before multiplication; MAX_VALUE itself is supported.
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("Unsupported page offset");
        return PageRequest.of(page,size,sort);
    }
}
