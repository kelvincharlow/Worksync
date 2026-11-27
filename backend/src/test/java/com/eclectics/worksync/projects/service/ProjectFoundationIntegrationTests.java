package com.eclectics.worksync.projects.service;

import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.repository.*;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.repository.UserRepository;
import com.eclectics.worksync.users.service.UserService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real Flyway/PostgreSQL; isolated from Auth and SMTP. TEST_DB_* must point to a disposable database. */
@SpringBootTest(classes = ProjectFoundationIntegrationTests.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"debug=false", "logging.level.org.springframework=WARN"})
@ActiveProfiles({"test", "projects-foundation-test"})
class ProjectFoundationIntegrationTests {
    @Configuration @org.springframework.context.annotation.Profile("projects-foundation-test") @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Project.class, User.class})
    @EnableJpaRepositories(basePackageClasses = {ProjectRepository.class, UserRepository.class})
    @Import({ProjectService.class, DefaultProjectAccessService.class, UserService.class})
    static class TestApplication { }
    @MockitoBean JavaMailSender mailSender;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMemberRepository members;
    @Autowired ProjectAccessService access;
    @Autowired ProjectService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    TransactionTemplate tx() { return new TransactionTemplate(manager); }
    @BeforeEach @AfterEach void clean() {
        tx().executeWithoutResult(t -> {
            jdbc.update("DELETE FROM project_members"); jdbc.update("DELETE FROM projects"); jdbc.update("DELETE FROM users");
        });
    }
    UUID user(String status) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,first_name,last_name,email,phone_number,password_hash,status) VALUES (?, 'Test','User',?,'+254712345678','test-fixture',?)",id,id+"@example.com",status);
        return id;
    }
    Project project(UUID owner) {
        return tx().execute(t -> {
            Project p=projects.saveAndFlush(new Project(owner,"Project",null,null,null,null,null,null,null));
            members.saveAndFlush(new ProjectMember(p.getId(),owner,ProjectRole.MANAGER,null));return p;
        });
    }
    @Test void persistsOwnerManagerAndSafeProjectionsThroughMigrationSchema() {
        UUID owner=user("ACTIVE");Project p=project(owner);
        assertThat(projects.findById(p.getId())).get().extracting(Project::getOwnerUserId).isEqualTo(owner);
        assertThat(access.getMemberAccess(p.getId(),owner).isOwner()).isTrue();
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(ProjectResponse.from(p, ProjectPermissionsResponse.initialOwner()).ownerUserId()).isEqualTo(owner);
        assertThat(ProjectMemberResponse.from(members.findByProjectIdAndUserId(p.getId(),owner).orElseThrow()).role()).isEqualTo(ProjectRole.MANAGER);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='5' AND success",Integer.class)).isEqualTo(1);
    }
    @Test void incompleteOwnerTransactionCannotCommit() {
        UUID owner=user("ACTIVE");
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> projects.saveAndFlush(new Project(owner,"Missing membership",null,null,null,null,null,null,null))))
                .isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThat(projects.count()).isZero();
    }
    @Test void failureAfterBothInsertsRollsBackBoth() {
        UUID owner=user("ACTIVE");
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> {
            Project p=projects.saveAndFlush(new Project(owner,"Rollback",null,null,null,null,null,null,null));
            members.saveAndFlush(new ProjectMember(p.getId(),owner,ProjectRole.MANAGER,null));
            throw new IllegalStateException("simulated failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(projects.count()).isZero(); assertThat(members.count()).isZero();
    }
    @Test void rejectsDuplicateMembership() {
        UUID owner=user("ACTIVE");Project p=project(owner);
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> members.saveAndFlush(new ProjectMember(p.getId(),owner,ProjectRole.MANAGER,null))))
                .isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
    }
    @ParameterizedTest @ValueSource(strings={"DELETE FROM project_members WHERE user_id = ?", "UPDATE project_members SET role='VIEWER' WHERE user_id = ?"})
    void databaseRejectsOwnerRemovalOrDemotionAtCommit(String sql) {
        UUID owner=user("ACTIVE");Project p=project(owner);
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> jdbc.update(sql,owner))).isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThat(access.getMemberAccess(p.getId(),owner).role()).isEqualTo(ProjectRole.MANAGER);
    }
    @Test void databaseValidatesResponsibilityAndReferenceIntegrity() {
        UUID owner=user("ACTIVE");Project p=project(owner);UUID candidate=user("ACTIVE");
        for (String role : List.of("MEMBER", "INVALID")) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,?)",p.getId(),candidate,role))
                    .isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO project_members(project_id,user_id,role,responsibility) VALUES (?,?,'VIEWER','QA_TESTER')",p.getId(),candidate))
                .isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,'MANAGER')",UUID.randomUUID(),candidate))
                .isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,'MANAGER')",p.getId(),UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id=?",owner)).isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE projects SET status='INVALID' WHERE id=?", "UPDATE projects SET name=' ' WHERE id=?", "UPDATE projects SET start_date='2026-05-02',due_date='2026-05-01' WHERE id=?", "UPDATE projects SET owner_membership_role='VIEWER' WHERE id=?"})
    void databaseRejectsInvalidProjectFields(String sql) {
        Project p=project(user("ACTIVE"));
        assertThatThrownBy(() -> jdbc.update(sql,p.getId())).isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
    }
    @Test void validatesCurrentStatusMembershipAndProjectScope() {
        UUID owner=user("ACTIVE"),candidate=user("ACTIVE");Project p=project(owner),other=project(candidate);
        assertThatThrownBy(() -> access.getMemberAccess(p.getId(),candidate)).isInstanceOf(ProjectAccessDeniedException.class);
        assertThatThrownBy(() -> access.getMemberAccess(UUID.randomUUID(),owner)).isInstanceOf(ProjectNotFoundException.class);
        jdbc.update("INSERT INTO project_members(project_id,user_id,role,responsibility) VALUES (?,?,'MEMBER','QA_TESTER')",p.getId(),candidate);
        access.requireEligibleVerifier(p.getId(),owner,candidate);
        assertThatThrownBy(() -> access.requireEligibleVerifier(p.getId(),candidate,candidate)).isInstanceOf(InvalidProjectMembershipException.class);
        jdbc.update("UPDATE users SET status='BLOCKED' WHERE id=?",candidate);
        assertThatThrownBy(() -> access.requireAssignableMember(p.getId(),candidate)).isInstanceOf(ProjectAccessDeniedException.class);
        assertThatThrownBy(() -> access.getMemberAccess(other.getId(),candidate)).isInstanceOf(ProjectAccessDeniedException.class);
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE id=?",p.getId());
        access.requireAction(p.getId(),owner,ProjectAction.VIEW_INTERNAL_WORK);
        assertThatThrownBy(() -> access.requireAction(p.getId(),owner,ProjectAction.CREATE_TASK)).isInstanceOf(ProjectArchivedException.class);
    }
    @Test void projectCreationWithoutPermissionLeavesNoRecords() {
        UUID owner=user("ACTIVE");
        assertThatThrownBy(() -> service.createProject(owner,new CreateProjectRequest("Project",null,null,null,null,null,null,null)))
                .isInstanceOf(com.eclectics.worksync.users.exception.OrganizationPermissionDeniedException.class);
        assertThat(projects.count()).isZero();assertThat(members.count()).isZero();
    }
    @Test void upgradesExistingV4SchemaWithoutLosingUsers() {
        String schema = "projects_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var source = jdbc.getDataSource();
        try {
            org.flywaydb.core.Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                    .target("4").load().migrate();
            UUID existing = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema + ".users(id,first_name,last_name,email,phone_number,password_hash) VALUES (?, 'Existing','User','existing@example.com','+254712345678','test-fixture')", existing);
            org.flywaydb.core.Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("SELECT id FROM " + schema + ".users", UUID.class)).isEqualTo(existing);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".project_members", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".flyway_schema_history WHERE version='5' AND success", Integer.class)).isEqualTo(1);
        } finally {
            // This schema is generated by this test in the disposable TEST_DB only.
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
    @Test void concurrentOwnerTransferAndManagerDemotionCannotBothCommit() throws Exception {
        UUID owner = user("ACTIVE"), nextOwner = user("ACTIVE");
        Project p = project(owner);
        members.saveAndFlush(new ProjectMember(p.getId(), nextOwner, ProjectRole.MANAGER, null));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var transfer = pool.submit(() -> attemptConcurrentChange(barrier,
                    "UPDATE projects SET owner_user_id=? WHERE id=?", nextOwner, p.getId()));
            var demotion = pool.submit(() -> attemptConcurrentChange(barrier,
                    "UPDATE project_members SET role='VIEWER' WHERE user_id=? AND project_id=?", nextOwner, p.getId()));
            int committed = (transfer.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
                    + (demotion.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertThat(committed).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM projects p JOIN project_members m ON m.project_id=p.id AND m.user_id=p.owner_user_id AND m.role='MANAGER' WHERE p.id=?", Integer.class, p.getId())).isEqualTo(1);
    }
    private boolean attemptConcurrentChange(java.util.concurrent.CyclicBarrier barrier, String sql, UUID user, UUID project) {
        try {
            tx().executeWithoutResult(t -> {
                jdbc.update(sql, user, project);
                try { barrier.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception e) { throw new IllegalStateException(e); }
            });
            return true;
        } catch (RuntimeException conflict) {
            return false;
        }
    }

}
