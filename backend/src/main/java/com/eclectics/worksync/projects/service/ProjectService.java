package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.entity.AccountStatus;
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
    /** No HTTP endpoint. Fail closed until V7 adds a database-backed Users permission guard.
     * When connected, that guard must participate in this transaction and serialize against revocation.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectResponse createProject(UUID actorUserId, CreateProjectRequest request) {
        Objects.requireNonNull(actorUserId, "actorUserId is required");
        Objects.requireNonNull(request, "request is required");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if (users.getUserById(actorUserId).status() != AccountStatus.ACTIVE) throw new ProjectAccessDeniedException();
        requireProjectCreatePermission();
        Project project = projects.saveAndFlush(new Project(actorUserId, request.name(), request.description(),
                request.requirements(), request.scope(), request.deliverables(), request.successCriteria(),
                request.startDate(), request.dueDate()));
        members.saveAndFlush(new ProjectMember(project.getId(), actorUserId, ProjectRole.MANAGER, null));
        return ProjectResponse.from(project);
    }
    private void requireProjectCreatePermission() {
        // Deliberate hard stop, not a fake grant, JWT check, test override or ADMIN bypass.
        throw new ProjectCreationUnavailableException();
    }
}
