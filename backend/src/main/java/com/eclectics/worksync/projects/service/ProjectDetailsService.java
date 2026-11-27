package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.dto.UpdateProjectRequest.Field;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.repository.ProjectRepository;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.service.UserDirectoryService;
import jakarta.persistence.EntityManager;
import jakarta.validation.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(readOnly=true)
public class ProjectDetailsService {
    private final ProjectRepository projects;
    private final ProjectAccessService access;
    private final UserDirectoryService users;
    private final EntityManager entityManager;
    private final Validator validator;
    public ProjectDetailsService(ProjectRepository projects,ProjectAccessService access,UserDirectoryService users,
            EntityManager entityManager,Validator validator) {
        this.projects=projects;this.access=access;this.users=users;this.entityManager=entityManager;this.validator=validator;
    }
    public Page<ProjectSummaryResponse> list(UUID actor,ProjectStatus status,int page,int size) {
        if (page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid pagination");
        if (users.findActive(actor).isEmpty()) throw new ProjectAccessDeniedException();
        return projects.findAccessible(actor,status,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","id")));
    }
    public Object get(UUID projectId,UUID actor) {
        var member=participant(projectId,actor);
        var project=projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        if(member.role()==ProjectRole.VIEWER) return ProjectSummaryResponse.from(project,member);
        return ProjectResponse.from(project,ProjectPermissionsResponse.from(member));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ProjectResponse update(UUID projectId,UUID actor,UpdateProjectRequest request) {
        Objects.requireNonNull(request,"request is required");
        var violations=validator.validate(request);
        if(!violations.isEmpty()) throw new ConstraintViolationException(violations);
        // Reuse the existing ACTIVE-actor lifecycle guard, before any Projects row lock.
        try { users.lockActiveActorForMembershipWrite(actor); }
        catch(UserNotFoundException missing) { throw new ProjectAccessDeniedException(); }
        var project=projects.lockById(projectId).orElseThrow(ProjectNotFoundException::new);
        entityManager.refresh(project);
        var member=participant(projectId,actor);
        if(member.role()!=ProjectRole.MANAGER) throw new ProjectAccessDeniedException();
        if(project.getStatus()==ProjectStatus.ARCHIVED) throw new ProjectArchivedException();
        project.updateDetails(request.has(Field.NAME)?request.name():project.getName(),
                request.has(Field.DESCRIPTION)?request.description():project.getDescription(),
                request.has(Field.REQUIREMENTS)?request.requirements():project.getRequirements(),
                request.has(Field.SCOPE)?request.scope():project.getScope(),
                request.has(Field.DELIVERABLES)?request.deliverables():project.getDeliverables(),
                request.has(Field.SUCCESS_CRITERIA)?request.successCriteria():project.getSuccessCriteria(),
                request.has(Field.START_DATE)?request.startDate():project.getStartDate(),
                request.has(Field.DUE_DATE)?request.dueDate():project.getDueDate());
        return ProjectResponse.from(projects.saveAndFlush(project),ProjectPermissionsResponse.from(member));
    }
    private ProjectMemberAccess participant(UUID projectId,UUID actor) {
        try { return access.getMemberAccess(projectId,actor); }
        catch(ProjectAccessDeniedException hidden) { throw new ProjectNotFoundException(); }
    }
}
