package com.eclectics.worksync.users.service;

import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.exception.*;
import com.eclectics.worksync.users.repository.UserRepository;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Disposable PostgreSQL only. Real JWT/filter/service chain, mocked SMTP, no Projects creation integration. */
@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("test")
class OrganizationPermissionIntegrationTests {
    @MockitoBean JavaMailSender mailSender;
    @Autowired UserService users;
    @Autowired UserRepository accounts;
    @Autowired com.eclectics.worksync.users.repository.UserPermissionRepository grantRepository;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager manager;
    final OrganizationPermission permission=OrganizationPermission.PROJECT_CREATE;
    UpdatePermissionsRequest grant() {return new UpdatePermissionsRequest(List.of(permission));}
    UpdatePermissionsRequest revoke() {return new UpdatePermissionsRequest(List.of());}
    TransactionTemplate tx() {var t=new TransactionTemplate(manager);t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t;}
    @BeforeEach @AfterEach void cleanup() {
        tx().executeWithoutResult(t -> {
            jdbc.update("DELETE FROM organization_permissions");jdbc.update("DELETE FROM project_members");
            jdbc.update("DELETE FROM projects");jdbc.update("DELETE FROM refresh_tokens");
            jdbc.update("DELETE FROM password_reset_tokens");jdbc.update("DELETE FROM users");
        });
    }
    UserResponse fixture(SystemRole role,AccountStatus status) {
        var u=users.createPendingUser(new CreateUserCommand("Test","User",UUID.randomUUID()+"@example.com","+254712345678","test-fixture"));
        jdbc.update("UPDATE users SET role=?,status=? WHERE id=?",role.name(),status.name(),u.id());
        return users.getUserById(u.id());
    }
    UserResponse admin() {return fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);}
    UserResponse active() {return fixture(SystemRole.USER,AccountStatus.ACTIVE);}
    String bearer(UserResponse user) {
        return "Bearer "+jwt.generateToken(user.id().toString(),user.email(),"ADMIN","ACTIVE",Set.of("PROJECT_CREATE"));
    }
    String url(UUID id) {return "/api/v1/admin/users/"+id+"/permissions";}
    @Test void newAndApprovedUsersHaveNoGrantsAndAdminRoleDoesNotGrant() {
        var pending=fixture(SystemRole.USER,AccountStatus.PENDING_APPROVAL);
        assertThat(pending.permissions()).isEmpty();
        assertThat(users.approveUser(pending.id()).permissions()).isEmpty();
        assertThat(users.hasPermission(admin().id(),permission)).isFalse();
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> users.requirePermissionForWrite(pending.id(),permission)))
                .isInstanceOf(OrganizationPermissionDeniedException.class);
    }
    @Test void patchReplacesEntireCollectionAndExistingTokenDoesNotRetainPermission() throws Exception {
        var actor=admin();var target=active();String token=bearer(target);
        mvc.perform(patch(url(target.id())).header("Authorization",bearer(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[\"PROJECT_CREATE\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions[0]").value("PROJECT_CREATE"))
                .andExpect(jsonPath("$.effectivePermissions[0]").value("PROJECT_CREATE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
        var row=jdbc.queryForMap("SELECT id,granted_at,granted_by_user_id FROM organization_permissions WHERE user_id=?",target.id());
        users.updatePermissions(actor.id(),target.id(),grant());
        assertThat(jdbc.queryForMap("SELECT id,granted_at,granted_by_user_id FROM organization_permissions WHERE user_id=?",target.id())).isEqualTo(row);
        mvc.perform(get("/api/v1/users/me").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.permissions[0]").value("PROJECT_CREATE"));
        mvc.perform(get("/api/v1/admin/users").header("Authorization",bearer(actor))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '"+target.id()+"')].permissions[0]").value("PROJECT_CREATE"));
        mvc.perform(patch(url(target.id())).header("Authorization",bearer(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions").isEmpty());
        mvc.perform(get("/api/v1/users/me").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.effectivePermissions").isEmpty());
        assertThat(users.hasPermission(target.id(),permission)).isFalse();
    }
    @ParameterizedTest
    @ValueSource(strings={"{}","{\"permissions\":null}","{\"permissions\":[null]}","{\"permissions\":[\"PROJECT_CREATE\",\"PROJECT_CREATE\"]}","{\"permissions\":[\"ADMIN\"]}","{\"permissions\":[\"MANAGER\"]}","{\"permissions\":[],\"actorId\":\"spoof\"}","{\"permissions\":[],\"role\":\"ADMIN\"}","{\"permissions\":\"PROJECT_CREATE\"}"})
    void invalidRequestsAre400AndCannotPartiallyRevoke(String body) throws Exception {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        mvc.perform(patch(url(target.id())).header("Authorization",bearer(actor)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(users.getGrantedPermissions(target.id())).containsExactly(permission);
    }
    @Test void securityChainRejectsUnauthenticatedOrdinaryPendingAndBlockedActors() throws Exception {
        var target=active();String body="{\"permissions\":[\"PROJECT_CREATE\"]}";
        mvc.perform(patch(url(target.id())).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        for (var state : AccountStatus.values()) {
            var actor=fixture(SystemRole.ADMIN,state);
            mvc.perform(patch(url(target.id())).header("Authorization",bearer(actor)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().is(state==AccountStatus.ACTIVE?200:state==AccountStatus.PENDING_APPROVAL?403:401));
        }
        var ordinary=active();
        mvc.perform(patch(url(target.id())).header("Authorization",bearer(ordinary)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }
    @Test void missingPrincipalMissingTargetAndInactiveTargetHaveSafeErrors() throws Exception {
        var auth=new UsernamePasswordAuthenticationToken("not-a-WorkSyncPrincipal",null,AuthorityUtils.createAuthorityList("ROLE_ADMIN","STATUS_ACTIVE"));
        mvc.perform(patch(url(UUID.randomUUID())).with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[]}"))
                .andExpect(status().isUnauthorized());
        var actor=admin();
        mvc.perform(patch(url(UUID.randomUUID())).header("Authorization",bearer(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        var pending=fixture(SystemRole.USER,AccountStatus.PENDING_APPROVAL);
        mvc.perform(patch(url(pending.id())).header("Authorization",bearer(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[\"PROJECT_CREATE\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PERMISSION_TARGET"));
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names="ACTIVE",mode=EnumSource.Mode.EXCLUDE)
    void inactiveAccountsRetainStoredGrantsButCannotExerciseThem(AccountStatus state) {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        jdbc.update("UPDATE users SET status=? WHERE id=?",state.name(),target.id());
        var response=users.getUserById(target.id());
        assertThat(response.permissions()).containsExactly(permission);assertThat(response.effectivePermissions()).isEmpty();
        assertThat(users.hasPermission(target.id(),permission)).isFalse();
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> users.requirePermissionForWrite(target.id(),permission))).isInstanceOf(OrganizationPermissionDeniedException.class);
        users.updatePermissions(actor.id(),target.id(),revoke());assertThat(users.getGrantedPermissions(target.id())).isEmpty();
    }
    @Test void rollbackRestoresPriorCollection() {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> {users.updatePermissions(actor.id(),target.id(),revoke());throw new IllegalStateException("rollback fixture");}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.hasPermission(target.id(),permission)).isTrue();
    }
    @Test void constraintsRejectDuplicatesUnsupportedNamesAndMissingUsers() {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO organization_permissions(user_id,permission,granted_by_user_id) VALUES (?,'PROJECT_CREATE',?)",target.id(),actor.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO organization_permissions(user_id,permission,granted_by_user_id) VALUES (?,'ADMIN',?)",target.id(),actor.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO organization_permissions(user_id,permission,granted_by_user_id) VALUES (?,'PROJECT_CREATE',?)",UUID.randomUUID(),actor.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id=?",target.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void mandatoryGuardRejectsNoTransactionReadonlyAndSnapshotIsolation() {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        assertThatThrownBy(() -> users.requirePermissionForWrite(target.id(),permission)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var readOnly=tx();readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(t -> users.requirePermissionForWrite(target.id(),permission))).isInstanceOf(PermissionTransactionRequiredException.class);
        var snapshot=tx();snapshot.setIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
        assertThatThrownBy(() -> snapshot.executeWithoutResult(t -> users.requirePermissionForWrite(target.id(),permission))).isInstanceOf(PermissionTransactionRequiredException.class);
        tx().executeWithoutResult(t -> users.requirePermissionForWrite(target.id(),permission));
    }
    static void await(CountDownLatch latch) {
        try {assertThat(latch.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
    @Test void guardHoldsLockUntilOuterCommitAndRevocationWaits() throws Exception {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        var checked=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var writer=pool.submit(() -> tx().executeWithoutResult(t -> {users.requirePermissionForWrite(target.id(),permission);checked.countDown();await(release);jdbc.update("UPDATE users SET first_name='Guarded' WHERE id=?",target.id());}));
            await(checked);
            var revoker=pool.submit(() -> {attempting.countDown();return users.updatePermissions(actor.id(),target.id(),revoke());});
            try {await(attempting);assertThatThrownBy(() -> revoker.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}
            finally {release.countDown();}
            writer.get(10,TimeUnit.SECONDS);revoker.get(10,TimeUnit.SECONDS);
        }
        assertThat(users.getUserById(target.id()).firstName()).isEqualTo("Guarded");assertThat(users.hasPermission(target.id(),permission)).isFalse();
    }
    @Test void revocationWinningRaceDeniesSubsequentGuard() throws Exception {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        var revoked=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var revoker=pool.submit(() -> tx().executeWithoutResult(t -> {users.updatePermissions(actor.id(),target.id(),revoke());revoked.countDown();await(release);}));
            await(revoked);
            var writer=pool.submit(() -> {attempting.countDown();tx().executeWithoutResult(t -> users.requirePermissionForWrite(target.id(),permission));});
            try {await(attempting);assertThatThrownBy(() -> writer.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}
            finally {release.countDown();}
            revoker.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(() -> writer.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(OrganizationPermissionDeniedException.class);
        }
    }
    @Test void concurrentReplacementsAreSerializedWithoutDuplicates() throws Exception {
        var actor=admin();var target=active();var first=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> tx().executeWithoutResult(t -> {users.updatePermissions(actor.id(),target.id(),grant());first.countDown();await(release);}));
            await(first);var b=pool.submit(() -> {attempting.countDown();return users.updatePermissions(actor.id(),target.id(),revoke());});
            try {await(attempting);assertThatThrownBy(() -> b.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}
            finally {release.countDown();}
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertThat(users.getGrantedPermissions(target.id())).isEmpty();
    }
    @Test void actorBlockedWhileWaitingCannotGrant() throws Exception {
        var actor=admin();admin();var target=active();var blocked=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var blocker=pool.submit(() -> tx().executeWithoutResult(t -> {users.blockUser(actor.id());blocked.countDown();await(release);}));
            await(blocked);var grantor=pool.submit(() -> {attempting.countDown();return users.updatePermissions(actor.id(),target.id(),grant());});
            try {await(attempting);assertThatThrownBy(() -> grantor.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}
            finally {release.countDown();}
            blocker.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(() -> grantor.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(AdministratorAccessDeniedException.class);
        }
        assertThat(users.getGrantedPermissions(target.id())).isEmpty();
    }
    @Test void refreshedGuardRejectsAccountStateCachedBeforeBlocking() throws Exception {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        var cached=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var checker=pool.submit(() -> tx().executeWithoutResult(t -> {accounts.findById(target.id()).orElseThrow();cached.countDown();await(release);users.requirePermissionForWrite(target.id(),permission);}));
            await(cached);try {users.blockUser(target.id());}finally {release.countDown();}
            assertThatThrownBy(() -> checker.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(OrganizationPermissionDeniedException.class);
        }
    }
    @Test void revocationDoesNotRemoveExistingProjectOwnership() {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());UUID project=UUID.randomUUID();
        tx().executeWithoutResult(t -> {jdbc.update("INSERT INTO projects(id,creator_user_id,owner_user_id,name) VALUES (?,?,?,'Existing project')",project,target.id(),target.id());jdbc.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,'MANAGER')",project,target.id());});
        users.updatePermissions(actor.id(),target.id(),revoke());
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM projects WHERE id=?",UUID.class,project)).isEqualTo(target.id());
        assertThat(jdbc.queryForObject("SELECT role FROM project_members WHERE project_id=? AND user_id=?",String.class,project,target.id())).isEqualTo("MANAGER");
    }
    @Test void revocationDefeatsGrantEntityCachedBeforeCheck() throws Exception {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        var cached=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var checker=pool.submit(() -> tx().executeWithoutResult(t -> {grantRepository.findAll();cached.countDown();await(release);users.requirePermissionForWrite(target.id(),permission);}));
            await(cached);try {users.updatePermissions(actor.id(),target.id(),revoke());}finally {release.countDown();}
            assertThatThrownBy(() -> checker.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(OrganizationPermissionDeniedException.class);
        }
    }
    @Test void blockAndUnblockRecomputeEffectivePermissionsWithoutChangingStoredGrant() {
        var actor=admin();var target=active();users.updatePermissions(actor.id(),target.id(),grant());
        assertThat(users.blockUser(target.id()).effectivePermissions()).isEmpty();
        assertThat(users.getGrantedPermissions(target.id())).containsExactly(permission);
        assertThat(users.hasPermission(target.id(),permission)).isFalse();
        assertThat(users.unblockUser(target.id()).effectivePermissions()).containsExactly(permission);
        assertThat(users.hasPermission(target.id(),permission)).isTrue();
    }

    @Test void upgradingV5PreservesExistingUsersAndStartsWithNoGrants() {
        String schema="permissions_upgrade_"+UUID.randomUUID().toString().replace("-", "");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).target("5").load().migrate();
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO "+schema+".users(id,first_name,last_name,email,phone_number,password_hash,status) VALUES (?, 'Existing','User','existing@example.com','+254712345678','fixture','ACTIVE')",id);
            org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("SELECT id FROM "+schema+".users",UUID.class)).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".organization_permissions",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".flyway_schema_history WHERE version='7' AND success",Integer.class)).isEqualTo(1);
        } finally {jdbc.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
    }

}
