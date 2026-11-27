package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.dto.ProjectMemberAccess;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.service.UserService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultProjectAccessService implements ProjectAccessService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserService users;
    public DefaultProjectAccessService(ProjectRepository projects, ProjectMemberRepository members, UserService users) {
        this.projects = projects; this.members = members; this.users = users;
    }
    @Override
    public ProjectMemberAccess getMemberAccess(UUID projectId, UUID actorUserId) {
        Project project = requireProject(projectId);
        requireActive(actorUserId);
        ProjectMember member = members.findByProjectIdAndUserId(projectId, actorUserId)
                .orElseThrow(ProjectAccessDeniedException::new);
        if (!member.isRoleResponsibilityValid()) throw new ProjectAccessDeniedException();
        boolean owner = project.getOwnerUserId().equals(actorUserId);
        if (owner && member.getRole() != ProjectRole.MANAGER) throw new ProjectAccessDeniedException();
        return new ProjectMemberAccess(projectId, actorUserId, member.getRole(), member.getResponsibility(),
                owner, project.getStatus());
    }
    @Override
    public void requireAction(UUID projectId, UUID actorUserId, ProjectAction action) {
        Objects.requireNonNull(action, "action is required");
        ProjectMemberAccess member = getMemberAccess(projectId, actorUserId);
        boolean manager = member.role() == ProjectRole.MANAGER;
        boolean lead = member.responsibility() == ProjectResponsibility.TECHNICAL_LEAD;
        boolean developer = member.responsibility() == ProjectResponsibility.DEVELOPER;
        boolean internal = member.role() != ProjectRole.VIEWER;
        boolean allowed = switch (action) {
            case VIEW_PROJECT,VIEW_PROJECT_TEAM,UPDATE_PROJECT_DETAILS,SELECT_ELIGIBLE_USERS,
                    ADD_PROJECT_MEMBER,ADD_PROJECT_MANAGER -> ProjectCapabilities.roleAllows(member.role(),member.isOwner(),action);
            case VIEW_DELIVERY -> true;
            case VIEW_INTERNAL_WORK, REPORT_BUG -> internal;
            case CREATE_EPIC, CREATE_STORY, ASSIGN_WORK, TRIAGE_BUG, DESIGNATE_BUG_VERIFIER -> manager || lead;
            case CREATE_TASK, UPDATE_ASSIGNED_WORK -> manager || lead || developer;
        };
        if (!allowed) throw new ProjectAccessDeniedException();
        if (action != ProjectAction.VIEW_DELIVERY && action != ProjectAction.VIEW_INTERNAL_WORK
                && !ProjectCapabilities.isRead(action) && member.projectStatus() == ProjectStatus.ARCHIVED) throw new ProjectArchivedException();
    }
    @Override
    public void requireWritable(UUID projectId) {
        if (requireProject(projectId).getStatus() == ProjectStatus.ARCHIVED) throw new ProjectArchivedException();
    }
    @Override
    public void requireAssignableMember(UUID projectId, UUID targetUserId) {
        requireWritable(projectId);
        ProjectMemberAccess member = getMemberAccess(projectId, targetUserId);
        if (member.role() == ProjectRole.VIEWER) throw new InvalidProjectMembershipException();
    }
    @Override
    public void requireEligibleVerifier(UUID projectId, UUID fixerUserId, UUID verifierUserId) {
        Objects.requireNonNull(fixerUserId, "fixerUserId is required");
        if (fixerUserId.equals(verifierUserId)) throw new InvalidProjectMembershipException();
        requireAssignableMember(projectId, verifierUserId);
        // M3 must pass the real attempt's fixer/submitter, and validate designation and state atomically.
    }
    private Project requireProject(UUID projectId) {
        return projects.findById(Objects.requireNonNull(projectId, "projectId is required"))
                .orElseThrow(ProjectNotFoundException::new);
    }
    private void requireActive(UUID userId) {
        Objects.requireNonNull(userId, "userId is required");
        try {
            if (users.getUserById(userId).status() != AccountStatus.ACTIVE) throw new ProjectAccessDeniedException();
        } catch (UserNotFoundException missing) {
            throw new ProjectAccessDeniedException();
        }
    }
}
