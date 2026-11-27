package com.eclectics.worksync.users.service;

import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserPermissionPageTests {
    UserRepository accounts = mock(UserRepository.class);
    UserPermissionRepository grants = mock(UserPermissionRepository.class);
    UserService service = new UserService(accounts, mock(Validator.class), mock(EntityManager.class), grants);

    User user(AccountStatus status) {
        User user = new User("Page", "User", UUID.randomUUID()+"@example.com", "+254712345678", "fixture");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
    UserPermissionRepository.PermissionGrantView grant(UUID id) {
        var grant = mock(UserPermissionRepository.PermissionGrantView.class);
        when(grant.getUserId()).thenReturn(id);
        when(grant.getPermission()).thenReturn(OrganizationPermission.PROJECT_CREATE);
        return grant;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void oneBatchForCurrentPagePreservesPermissionsAndPagination(boolean filtered) {
        User first=user(AccountStatus.ACTIVE), second=user(filtered?AccountStatus.ACTIVE:AccountStatus.BLOCKED), third=user(AccountStatus.ACTIVE);
        var request=PageRequest.of(2,3,Sort.by(Sort.Direction.DESC,"createdAt","id"));
        var page=new PageImpl<>(List.of(first,second,third),request,14);
        if(filtered) when(accounts.findByStatus(AccountStatus.ACTIVE,request)).thenReturn(page);
        else when(accounts.findAll(request)).thenReturn(page);
        var ids=List.of(first.getId(),second.getId(),third.getId());
        var rows=List.of(grant(first.getId()),grant(second.getId()));
        when(grants.findPermissionsForUsers(ids)).thenReturn(rows);
        var result=filtered?service.getUsersByStatus(AccountStatus.ACTIVE,request):service.getUsers(request);
        assertThat(result.getContent()).extracting(r->r.id()).containsExactlyElementsOf(ids);
        assertThat(result.getContent().get(0).permissions()).containsExactly(OrganizationPermission.PROJECT_CREATE);
        assertThat(result.getContent().get(0).effectivePermissions()).containsExactly(OrganizationPermission.PROJECT_CREATE);
        assertThat(result.getContent().get(1).permissions()).containsExactly(OrganizationPermission.PROJECT_CREATE);
        if(filtered) assertThat(result.getContent().get(1).effectivePermissions()).containsExactly(OrganizationPermission.PROJECT_CREATE);
        else assertThat(result.getContent().get(1).effectivePermissions()).isEmpty();
        assertThat(result.getContent().get(2).permissions()).isEmpty();
        assertThat(result.getContent().get(2).effectivePermissions()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(2);assertThat(result.getSize()).isEqualTo(3);
        assertThat(result.getTotalElements()).isEqualTo(14);assertThat(result.getSort()).isEqualTo(request.getSort());
        verify(grants,times(1)).findPermissionsForUsers(ids);
        verifyNoMoreInteractions(grants); // In particular, no per-user permission lookups.
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void emptyPageDoesNotQueryPermissions(boolean filtered) {
        var request=PageRequest.of(4,20);
        var page=new PageImpl<User>(List.of(),request,5);
        if(filtered) when(accounts.findByStatus(AccountStatus.ACTIVE,request)).thenReturn(page);
        else when(accounts.findAll(request)).thenReturn(page);
        var result=filtered?service.getUsersByStatus(AccountStatus.ACTIVE,request):service.getUsers(request);
        assertThat(result.getContent()).isEmpty();assertThat(result.getNumber()).isEqualTo(4);
        assertThat(result.getTotalElements()).isEqualTo(5);assertThat(result.getSize()).isEqualTo(20);
        verifyNoInteractions(grants);
    }
    @Test void singleUserLookupStillUsesFreshSingleUserQuery() {
        User user=user(AccountStatus.ACTIVE);
        when(accounts.findById(user.getId())).thenReturn(Optional.of(user));
        when(grants.findPermissionsByUserId(user.getId())).thenReturn(Set.of(OrganizationPermission.PROJECT_CREATE));
        assertThat(service.getUserById(user.getId()).permissions()).containsExactly(OrganizationPermission.PROJECT_CREATE);
        verify(grants).findPermissionsByUserId(user.getId());verifyNoMoreInteractions(grants);
    }
}
