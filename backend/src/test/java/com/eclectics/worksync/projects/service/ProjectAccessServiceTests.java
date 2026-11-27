package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.service.UserService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTests {
    @Mock ProjectRepository projects;
    @Mock ProjectMemberRepository members;
    @Mock UserService users;
    DefaultProjectAccessService service;
    UUID projectId = UUID.randomUUID(), actor = UUID.randomUUID(), owner = UUID.randomUUID();
    Project project;
    @BeforeEach void setup() {
        service = new DefaultProjectAccessService(projects, members, users);
        project = new Project(owner, "Project", null, null, null, null, null, null, null);
    }
    private UserResponse user(AccountStatus status) {
        return new UserResponse(actor, "Test", "User", "test@example.com", "+254712345678",
                SystemRole.ADMIN, status, Instant.now(), Instant.now());
    }
    private void setupMember(ProjectRole role, ProjectResponsibility responsibility) {
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(users.getUserById(actor)).thenReturn(user(AccountStatus.ACTIVE));
        when(members.findByProjectIdAndUserId(projectId, actor)).thenReturn(Optional.of(new ProjectMember(projectId, actor, role, responsibility)));
    }
    @ParameterizedTest @EnumSource(ProjectAction.class)
    void enforcesCapabilityMatrixForEveryRole(ProjectAction action) {
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(users.getUserById(actor)).thenReturn(user(AccountStatus.ACTIVE));
        for (ProjectRole role : ProjectRole.values()) {
            var responsibilities = role == ProjectRole.MEMBER ? Arrays.asList(ProjectResponsibility.values()) : Collections.<ProjectResponsibility>singletonList(null);
            for (var responsibility : responsibilities) {
                when(members.findByProjectIdAndUserId(projectId, actor)).thenReturn(Optional.of(new ProjectMember(projectId, actor, role, responsibility)));
                Set<ProjectAction> expected;
                if (role == ProjectRole.MANAGER || responsibility == ProjectResponsibility.TECHNICAL_LEAD) {
                    expected = EnumSet.of(ProjectAction.VIEW_DELIVERY,ProjectAction.VIEW_INTERNAL_WORK,
                            ProjectAction.CREATE_EPIC,ProjectAction.CREATE_STORY,ProjectAction.CREATE_TASK,
                            ProjectAction.UPDATE_ASSIGNED_WORK,ProjectAction.ASSIGN_WORK,ProjectAction.REPORT_BUG,
                            ProjectAction.TRIAGE_BUG,ProjectAction.DESIGNATE_BUG_VERIFIER);
                } else if (role == ProjectRole.VIEWER) {
                    expected = Set.of(ProjectAction.VIEW_DELIVERY);
                } else if (responsibility == ProjectResponsibility.DEVELOPER) {
                    expected = Set.of(ProjectAction.VIEW_DELIVERY, ProjectAction.VIEW_INTERNAL_WORK,
                            ProjectAction.CREATE_TASK, ProjectAction.UPDATE_ASSIGNED_WORK, ProjectAction.REPORT_BUG);
                } else {
                    expected = Set.of(ProjectAction.VIEW_DELIVERY, ProjectAction.VIEW_INTERNAL_WORK, ProjectAction.REPORT_BUG);
                }
                expected=EnumSet.copyOf(expected);
                expected.add(ProjectAction.VIEW_PROJECT);
                if(role!=ProjectRole.VIEWER) expected.addAll(Set.of(ProjectAction.VIEW_PROJECT_TEAM,ProjectAction.VIEW_PROJECT_CONFIGURATION));
                if(role==ProjectRole.MANAGER) expected.addAll(Set.of(ProjectAction.UPDATE_PROJECT_DETAILS,
                        ProjectAction.SELECT_ELIGIBLE_USERS,ProjectAction.ADD_PROJECT_MEMBER,ProjectAction.UPDATE_PROJECT_CONFIGURATION));
                boolean allowed = expected.contains(action);
                if (allowed) assertThatCode(() -> service.requireAction(projectId, actor, action)).doesNotThrowAnyException();
                else assertThatThrownBy(() -> service.requireAction(projectId, actor, action)).isInstanceOf(ProjectAccessDeniedException.class);
            }
        }
    }
    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void rejectsInactiveAccounts(AccountStatus status) {
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(users.getUserById(actor)).thenReturn(user(status));
        assertThatThrownBy(() -> service.getMemberAccess(projectId, actor)).isInstanceOf(ProjectAccessDeniedException.class);
        verifyNoInteractions(members);
    }
    @Test void systemAdminDoesNotBypassMissingProjectMembership() {
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(users.getUserById(actor)).thenReturn(user(AccountStatus.ACTIVE));
        assertThatThrownBy(() -> service.getMemberAccess(projectId, actor)).isInstanceOf(ProjectAccessDeniedException.class);
    }
    @Test void missingProjectAndDeletedAccountFailClosed() {
        assertThatThrownBy(() -> service.getMemberAccess(projectId, actor)).isInstanceOf(ProjectNotFoundException.class);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(users.getUserById(actor)).thenThrow(new UserNotFoundException());
        assertThatThrownBy(() -> service.getMemberAccess(projectId, actor)).isInstanceOf(ProjectAccessDeniedException.class);
    }
    @ParameterizedTest @EnumSource(ProjectAction.class)
    void archivedProjectsAllowOnlyReadCapabilities(ProjectAction action) {
        setupMember(ProjectRole.MANAGER, null);
        ReflectionTestUtils.setField(project, "status", ProjectStatus.ARCHIVED);
        if (action == ProjectAction.VIEW_DELIVERY || action == ProjectAction.VIEW_INTERNAL_WORK
                || action == ProjectAction.VIEW_PROJECT || action == ProjectAction.VIEW_PROJECT_TEAM || action == ProjectAction.VIEW_PROJECT_CONFIGURATION)
            assertThatCode(() -> service.requireAction(projectId, actor, action)).doesNotThrowAnyException();
        else if(action==ProjectAction.ADD_PROJECT_MANAGER || action==ProjectAction.TRANSFER_PROJECT_OWNERSHIP)
            assertThatThrownBy(() -> service.requireAction(projectId, actor, action)).isInstanceOf(ProjectAccessDeniedException.class);
        else assertThatThrownBy(() -> service.requireAction(projectId, actor, action)).isInstanceOf(ProjectArchivedException.class);
    }
    @Test void onlyOwnerMayAppointAnotherManager() {
        setupMember(ProjectRole.MANAGER,null);
        assertThatThrownBy(() -> service.requireAction(projectId,actor,ProjectAction.ADD_PROJECT_MANAGER)).isInstanceOf(ProjectAccessDeniedException.class);
        ReflectionTestUtils.setField(project,"ownerUserId",actor);
        assertThatCode(() -> service.requireAction(projectId,actor,ProjectAction.ADD_PROJECT_MANAGER)).doesNotThrowAnyException();
    }
    @Test void onlyOwnerMayTransferOwnership() {
        setupMember(ProjectRole.MANAGER,null);
        assertThatThrownBy(() -> service.requireAction(projectId,actor,ProjectAction.TRANSFER_PROJECT_OWNERSHIP)).isInstanceOf(ProjectAccessDeniedException.class);
        ReflectionTestUtils.setField(project,"ownerUserId",actor);
        assertThatCode(() -> service.requireAction(projectId,actor,ProjectAction.TRANSFER_PROJECT_OWNERSHIP)).doesNotThrowAnyException();
    }
    @Test void writableGuardRejectsArchivedProject() {
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        ReflectionTestUtils.setField(project, "status", ProjectStatus.ARCHIVED);
        assertThatThrownBy(() -> service.requireWritable(projectId)).isInstanceOf(ProjectArchivedException.class);
    }
    @Test void viewerCannotBeAssigneeOrVerifierAndSelfVerificationIsRejected() {
        setupMember(ProjectRole.VIEWER, null);
        assertThatThrownBy(() -> service.requireAssignableMember(projectId, actor)).isInstanceOf(InvalidProjectMembershipException.class);
        assertThatThrownBy(() -> service.requireEligibleVerifier(projectId, owner, actor)).isInstanceOf(InvalidProjectMembershipException.class);
        assertThatThrownBy(() -> service.requireEligibleVerifier(projectId, actor, actor)).isInstanceOf(InvalidProjectMembershipException.class);
    }
    @ParameterizedTest @EnumSource(ProjectResponsibility.class)
    void independentActiveMemberIsEligible(ProjectResponsibility responsibility) {
        setupMember(ProjectRole.MEMBER, responsibility);
        assertThatCode(() -> service.requireAssignableMember(projectId, actor)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireEligibleVerifier(projectId, owner, actor)).doesNotThrowAnyException();
        assertThat(service.getMemberAccess(projectId, actor).responsibility()).isEqualTo(responsibility);
    }
    @Test void ownerSnapshotRequiresManagerMembership() {
        owner = actor;
        project = new Project(actor, "Project", null, null, null, null, null, null, null);
        setupMember(ProjectRole.MANAGER, null);
        assertThat(service.getMemberAccess(projectId, actor).isOwner()).isTrue();
    }
}
