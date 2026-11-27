package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.dto.CreateProjectRequest;
import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.entity.OrganizationPermission;
import com.eclectics.worksync.users.exception.OrganizationPermissionDeniedException;
import com.eclectics.worksync.users.service.UserService;
import jakarta.validation.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectServiceTests {
    @Test void permissionGuardRunsBeforeProjectPersistence() {
        var projects = mock(ProjectRepository.class); var members = mock(ProjectMemberRepository.class);
        var users = mock(UserService.class); UUID id = UUID.randomUUID();
        doThrow(new OrganizationPermissionDeniedException()).when(users)
                .requirePermissionForWrite(id, OrganizationPermission.PROJECT_CREATE);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new ProjectService(projects, members, users, factory.getValidator());
            assertThatThrownBy(() -> service.createProject(id,
                    new CreateProjectRequest("Project", null, null, null, null, null, null, null)))
                    .isInstanceOf(OrganizationPermissionDeniedException.class);
            verify(users).requirePermissionForWrite(id, OrganizationPermission.PROJECT_CREATE);
            verifyNoInteractions(projects, members);
        }
    }

    @Test void validatesJavaCallersBeforeCreatingAnything() {
        var projects = mock(ProjectRepository.class); var members = mock(ProjectMemberRepository.class);
        var users = mock(UserService.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new ProjectService(projects, members, users, factory.getValidator());
            assertThatThrownBy(() -> service.createProject(UUID.randomUUID(),
                    new CreateProjectRequest(" ", null, null, null, null, null, null, null)))
                    .isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(projects, members, users);
        }
    }
}
