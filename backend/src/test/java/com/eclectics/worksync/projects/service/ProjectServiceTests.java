package com.eclectics.worksync.projects.service;
import com.eclectics.worksync.projects.dto.CreateProjectRequest;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.service.UserService;
import jakarta.validation.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectServiceTests {
    @ParameterizedTest @EnumSource(SystemRole.class)
    void refusesCreationForAllSystemRolesUntilRealPermissionGuardExists(SystemRole role) {
        var projects = mock(ProjectRepository.class); var members = mock(ProjectMemberRepository.class);
        var users = mock(UserService.class); UUID id = UUID.randomUUID();
        when(users.getUserById(id)).thenReturn(new UserResponse(id,"A","B","a@example.com","+254712345678",role,AccountStatus.ACTIVE,Instant.now(),Instant.now()));
        try (var f = Validation.buildDefaultValidatorFactory()) {
            var service = new ProjectService(projects,members,users,f.getValidator());
            assertThatThrownBy(() -> service.createProject(id,new CreateProjectRequest("Project",null,null,null,null,null,null,null)))
                    .isInstanceOf(ProjectCreationUnavailableException.class);
            verifyNoInteractions(projects,members);
        }
    }
    @Test void validatesJavaCallersBeforeCreatingAnything() {
        var projects = mock(ProjectRepository.class); var members = mock(ProjectMemberRepository.class);var users=mock(UserService.class);
        try(var f=Validation.buildDefaultValidatorFactory()) {
            var service=new ProjectService(projects,members,users,f.getValidator());
            assertThatThrownBy(() -> service.createProject(UUID.randomUUID(),new CreateProjectRequest(" ",null,null,null,null,null,null,null)))
                    .isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(projects,members,users);
        }
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names="ACTIVE",mode=EnumSource.Mode.EXCLUDE)
    void inactiveAccountCannotCreate(AccountStatus status) {
        var projects=mock(ProjectRepository.class);var members=mock(ProjectMemberRepository.class);var users=mock(UserService.class);
        UUID id=UUID.randomUUID();
        when(users.getUserById(id)).thenReturn(new UserResponse(id,"A","B","a@example.com","+254712345678",SystemRole.USER,status,Instant.now(),Instant.now()));
        try(var f=Validation.buildDefaultValidatorFactory()) {
            var service=new ProjectService(projects,members,users,f.getValidator());
            assertThatThrownBy(() -> service.createProject(id,new CreateProjectRequest("Project",null,null,null,null,null,null,null)))
                    .isInstanceOf(ProjectAccessDeniedException.class);
            verifyNoInteractions(projects,members);
        }
    }

}
