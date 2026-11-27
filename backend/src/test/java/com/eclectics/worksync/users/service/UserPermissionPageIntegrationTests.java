package com.eclectics.worksync.users.service;

import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.repository.UserPermissionRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL SQL counts, not just mapper mocks. Disposable TEST_DB only; no SMTP. */
@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN"})
@ActiveProfiles("test")
class UserPermissionPageIntegrationTests {
    @MockitoBean JavaMailSender mailSender;
    @MockitoSpyBean UserPermissionRepository grants;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @BeforeEach @AfterEach void clean() {
        new TransactionTemplate(manager).executeWithoutResult(t -> {
            jdbc.update("DELETE FROM organization_permissions");jdbc.update("DELETE FROM project_members");
            jdbc.update("DELETE FROM projects");jdbc.update("DELETE FROM refresh_tokens");
            jdbc.update("DELETE FROM password_reset_tokens");jdbc.update("DELETE FROM users");
        });
    }
    UserResponse user(SystemRole role) {
        var user=users.createPendingUser(new CreateUserCommand("Page","User",UUID.randomUUID()+"@example.com","+254712345678","fixture"));
        jdbc.update("UPDATE users SET status='ACTIVE',role=? WHERE id=?",role.name(),user.id());
        return users.getUserById(user.id());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void pageUsesOneRestrictedBatchAndConstantSqlCount(boolean filtered) {
        var actor=user(SystemRole.ADMIN);
        var active=user(SystemRole.USER);
        var blocked=user(SystemRole.USER);
        var noGrant=user(SystemRole.USER);
        var outside=user(SystemRole.USER);
        var request=new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE));
        for(var target:List.of(active,blocked,outside)) users.updatePermissions(actor.id(),target.id(),request);
        users.blockUser(blocked.id());
        var pageable=PageRequest.of(0,3,Sort.by(Sort.Direction.ASC,"createdAt","id"));
        var ids=filtered?List.of(actor.id(),active.id(),noGrant.id()):List.of(actor.id(),active.id(),blocked.id());
        var stats=entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled=stats.isStatisticsEnabled();
        try {
            stats.setStatisticsEnabled(true);stats.clear();clearInvocations(grants);
            var page=filtered?users.getUsersByStatus(AccountStatus.ACTIVE,pageable):users.getUsers(pageable);
            assertThat(stats.getPrepareStatementCount()).as("user page + count + one permission batch").isEqualTo(3);
            assertThat(page.getContent()).extracting(UserResponse::id).containsExactlyElementsOf(ids);
            assertThat(page.getTotalElements()).isEqualTo(filtered?4:5);
            assertThat(page.getSize()).isEqualTo(3);assertThat(page.getNumber()).isZero();
            assertThat(page.getSort()).isEqualTo(pageable.getSort());
            assertThat(page.getContent().get(0).permissions()).isEmpty();
            assertThat(page.getContent().get(1).permissions()).containsExactly(OrganizationPermission.PROJECT_CREATE);
            assertThat(page.getContent().get(1).effectivePermissions()).containsExactly(OrganizationPermission.PROJECT_CREATE);
            if(filtered) assertThat(page.getContent().get(2).permissions()).isEmpty();
            else {
                assertThat(page.getContent().get(2).permissions()).containsExactly(OrganizationPermission.PROJECT_CREATE);
                assertThat(page.getContent().get(2).effectivePermissions()).isEmpty();
            }
            verify(grants,times(1)).findPermissionsForUsers(ids);
            verifyNoMoreInteractions(grants);
        } finally {stats.clear();stats.setStatisticsEnabled(enabled);}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void emptyPageSkipsGrantQueryAndRetainsTotal(boolean filtered) {
        user(SystemRole.USER);
        var pageable=PageRequest.of(2,3);
        var stats=entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled=stats.isStatisticsEnabled();
        try {
            stats.setStatisticsEnabled(true);stats.clear();clearInvocations(grants);
            var page=filtered?users.getUsersByStatus(AccountStatus.ACTIVE,pageable):users.getUsers(pageable);
            assertThat(page.getContent()).isEmpty();assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getNumber()).isEqualTo(2);
            assertThat(stats.getPrepareStatementCount()).as("page + count, no permission SQL").isEqualTo(2);
            verifyNoInteractions(grants);
        } finally {stats.clear();stats.setStatisticsEnabled(enabled);}
    }
}
