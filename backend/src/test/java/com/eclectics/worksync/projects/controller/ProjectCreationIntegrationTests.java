package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.projects.dto.CreateProjectRequest;
import com.eclectics.worksync.projects.repository.ProjectMemberRepository;
import com.eclectics.worksync.projects.service.ProjectService;
import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.exception.OrganizationPermissionDeniedException;
import com.eclectics.worksync.users.service.UserService;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"debug=false", "logging.level.org.springframework=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectCreationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired JwtService jwt;
    @Autowired ProjectService projects;
    @Autowired PlatformTransactionManager manager;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean JavaMailSender sender;
    @MockitoSpyBean ProjectMemberRepository members;
    final OrganizationPermission permission = OrganizationPermission.PROJECT_CREATE;
    CreateProjectRequest request() { return new CreateProjectRequest("Project", null, null, null, null, null, null, null); }
    TransactionTemplate tx() {
        var tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }
    @BeforeEach @AfterEach void cleanup() {
        reset(members);
        tx().executeWithoutResult(t -> {
            for (String table : List.of("bug_verifications", "bug_details", "work_item_links", "work_items",
                    "organization_permissions", "project_members", "projects", "refresh_tokens", "password_reset_tokens", "users")) {
                jdbc.update("DELETE FROM " + table);
            }
        });
    }
    UserResponse fixture(SystemRole role, AccountStatus state) {
        var u = users.createPendingUser(new CreateUserCommand("Test", "User", UUID.randomUUID()+"@example.com", "+254712345678", "fixture-hash"));
        jdbc.update("UPDATE users SET role=?,status=? WHERE id=?", role.name(), state.name(), u.id());
        return users.getUserById(u.id());
    }
    void grant(UUID user) {
        var admin = fixture(SystemRole.ADMIN, AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(), user, new UpdatePermissionsRequest(List.of(permission)));
    }
    void revoke(UUID user) {
        var admin = fixture(SystemRole.ADMIN, AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(), user, new UpdatePermissionsRequest(List.of()));
    }
    String bearer(UserResponse user) {
        // Deliberately misleading claims: current database state must win.
        return "Bearer " + jwt.generateToken(user.id().toString(), user.email(), "ADMIN", "ACTIVE", Set.of("PROJECT_CREATE"));
    }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table, Integer.class); }

    @Test void createsOwnerAndManagerAtomicallyUsingVerifiedIdentity() throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor)).contentType("application/json")
                .content("{\"name\":\"Delivery\",\"description\":\"Brief\",\"startDate\":\"2026-01-01\",\"dueDate\":\"2026-02-01\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerUserId").value(actor.id().toString()))
                .andExpect(jsonPath("$.creatorUserId").value(actor.id().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.permissions.role").value("MANAGER"))
                .andExpect(jsonPath("$.permissions.isOwner").value(true))
                .andExpect(jsonPath("$.permissions.responsibility").hasJsonPath())
                .andExpect(jsonPath("$.permissions.actions").value(org.hamcrest.Matchers.containsInAnyOrder("VIEW_PROJECT","VIEW_PROJECT_TEAM","UPDATE_PROJECT_DETAILS","SELECT_ELIGIBLE_USERS","ADD_PROJECT_MEMBER","ADD_PROJECT_MANAGER","TRANSFER_PROJECT_OWNERSHIP","VIEW_PROJECT_CONFIGURATION","UPDATE_PROJECT_CONFIGURATION")))
                .andExpect(jsonPath("$.permissions.configuration.enabledViews").isEmpty())
                .andExpect(jsonPath("$.permissions.configuration.enabledWorkTypes").isEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(count("projects")).isOne(); assertThat(count("project_members")).isOne();
        assertThat(jdbc.queryForObject("SELECT role FROM project_members", String.class)).isEqualTo("MANAGER");
        assertThat(jdbc.queryForObject("SELECT user_id FROM project_members", UUID.class)).isEqualTo(actor.id());
    }
    @ParameterizedTest @EnumSource(SystemRole.class)
    void roleWithoutGrantCannotCreate(SystemRole role) throws Exception {
        var actor = fixture(role, AccountStatus.ACTIVE);
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor)).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());
        assertThat(count("projects")).isZero();
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class, names="ACTIVE", mode=EnumSource.Mode.EXCLUDE)
    void inactiveStoredGrantCannotCreate(AccountStatus state) throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        jdbc.update("UPDATE users SET status=? WHERE id=?", state.name(), actor.id());
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor)).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().is(state == AccountStatus.PENDING_APPROVAL ? 403 : 401));
        assertThatThrownBy(() -> projects.createProject(actor.id(), request())).isInstanceOf(OrganizationPermissionDeniedException.class);
    }
    @Test void revocationDefeatsExistingJwt() throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id()); String token = bearer(actor);
        revoke(actor.id());
        mvc.perform(post("/api/v1/projects").header("Authorization", token).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void anonymousCannotCreate() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings={"{}", "{\"name\":\" \"}", "{\"name\":null}", "{\"name\":\"A\",\"startDate\":\"2026-03-01\",\"dueDate\":\"2026-02-01\"}", "{\"name\":\"A\",\"ownerUserId\":\"00000000-0000-0000-0000-000000000001\"}", "{\"name\":\"A\",\"creatorUserId\":\"00000000-0000-0000-0000-000000000001\"}", "{\"name\":\"A\",\"role\":\"MANAGER\"}", "{\"name\":\"A\",\"extra\":true}", "{\"name\":\"A\",\"startDate\":\"invalid\"}"})
    void invalidOrImpersonatingBodiesAreRejected(String body) throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST"));
        assertThat(count("projects")).isZero(); assertThat(count("project_members")).isZero();
    }
    @Test void overlongNameIsRejected() throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor)).contentType("application/json")
                .content("{\"name\":\"" + "A".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(count("projects")).isZero();
    }
    static java.util.stream.Stream<Arguments> invalidTextValues() {
        return java.util.stream.Stream.of("name", "description", "requirements", "scope", "deliverables", "successCriteria")
                .flatMap(field -> java.util.stream.Stream.of("123", "false", "null", "[]", "{}")
                        .map(value -> Arguments.of(field, value)));
    }
    @ParameterizedTest @MethodSource("invalidTextValues")
    void textFieldsRejectNonStringsWithoutPersistence(String field, String value) throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        String body = field.equals("name") ? "{\"name\":" + value + "}"
                : "{\"name\":\"Project\",\"" + field + "\":" + value + "}";
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor))
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST"));
        assertThat(count("projects")).isZero(); assertThat(count("project_members")).isZero();
    }
    @Test void omittedOptionalTextAndNullDatesAreAccepted() throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor)).contentType("application/json")
                .content("{\"name\":\"Minimal\",\"startDate\":null,\"dueDate\":null}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Minimal"));
        assertThat(count("projects")).isOne(); assertThat(count("project_members")).isOne();
    }
    @Test @SuppressWarnings("unchecked")
    void allValidTextAndDatesReturnCompleteCreationResponseShape() throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        String json = mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor)).contentType("application/json")
                .content("{\"name\":\"Project\",\"description\":\"\",\"requirements\":\"Requirements\",\"scope\":\"Scope\",\"deliverables\":\"Delivery\",\"successCriteria\":\"Success\",\"startDate\":\"2026-01-01\",\"dueDate\":\"2026-02-01\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Map<String, Object> response = tools.jackson.databind.json.JsonMapper.builder().build().readValue(json, Map.class);
        assertThat(response.keySet()).containsExactlyInAnyOrder("id", "creatorUserId", "ownerUserId", "name", "description",
                "requirements", "scope", "deliverables", "successCriteria", "startDate", "dueDate", "status", "permissions", "createdAt", "updatedAt");
        assertThat(UUID.fromString((String) response.get("id"))).isNotNull();
        assertThat(response.get("creatorUserId")).isEqualTo(actor.id().toString());
        assertThat(response.get("ownerUserId")).isEqualTo(actor.id().toString());
        assertThat(response).containsEntry("name", "Project").containsEntry("description", "")
                .containsEntry("requirements", "Requirements").containsEntry("scope", "Scope")
                .containsEntry("deliverables", "Delivery").containsEntry("successCriteria", "Success")
                .containsEntry("status", "ACTIVE").containsEntry("startDate", "2026-01-01").containsEntry("dueDate", "2026-02-01");
        assertThat(java.time.Instant.parse((String) response.get("createdAt"))).isNotNull();
        assertThat(java.time.Instant.parse((String) response.get("updatedAt"))).isNotNull();
        Map<String, Object> permissions = (Map<String, Object>) response.get("permissions");
        assertThat(permissions.keySet()).containsExactlyInAnyOrder("role", "responsibility", "isOwner", "actions", "configuration");
        assertThat(permissions).containsEntry("role", "MANAGER").containsEntry("responsibility", null)
                .containsEntry("isOwner", true);
        assertThat((List<String>) permissions.get("actions")).containsExactlyInAnyOrder("VIEW_PROJECT","VIEW_PROJECT_TEAM","UPDATE_PROJECT_DETAILS","SELECT_ELIGIBLE_USERS","ADD_PROJECT_MEMBER","ADD_PROJECT_MANAGER","TRANSFER_PROJECT_OWNERSHIP","VIEW_PROJECT_CONFIGURATION","UPDATE_PROJECT_CONFIGURATION");
        assertThat((Map<String, Object>) permissions.get("configuration"))
                .containsOnlyKeys("enabledViews", "enabledWorkTypes")
                .containsEntry("enabledViews", List.of()).containsEntry("enabledWorkTypes", List.of());
    }
    @Test void deferredOwnerConstraintFailureAtCommitRollsBackBothInserts() throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        var reachedEndOfMembershipWrite = new AtomicBoolean(false);
        doAnswer(call -> {
            com.eclectics.worksync.projects.entity.ProjectMember member = call.getArgument(0);
            // Repository proxy methods are abstract; use real JPA persistence in this fault injection.
            entityManager.persist(member);
            entityManager.flush();
            // Real database violation: deferred FK allows deletion now but rejects outer commit.
            jdbc.update("DELETE FROM project_members WHERE id=?", member.getId());
            reachedEndOfMembershipWrite.set(true);
            return member;
        }).when(members).saveAndFlush(any());
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor)).contentType("application/json")
                .content("{\"name\":\"Deferred rollback\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_CREATION_CONFLICT"));
        assertThat(reachedEndOfMembershipWrite).isTrue();
        assertThat(count("projects")).isZero(); assertThat(count("project_members")).isZero();
    }
    @Test void membershipFailureRollsBackProjectAndReturnsSafeConflict() throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        doThrow(new org.springframework.dao.DataIntegrityViolationException("private SQL details"))
                .when(members).saveAndFlush(any());
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(actor)).contentType("application/json").content("{\"name\":\"Rollback\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_CREATION_CONFLICT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        assertThat(count("projects")).isZero(); assertThat(count("project_members")).isZero();
    }
    @Test void creationHoldsPermissionLockUntilOuterCommit() throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        var admin = fixture(SystemRole.ADMIN, AccountStatus.ACTIVE);
        var inserted = new CountDownLatch(1); var release = new CountDownLatch(1); var revoking = new CountDownLatch(1);
        var revoked = new AtomicBoolean(false);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var creation = pool.submit(() -> tx().execute(t -> {
                var result = projects.createProject(actor.id(), request()); inserted.countDown(); await(release); return result;
            }));
            await(inserted);
            var revocation = pool.submit(() -> { revoking.countDown(); users.updatePermissions(admin.id(), actor.id(), new UpdatePermissionsRequest(List.of())); revoked.set(true); });
            await(revoking);
            try { assertThatThrownBy(() -> revocation.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            creation.get(10, TimeUnit.SECONDS); revocation.get(10, TimeUnit.SECONDS);
            assertThat(revoked).isTrue(); assertThat(count("projects")).isOne();
            assertThatThrownBy(() -> projects.createProject(actor.id(), request())).isInstanceOf(OrganizationPermissionDeniedException.class);
        } finally { release.countDown(); }
    }
    @Test void revocationWinningLockPreventsWaitingCreation() throws Exception {
        var actor = fixture(SystemRole.USER, AccountStatus.ACTIVE); grant(actor.id());
        var admin = fixture(SystemRole.ADMIN, AccountStatus.ACTIVE);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1); var starting = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var revocation = pool.submit(() -> tx().executeWithoutResult(t -> {
                users.updatePermissions(admin.id(), actor.id(), new UpdatePermissionsRequest(List.of())); held.countDown(); await(release);
            }));
            await(held);
            var creation = pool.submit(() -> { starting.countDown(); return projects.createProject(actor.id(), request()); });
            await(starting);
            try { assertThatThrownBy(() -> creation.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            revocation.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> creation.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(OrganizationPermissionDeniedException.class);
            assertThat(count("projects")).isZero(); assertThat(count("project_members")).isZero();
        } finally { release.countDown(); }
    }
    static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
