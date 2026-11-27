package com.eclectics.worksync.users.service;
import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.exception.*;
import com.eclectics.worksync.users.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class UserPermissionServiceTests {
    @Mock UserRepository accounts;
    @Mock UserPermissionRepository grants;
    @Mock EntityManager em;
    ValidatorFactory factory; UserService service;
    UUID actor=UUID.randomUUID(),target=UUID.randomUUID();
    @BeforeEach void setup() {factory=Validation.buildDefaultValidatorFactory();service=new UserService(accounts,factory.getValidator(),em,grants);}
    @AfterEach void close() {factory.close();}
    User user(UUID id, SystemRole role, AccountStatus status) {
        User u=new User("A","B","test@example.com","+254712345678","fixture");
        ReflectionTestUtils.setField(u,"id",id);ReflectionTestUtils.setField(u,"role",role);ReflectionTestUtils.setField(u,"status",status);return u;
    }
    void administrator() {when(accounts.findById(actor)).thenReturn(Optional.of(user(actor,SystemRole.ADMIN,AccountStatus.ACTIVE)));}
    @Test void rejectsDuplicateAndNullEntriesBeforeMutation() {
        assertThatThrownBy(() -> service.updatePermissions(actor,target,new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE,OrganizationPermission.PROJECT_CREATE))))
                .isInstanceOf(InvalidPermissionRequestException.class);
        assertThatThrownBy(() -> service.updatePermissions(actor,target,new UpdatePermissionsRequest(Arrays.asList((OrganizationPermission)null))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.updatePermissions(actor,target,new UpdatePermissionsRequest(null))).isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(accounts,grants,em);
    }
    @Test void ordinaryUserCannotGrant() {
        when(accounts.findById(actor)).thenReturn(Optional.of(user(actor,SystemRole.USER,AccountStatus.ACTIVE)));
        assertThatThrownBy(() -> service.updatePermissions(actor,target,new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE))))
                .isInstanceOf(AdministratorAccessDeniedException.class);
        verifyNoInteractions(grants);
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names="ACTIVE",mode=EnumSource.Mode.EXCLUDE)
    void inactiveTargetCannotReceiveNewGrant(AccountStatus status) {
        administrator();when(accounts.findById(target)).thenReturn(Optional.of(user(target,SystemRole.USER,status)));
        assertThatThrownBy(() -> service.updatePermissions(actor,target,new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE))))
                .isInstanceOf(InvalidPermissionTargetException.class);
        verify(grants,never()).save(any());verify(grants,never()).deleteGrant(any(),any());
    }
    @Test void identicalReplacementDoesNotRewriteGrantMetadata() {
        administrator();when(accounts.findById(target)).thenReturn(Optional.of(user(target,SystemRole.USER,AccountStatus.ACTIVE)));
        when(grants.findPermissionsByUserId(target)).thenReturn(Set.of(OrganizationPermission.PROJECT_CREATE));
        assertThat(service.updatePermissions(actor,target,new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE))).permissions())
                .containsExactly(OrganizationPermission.PROJECT_CREATE);
        verify(grants,never()).save(any());verify(grants,never()).deleteGrant(any(),any());
    }
    @Test void inactiveTargetCanHaveExistingGrantsRevoked() {
        administrator();when(accounts.findById(target)).thenReturn(Optional.of(user(target,SystemRole.USER,AccountStatus.BLOCKED)));
        when(grants.findPermissionsByUserId(target)).thenReturn(Set.of(OrganizationPermission.PROJECT_CREATE));
        service.updatePermissions(actor,target,new UpdatePermissionsRequest(List.of()));
        verify(grants).deleteGrant(target,OrganizationPermission.PROJECT_CREATE);
    }
}
