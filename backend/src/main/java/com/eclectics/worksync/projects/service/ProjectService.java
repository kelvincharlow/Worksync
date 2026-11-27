package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.entity.OrganizationPermission;
import com.eclectics.worksync.users.service.UserService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserService users;
    private final Validator validator;
    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, UserService users, Validator validator) {
        this.projects = projects; this.members = members; this.users = users; this.validator = validator;
    }
    /** The Users guard joins this writable transaction and holds its lifecycle lock until commit.
     * Always acquire it before inserting/locking Projects rows, so revocation is serialized.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectResponse createProject(UUID actorUserId, CreateProjectRequest request) {
        Objects.requireNonNull(actorUserId, "actorUserId is required");
        Objects.requireNonNull(request, "request is required");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        users.requirePermissionForWrite(actorUserId, OrganizationPermission.PROJECT_CREATE);
        Project project = projects.saveAndFlush(new Project(actorUserId, request.name(), request.description(),
                request.requirements(), request.scope(), request.deliverables(), request.successCriteria(),
                request.startDate(), request.dueDate()));
        members.saveAndFlush(new ProjectMember(project.getId(), actorUserId, ProjectRole.MANAGER, null));
        return ProjectResponse.from(project, ProjectPermissionsResponse.initialOwner(project));
    }
}
