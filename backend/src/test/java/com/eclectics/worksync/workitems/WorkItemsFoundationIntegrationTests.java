package com.eclectics.worksync.workitems;

import com.eclectics.worksync.projects.entity.Project;
import com.eclectics.worksync.projects.entity.ProjectMember;
import com.eclectics.worksync.projects.entity.ProjectRole;
import com.eclectics.worksync.projects.repository.ProjectMemberRepository;
import com.eclectics.worksync.projects.repository.ProjectRepository;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.workitems.entity.*;
import com.eclectics.worksync.workitems.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = WorkItemsFoundationIntegrationTests.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"debug=false", "logging.level.org.springframework=WARN"}
)
@ActiveProfiles({"test", "workitems-foundation-test"})
class WorkItemsFoundationIntegrationTests {

    @Configuration
    @Profile("workitems-foundation-test")
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {
        WorkItem.class, WorkItemLink.class, BugDetails.class, 
        BugVerification.class, Project.class, User.class
    })
    @EnableJpaRepositories(basePackageClasses = {
        WorkItemRepository.class, BugDetailsRepository.class,
        BugVerificationRepository.class, ProjectRepository.class, 
        ProjectMemberRepository.class
    })
    static class TestApplication {}

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired ProjectRepository projects;
    @Autowired ProjectMemberRepository members;
    @Autowired WorkItemRepository workItems;
    @Autowired BugDetailsRepository bugDetails;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    @BeforeEach
    @AfterEach
    void clean() {
        tx().executeWithoutResult(t -> {
            jdbc.update("DELETE FROM bug_verifications");
            jdbc.update("DELETE FROM bug_details");
            jdbc.update("DELETE FROM work_item_links");
            jdbc.update("DELETE FROM work_items");
            jdbc.update("DELETE FROM project_members");
            jdbc.update("DELETE FROM projects");
            jdbc.update("DELETE FROM users");
        });
    }

    UUID createUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, first_name, last_name, email, phone_number, password_hash, status) " +
            "VALUES (?, 'Test', 'User', ?, '+254712345678', 'test-fixture', 'ACTIVE')",
            id, email
        );
        return id;
    }

    Project createProject(UUID ownerId, String name) {
        return tx().execute(t -> {
            Project p = projects.saveAndFlush(
                new Project(ownerId, name, null, null, null, null, null, null, null)
            );
            members.saveAndFlush(new ProjectMember(p.getId(), ownerId, ProjectRole.MANAGER, null));
            return p;
        });
    }

    @Test
    @DisplayName("Persists work item successfully when linked to a valid project")
    void persistsWorkItemWithProjectFk() {
        UUID user = createUser("worker@example.com");
        Project p = createProject(user, "Alpha Project");

        WorkItem item = new WorkItem(
            null, p.getId(), WorkItemType.STORY,
            "User Onboarding", "Description", WorkItemPriority.HIGH,
            user, null, null
        );

        WorkItem saved = tx().execute(t -> workItems.saveAndFlush(item));

        assertThat(saved.getId()).isNotNull();
        assertThat(workItems.findByIdAndProjectId(saved.getId(), p.getId())).isPresent();
    }

    @Test
    @DisplayName("Database enforces foreign key: rejects work item with non-existent project")
    void databaseRejectsInvalidProjectFk() {
        UUID user = createUser("user2@example.com");
        UUID fakeProjectId = UUID.randomUUID();

        WorkItem item = new WorkItem(
            null, fakeProjectId, WorkItemType.TASK,
            "Orphan Task", null, null, user, null, null
        );

        assertThatThrownBy(() -> tx().executeWithoutResult(t -> workItems.saveAndFlush(item)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database enforces same-project parent constraint")
    void databaseRejectsParentFromDifferentProject() {
        UUID user = createUser("lead@example.com");
        Project projectA = createProject(user, "Project A");
        Project projectB = createProject(user, "Project B");

        WorkItem parentInA = tx().execute(t -> workItems.saveAndFlush(new WorkItem(
            null, projectA.getId(), WorkItemType.STORY,
            "Story in A", null, null, user, null, null
        )));

        WorkItem childInB = new WorkItem(
            null, projectB.getId(), WorkItemType.TASK,
            "Task in B", null, null, user, null, parentInA.getId()
        );

        assertThatThrownBy(() -> tx().executeWithoutResult(t -> workItems.saveAndFlush(childInB)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database rejects cross-project work item link")
    void databaseRejectsCrossProjectLink() {
        UUID user = createUser("linker@example.com");
        Project projectA = createProject(user, "Project A");
        Project projectB = createProject(user, "Project B");

        WorkItem itemA = tx().execute(t -> workItems.saveAndFlush(new WorkItem(
            null, projectA.getId(), WorkItemType.TASK, "Task A", null, null, user, null, null
        )));
        WorkItem itemB = tx().execute(t -> workItems.saveAndFlush(new WorkItem(
            null, projectB.getId(), WorkItemType.TASK, "Task B", null, null, user, null, null
        )));

        // Attempting to link items across different projects in work_item_links must fail
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO work_item_links (id, project_id, source_id, target_id, link_type) " +
            "VALUES (?, ?, ?, ?, 'BLOCKS')",
            UUID.randomUUID(), projectA.getId(), itemA.getId(), itemB.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database rejects attaching bug details to an ordinary Task or Story")
    void databaseRejectsBugDetailsOnNonBugItem() {
        UUID user = createUser("developer@example.com");
        Project p = createProject(user, "Core Project");

        WorkItem story = tx().execute(t -> workItems.saveAndFlush(new WorkItem(
            null, p.getId(), WorkItemType.STORY, "User Story", null, null, user, null, null
        )));

        // Attempting to attach bug_details to an item of type STORY must fail foreign key
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO bug_details (work_item_id, severity, affected_area, " +
            "reproduction_steps, expected_result, actual_result) " +
            "VALUES (?, 'HIGH', 'Checkout', 'Step 1', 'Success', 'Error')",
            story.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database rejects blank title constraint (btrim(title) <> '')")
    void databaseRejectsBlankTitle() {
        UUID user = createUser("dev@example.com");
        Project p = createProject(user, "Test Project");

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO work_items (id, project_id, type, title, status, priority, creator_id) " +
            "VALUES (?, ?, 'TASK', '    ', 'TODO', 'MEDIUM', ?)",
            UUID.randomUUID(), p.getId(), user
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database enforces: verifier cannot equal fixer in bug_details")
    void databaseRejectsVerifierEqualsFixer() {
        UUID user = createUser("qa@example.com");
        Project p = createProject(user, "Quality Project");

        WorkItem bug = tx().execute(t -> workItems.saveAndFlush(new WorkItem(
            null, p.getId(), WorkItemType.BUG,
            "Crash on checkout", null, null, user, null, null
        )));

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO bug_details (work_item_id, severity, affected_area, " +
            "reproduction_steps, expected_result, actual_result, fixer_id, verifier_id) " +
            "VALUES (?, 'HIGH', 'Checkout', 'Step 1', 'Success', 'Crash', ?, ?)",
            bug.getId(), user, user
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}