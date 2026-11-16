package com.eclectics.worksync.integration;

import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.users.dto.CreateUserCommand;
import com.eclectics.worksync.users.dto.UpdateProfileRequest;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.exception.AdministratorAccessDeniedException;
import com.eclectics.worksync.users.exception.ProtectedAdministratorOperationException;
import com.eclectics.worksync.users.repository.UserRepository;
import com.eclectics.worksync.users.service.UserService;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.MockMvc;

/** Separate committed transactions and real PostgreSQL locks; no live SMTP or printed tokens. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class AdminUserActionsIntegrationTests {
    @Autowired UserService users;
    @Autowired UserRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender sender;

    @BeforeEach
    @AfterEach
    void cleanCommittedFixtures() {
        repository.deleteAllInBatch();
    }

    private UserResponse fixture(String name, SystemRole role, AccountStatus status) {
        var user = users.createPendingUser(new CreateUserCommand(
                name, "Fixture", name + "@example.com", "+254712345678", "encoded-hash-fixture"));
        // Fixture-only SQL, not a role/status management API.
        jdbc.update("UPDATE users SET role = ?, status = ? WHERE id = ?", role.name(), status.name(), user.id());
        return users.getUserById(user.id());
    }

    private String authorization(UserResponse user) {
        return "Bearer " + jwt.generateToken(user.id().toString(), user.email(), "ADMIN", "ACTIVE", Set.of());
    }

    private String statusBody(String state) {
        return JsonMapper.builder().build().writeValueAsString(java.util.Map.of("status", state));
    }

    @ParameterizedTest
    @CsvSource({"approve,PENDING_APPROVAL,ACTIVE", "reject,PENDING_APPROVAL,REJECTED",
            "BLOCKED,ACTIVE,BLOCKED", "ACTIVE,BLOCKED,ACTIVE"})
    void persistsLifecycleTransitionAndPreservesProtectedFields(String operation, AccountStatus before,
            AccountStatus after) throws Exception {
        var admin = fixture("actor", SystemRole.ADMIN, AccountStatus.ACTIVE);
        var target = fixture("target", SystemRole.USER, before);
        jdbc.update("UPDATE users SET updated_at = created_at - INTERVAL '1 day' WHERE id = ?", target.id());
        var original = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", target.id());
        String endpoint = List.of("approve", "reject").contains(operation) ? operation : "status";
        var request = patch("/api/v1/admin/users/" + target.id() + "/" + endpoint)
                .header("Authorization", authorization(admin));
        if (endpoint.equals("status")) request.contentType(MediaType.APPLICATION_JSON).content(statusBody(operation));
        if (endpoint.equals("reject")) request.contentType(MediaType.APPLICATION_JSON).content(JsonMapper.builder().build().writeValueAsString(java.util.Map.of("reason", "Optional")));
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(after.name()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.reason").doesNotExist());
        var persisted = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", target.id());
        assertThat(persisted.get("status")).isEqualTo(after.name());
        assertThat(persisted.get("updated_at")).isNotEqualTo(original.get("updated_at"));
        for (String field : List.of("email", "role", "password_hash", "first_name", "last_name", "phone_number", "created_at")) {
            assertThat(persisted.get(field).equals(original.get(field))).as(field + " unchanged").isTrue();
        }
    }

    @ParameterizedTest
    @CsvSource({"approve,ACTIVE", "approve,REJECTED", "reject,ACTIVE", "BLOCKED,PENDING_APPROVAL",
            "BLOCKED,REJECTED", "ACTIVE,PENDING_APPROVAL", "ACTIVE,REJECTED", "ACTIVE,ACTIVE"})
    void invalidTransitionsAre409WithoutChangingState(String operation, AccountStatus state) throws Exception {
        var admin = fixture("actor", SystemRole.ADMIN, AccountStatus.ACTIVE);
        var target = fixture("target", SystemRole.USER, state);
        String endpoint = List.of("approve", "reject").contains(operation) ? operation : "status";
        var request = patch("/api/v1/admin/users/" + target.id() + "/" + endpoint)
                .header("Authorization", authorization(admin));
        if (endpoint.equals("status")) request.contentType(MediaType.APPLICATION_JSON).content(statusBody(operation));
        mvc.perform(request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_STATUS_TRANSITION"));
        assertThat(users.getUserById(target.id()).status()).isEqualTo(state);
    }

    @Test
    void selfBlockingAndLastActiveAdminAreProtectedInServiceAndHttp() throws Exception {
        var only = fixture("only", SystemRole.ADMIN, AccountStatus.ACTIVE);
        fixture("pending", SystemRole.ADMIN, AccountStatus.PENDING_APPROVAL);
        fixture("blocked", SystemRole.ADMIN, AccountStatus.BLOCKED);
        mvc.perform(patch("/api/v1/admin/users/" + only.id() + "/status")
                .header("Authorization", authorization(only)).contentType(MediaType.APPLICATION_JSON)
                .content(statusBody("BLOCKED"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROTECTED_ADMINISTRATOR_OPERATION"));
        assertThatThrownBy(() -> users.blockUser(only.id()))
                .isInstanceOf(ProtectedAdministratorOperationException.class);
        assertThat(users.getUserById(only.id()).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void selfBlockingIsForbiddenEvenWithBackupAdmin() {
        var first = fixture("first", SystemRole.ADMIN, AccountStatus.ACTIVE);
        fixture("backup", SystemRole.ADMIN, AccountStatus.ACTIVE);
        assertThatThrownBy(() -> users.blockUser(first.id(), first.id()))
                .isInstanceOf(ProtectedAdministratorOperationException.class);
    }

    @Test
    void blockingAnotherAdminRevokesTheirOldJwtImmediately() throws Exception {
        var actor = fixture("actor", SystemRole.ADMIN, AccountStatus.ACTIVE);
        var target = fixture("target", SystemRole.ADMIN, AccountStatus.ACTIVE);
        String old = authorization(target);
        mvc.perform(patch("/api/v1/admin/users/" + target.id() + "/status")
                .header("Authorization", authorization(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(statusBody("BLOCKED"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization", old)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/" + actor.id() + "/status")
                .header("Authorization", old).contentType(MediaType.APPLICATION_JSON)
                .content(statusBody("BLOCKED"))).andExpect(status().isUnauthorized());
        assertThat(repository.countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE)).isEqualTo(1);
    }

    @Test
    void actorIsRecheckedInsideServiceIncludingCachedEntityState() {
        var actor = fixture("actor", SystemRole.ADMIN, AccountStatus.ACTIVE);
        var target = fixture("target", SystemRole.USER, AccountStatus.PENDING_APPROVAL);
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            users.getUserById(actor.id()); // Cached ACTIVE actor inside outer transaction.
            jdbc.update("UPDATE users SET status = 'BLOCKED' WHERE id = ?", actor.id());
            assertThatThrownBy(() -> users.approveUser(actor.id(), target.id()))
                    .isInstanceOf(AdministratorAccessDeniedException.class);
            status.setRollbackOnly();
        });
        assertThat(users.getUserById(target.id()).status()).isEqualTo(AccountStatus.PENDING_APPROVAL);
    }

    @Test
    void snapshotIsolationCannotBypassLastAdminGuard() {
        var actor = fixture("actor", SystemRole.ADMIN, AccountStatus.ACTIVE);
        var transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> users.blockUser(actor.id())))
                .isInstanceOf(ProtectedAdministratorOperationException.class);
        assertThat(users.getUserById(actor.id()).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void simultaneousInternalBlocksCannotRemoveBothActiveAdmins() throws Exception {
        var first = fixture("first", SystemRole.ADMIN, AccountStatus.ACTIVE);
        var second = fixture("second", SystemRole.ADMIN, AccountStatus.ACTIVE);
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> a = pool.submit(() -> competingBlock(first.id(), ready, start));
            Future<Boolean> b = pool.submit(() -> competingBlock(second.id(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(repository.countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE)).isEqualTo(1);
    }

    private boolean competingBlock(UUID target, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent fixture did not start");
        try {
            users.blockUser(target);
            return true;
        } catch (ProtectedAdministratorOperationException expected) {
            return false;
        }
    }

    @Test
    void concurrentActorsCannotDisableEachOtherAfterRevocation() throws Exception {
        var first = fixture("first", SystemRole.ADMIN, AccountStatus.ACTIVE);
        var second = fixture("second", SystemRole.ADMIN, AccountStatus.ACTIVE);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> a = pool.submit(() -> actorBlock(first.id(), second.id(), start));
            Future<Boolean> b = pool.submit(() -> actorBlock(second.id(), first.id(), start));
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(repository.countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE)).isEqualTo(1);
    }

    private boolean actorBlock(UUID actor, UUID target, CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent fixture did not start");
        try {
            users.blockUser(actor, target);
            return true;
        } catch (AdministratorAccessDeniedException | ProtectedAdministratorOperationException expected) {
            return false;
        }
    }

    @Test
    void profileAndPasswordUpdatesPreserveAnAlreadyChangedStatus() {
        var first = fixture("first", SystemRole.ADMIN, AccountStatus.ACTIVE);
        fixture("second", SystemRole.ADMIN, AccountStatus.ACTIVE);
        users.blockUser(first.id());
        users.updateProfile(first.id(), new UpdateProfileRequest("Changed", "Admin", "+254700000001"));
        users.resetPassword(first.email(), "replacement-hash-fixture");
        assertThat(users.getUserById(first.id()).status()).isEqualTo(AccountStatus.BLOCKED);
    }

    @Test
    void unknownTargetIs404OnEveryAction() throws Exception {
        var actor = fixture("actor", SystemRole.ADMIN, AccountStatus.ACTIVE);
        for (String endpoint : List.of("approve", "reject", "status")) {
            var request = patch("/api/v1/admin/users/" + UUID.randomUUID() + "/" + endpoint)
                    .header("Authorization", authorization(actor));
            if (endpoint.equals("status")) request.contentType(MediaType.APPLICATION_JSON).content(statusBody("BLOCKED"));
            mvc.perform(request).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }
}
