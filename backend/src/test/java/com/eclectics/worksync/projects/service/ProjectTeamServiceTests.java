package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.service.UserDirectoryService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTeamServiceTests {
    @ParameterizedTest @CsvSource({"2147483647,100", "21474837,100", "-1,20", "0,0", "0,101"})
    void invalidPaginationFailsBeforeAnyRepositoryOrDirectoryAccess(int page,int size) {
        var projects=mock(ProjectRepository.class);var members=mock(ProjectMemberRepository.class);
        var access=mock(ProjectAccessService.class);var directory=mock(UserDirectoryService.class);
        var service=new ProjectTeamService(projects,members,access,directory,mock(Validator.class),mock(EntityManager.class));
        var project=UUID.randomUUID();var actor=UUID.randomUUID();
        assertThatThrownBy(() -> service.eligibleUsers(project,actor,"",page,size)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listMembers(project,actor,page,size)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(projects,members,access,directory);
    }
}
