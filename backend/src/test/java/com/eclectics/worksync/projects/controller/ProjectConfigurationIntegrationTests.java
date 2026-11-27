package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.service.*;
import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.service.UserService;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ProjectConfigurationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired ProjectService creation;
    @Autowired ProjectTeamService teams;
    @Autowired ProjectConfigurationService configuration;
    @Autowired javax.sql.DataSource dataSource;
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.url}") String dbUrl;
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.username}") String dbUser;
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.password}") String dbPassword;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager entityManager;
    @Autowired JwtService jwt;
    @MockitoBean JavaMailSender sender;
    UserResponse owner;
    UUID project;
    TransactionTemplate tx(){var t=new TransactionTemplate(manager);t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t;}
    @BeforeEach void setup(){
        cleanup();owner=fixture(SystemRole.USER,AccountStatus.ACTIVE);
        var admin=fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(),owner.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        project=creation.createProject(owner.id(),new CreateProjectRequest("Transfer",null,null,null,null,null,null,null)).id();
    }
    @AfterEach void cleanup(){tx().executeWithoutResult(t->{for(String table:List.of("bug_verifications","bug_details","work_item_links","work_items","organization_permissions","project_members","projects","refresh_tokens","password_reset_tokens","users"))jdbc.update("DELETE FROM "+table);});}
    UserResponse fixture(SystemRole role,AccountStatus status){
        var u=users.createPendingUser(new CreateUserCommand("Test","Person",UUID.randomUUID()+"@example.com","+254712345678","fixture-hash"));
        jdbc.update("UPDATE users SET role=?,status=? WHERE id=?",role.name(),status.name(),u.id());return users.getUserById(u.id());
    }
    UserResponse participant(ProjectRole role){var u=fixture(SystemRole.USER,AccountStatus.ACTIVE);teams.addMember(project,owner.id(),new AddProjectMemberRequest(u.id(),role,role==ProjectRole.MEMBER?ProjectResponsibility.DEVELOPER:null));return u;}
    String token(UserResponse u){return "Bearer "+jwt.generateToken(u.id().toString(),u.email(),"ADMIN","ACTIVE",Set.of("PROJECT_CREATE"));}
    String path(){return "/api/v1/projects/"+project+"/configuration";}
    String body(UUID target){return "{\"newOwnerUserId\":\""+target+"\"}";}
    UUID currentOwner(){return jdbc.queryForObject("SELECT owner_user_id FROM projects WHERE id=?",UUID.class,project);}

    @Test void defaultsAreEmptyForNewProjectsAndPersistAcrossTransactions() throws Exception {
        assertThat(configuration.get(project,owner.id())).isEqualTo(new ProjectConfigurationResponse(List.of(),List.of()));
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"enabledViews\":[],\"enabledWorkTypes\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabledViews").isEmpty()).andExpect(jsonPath("$.enabledWorkTypes").isEmpty());
        assertThat(configuration.get(project,owner.id()).enabledViews()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_enabled_views",Integer.class)).isZero();
    }
    @ParameterizedTest @EnumSource(ProjectRole.class)
    void roleAuthorization(ProjectRole role) throws Exception {
        var actor=participant(role);
        mvc.perform(get(path()).header("Authorization",token(actor))).andExpect(status().is(role==ProjectRole.VIEWER?403:200));
        mvc.perform(patch(path()).header("Authorization",token(actor)).contentType("application/json").content("{\"enabledViews\":[]}"))
                .andExpect(status().is(role==ProjectRole.MANAGER?200:403));
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"enabledViews\":null}","{\"enabledWorkTypes\":null}",
        "{\"enabledViews\":[null]}","{\"enabledViews\":[\"LIST\",\"LIST\"]}","{\"enabledViews\":[1]}",
        "{\"enabledViews\":[true]}","{\"enabledViews\":\"LIST\"}","{\"enabledViews\":{}}",
        "{\"enabledViews\":false}","{\"enabledWorkTypes\":[\"UNKNOWN\"]}","{\"enabledViews\":[\"PLANNING\"]}",
        "{\"enabledViews\":[\"LIST\"]}","{\"enabledViews\":[\"BOARD\"]}","{\"enabledWorkTypes\":[\"EPIC\"]}",
        "{\"enabledWorkTypes\":[\"STORY\"]}","{\"enabledWorkTypes\":[\"TASK\"]}","{\"enabledWorkTypes\":[\"BUG\"]}",
        "{\"ownerUserId\":null}","{\"enabledWorkTypes\":[\"TASK\",\"TASK\"]}"})
    void invalidOrUnavailableRequestsNeverPersist(String body) throws Exception {
        var before=jdbc.queryForMap("SELECT * FROM projects WHERE id=?",project);
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid project request"));
        assertThat(jdbc.queryForMap("SELECT * FROM projects WHERE id=?",project)).isEqualTo(before);
        assertThat(configuration.get(project,owner.id()).enabledViews()).isEmpty();
        assertThat(configuration.get(project,owner.id()).enabledWorkTypes()).isEmpty();
    }
    @ParameterizedTest @EnumSource(SystemRole.class)
    void outsidersCannotUseRoleOrCreateGrant(SystemRole role) throws Exception {
        var actor=fixture(role,AccountStatus.ACTIVE);var admin=fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(),actor.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        mvc.perform(get(path()).header("Authorization",token(actor))).andExpect(status().isNotFound());
        mvc.perform(patch(path()).header("Authorization",token(actor)).contentType("application/json").content("{\"enabledViews\":[]}"))
                .andExpect(status().isNotFound());
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names="ACTIVE",mode=EnumSource.Mode.EXCLUDE)
    void inactiveOwnerDenied(AccountStatus state) throws Exception {
        String token=token(owner);jdbc.update("UPDATE users SET status=? WHERE id=?",state.name(),owner.id());
        mvc.perform(get(path()).header("Authorization",token)).andExpect(status().is(state==AccountStatus.PENDING_APPROVAL?403:401));
        mvc.perform(patch(path()).header("Authorization",token).contentType("application/json").content("{\"enabledViews\":[]}"))
                .andExpect(status().is(state==AccountStatus.PENDING_APPROVAL?403:401));
    }
    @Test void archivedInternalReadsRemainAvailableButWritesConflict() throws Exception {
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE id=?",project);
        mvc.perform(get(path()).header("Authorization",token(owner))).andExpect(status().isOk());
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"enabledViews\":[]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
    }
    @Test void persistedSettingsAreReadAndOmittedCollectionsSurviveClearingOtherCollection() throws Exception {
        // SQL fixture only: public APIs must never activate these unavailable features.
        jdbc.update("INSERT INTO project_enabled_views VALUES (?, 'LIST')",project);
        jdbc.update("INSERT INTO project_enabled_work_types VALUES (?, 'TASK')",project);
        mvc.perform(get(path()).header("Authorization",token(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledViews[0]").value("LIST")).andExpect(jsonPath("$.enabledWorkTypes[0]").value("TASK"));
        for(String endpoint:List.of("/api/v1/projects/"+project,"/api/v1/projects/"+project+"/permissions/me")) {
            String prefix=endpoint.endsWith("/me")?"$":"$.permissions";
            mvc.perform(get(endpoint).header("Authorization",token(owner))).andExpect(status().isOk())
                    .andExpect(jsonPath(prefix+".configuration.enabledViews[0]").value("LIST"));
        }
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"enabledViews\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabledViews").isEmpty()).andExpect(jsonPath("$.enabledWorkTypes[0]").value("TASK"));
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"enabledWorkTypes\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabledWorkTypes").isEmpty());
        assertThat(configuration.get(project,owner.id()).enabledWorkTypes()).isEmpty();
    }
    @Test void viewerCannotSeePersistedInternalConfiguration() throws Exception {
        var viewer=participant(ProjectRole.VIEWER);
        jdbc.update("INSERT INTO project_enabled_views VALUES (?, 'BOARD')",project);
        mvc.perform(get("/api/v1/projects/"+project+"/permissions/me").header("Authorization",token(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configuration").doesNotExist())
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.contains("VIEW_PROJECT")));
    }
    @Test void concurrentPartialClearsDoNotLoseOtherChanges() throws Exception {
        jdbc.update("INSERT INTO project_enabled_views VALUES (?, 'LIST')",project);
        jdbc.update("INSERT INTO project_enabled_work_types VALUES (?, 'TASK')",project);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"enabledViews\":[]}")).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{start.await();return mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"enabledWorkTypes\":[]}")).andReturn().getResponse().getStatus();});
            start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(200);assertThat(b.get(15,TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(configuration.get(project,owner.id())).isEqualTo(new ProjectConfigurationResponse(List.of(),List.of()));
    }
    @Test void missingMalformedAndUnauthenticatedRequestsAreSafe() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects/invalid/configuration").header("Authorization",token(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid project request"));
        mvc.perform(get("/api/v1/projects/"+UUID.randomUUID()+"/configuration").header("Authorization",token(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Project not found"));
    }
    @Test void removedMemberLosesConfigurationAccess() throws Exception {
        var member=participant(ProjectRole.MEMBER);String token=token(member);
        jdbc.update("DELETE FROM project_members WHERE project_id=? AND user_id=?",project,member.id());
        mvc.perform(get(path()).header("Authorization",token)).andExpect(status().isNotFound());
    }
    @Test void rolledBackClearPreservesPersistedConfiguration() {
        jdbc.update("INSERT INTO project_enabled_views VALUES (?, 'LIST')",project);
        var request=new UpdateProjectConfigurationRequest();
        request.setEnabledViews(tools.jackson.databind.json.JsonMapper.builder().build().readTree("[]"));
        assertThatThrownBy(()->tx().executeWithoutResult(t->{configuration.update(project,owner.id(),request);throw new IllegalStateException("Rollback fixture");}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(configuration.get(project,owner.id()).enabledViews()).containsExactly("LIST");
    }
    @ParameterizedTest @EnumSource(value=ProjectRole.class,names={"MEMBER","VIEWER"})
    void cachedManagerCannotAuthorizeAfterAuthoritativeDemotion(ProjectRole currentRole) {
        var actor=participant(ProjectRole.MANAGER);
        UUID membership=jdbc.queryForObject("SELECT id FROM project_members WHERE project_id=? AND user_id=?",UUID.class,project,actor.id());
        jdbc.update("INSERT INTO project_enabled_views VALUES (?, 'LIST')",project);
        assertThatThrownBy(()->tx().executeWithoutResult(t->{
            var cached=entityManager.find(ProjectMember.class,membership);
            assertThat(cached.getRole()).isEqualTo(ProjectRole.MANAGER);
            // SQL represents authoritative state not yet reflected in the managed entity.
            jdbc.update("UPDATE project_members SET role=?,responsibility=? WHERE id=?",currentRole.name(),
                    currentRole==ProjectRole.MEMBER?"QA_TESTER":null,membership);
            var request=new UpdateProjectConfigurationRequest();
            request.setEnabledViews(tools.jackson.databind.json.JsonMapper.builder().build().readTree("[]"));
            configuration.update(project,actor.id(),request);
        })).isInstanceOf(ProjectAccessDeniedException.class);
        assertThat(configuration.get(project,owner.id()).enabledViews()).containsExactly("LIST");
    }
    @Test void cachedManagerCannotReadConfigurationAfterBecomingViewer() {
        var actor=participant(ProjectRole.MANAGER);
        UUID membership=jdbc.queryForObject("SELECT id FROM project_members WHERE project_id=? AND user_id=?",UUID.class,project,actor.id());
        assertThatThrownBy(()->tx().executeWithoutResult(t->{
            assertThat(entityManager.find(ProjectMember.class,membership).getRole()).isEqualTo(ProjectRole.MANAGER);
            jdbc.update("UPDATE project_members SET role='VIEWER' WHERE id=?",membership);
            configuration.get(project,actor.id());
        })).isInstanceOf(ProjectAccessDeniedException.class);
    }
    @Test void heldProjectLockMakesCompetingUpdateWaitAndRecheckArchivedState() throws Exception {
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        var waiterPid=new java.util.concurrent.atomic.AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            // Lock-holder fixture never requests a Users lock while holding this Project lock.
            var holder=pool.submit(()->tx().executeWithoutResult(t->{
                jdbc.queryForObject("SELECT id FROM projects WHERE id=? FOR UPDATE",UUID.class,project);
                held.countDown();awaitLatch(release);
                jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE id=?",project);
            }));
            awaitLatch(held);
            var waiter=pool.submit(()->tx().executeWithoutResult(t->{
                waiterPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));attempting.countDown();
                var request=new UpdateProjectConfigurationRequest();
                request.setEnabledViews(tools.jackson.databind.json.JsonMapper.builder().build().readTree("[]"));
                configuration.update(project,owner.id(),request);
            }));
            try {
                awaitLatch(attempting);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                boolean blocked=false;
                while(System.nanoTime()<deadline) {
                    blocked=Boolean.TRUE.equals(jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?)) > 0",Boolean.class,waiterPid.get()));
                    if(blocked)break;
                    Thread.yield();
                }
                assertThat(blocked).as("PostgreSQL confirms competing transaction is waiting on a lock").isTrue();
                assertThat(waiter.isDone()).isFalse();
            } finally {release.countDown();}
            holder.get(15,TimeUnit.SECONDS);
            assertThatThrownBy(()->waiter.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(ProjectArchivedException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM projects WHERE id=?",String.class,project)).isEqualTo("ARCHIVED");
        } finally {release.countDown();}
    }
    private static void awaitLatch(CountDownLatch latch) {
        try {assertThat(latch.await(15,TimeUnit.SECONDS)).isTrue();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
    @Test void migrationFromV7PreservesExistingProjectsAndAddsEmptyConfiguration() throws Exception {
        String schema="config_migration_"+UUID.randomUUID().toString().replace("-","");
        try {
            var flyway=org.flywaydb.core.Flyway.configure().dataSource(dbUrl,dbUser,dbPassword).schemas(schema).defaultSchema(schema).target("7").load();
            flyway.migrate();
            try(var connection=dataSource.getConnection();var statement=connection.createStatement()) {
                connection.setAutoCommit(false);statement.execute("SET LOCAL search_path TO "+schema);
                statement.execute("INSERT INTO users(id,first_name,last_name,email,phone_number,password_hash) VALUES ('"+owner.id()+"','Old','Owner','old@example.com','+254712345678','fixture-hash')");
                statement.execute("INSERT INTO projects(id,creator_user_id,owner_user_id,name) VALUES ('"+project+"','"+owner.id()+"','"+owner.id()+"','Existing')");
                statement.execute("INSERT INTO project_members(project_id,user_id,role) VALUES ('"+project+"','"+owner.id()+"','MANAGER')");connection.commit();
            }
            var result=org.flywaydb.core.Flyway.configure().dataSource(dbUrl,dbUser,dbPassword).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(result.migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT name FROM "+schema+".projects WHERE id=?",String.class,project)).isEqualTo("Existing");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".project_enabled_views",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".project_enabled_work_types",Integer.class)).isZero();
        } finally {jdbc.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
    }
}
