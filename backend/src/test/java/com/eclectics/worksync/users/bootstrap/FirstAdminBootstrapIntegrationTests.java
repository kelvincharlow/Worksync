package com.eclectics.worksync.users.bootstrap;

import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.repository.UserRepository;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

/** Disposable PostgreSQL only. Tests invoke the isolated service, never the production CLI. */
@SpringBootTest(classes = FirstAdminBootstrap.BootstrapConfiguration.class)
@ActiveProfiles("test")
// Test fixture setup only; the production CLI requires schema migrations already applied.
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
class FirstAdminBootstrapIntegrationTests {
    @Autowired FirstAdminBootstrapService service;
    @Autowired UserRepository repository;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach @AfterEach
    void cleanup() {
        repository.deleteAllInBatch();
    }

    private void create(String email) {
        service.create("First", "Admin", email, "+254712345678", UUID.randomUUID().toString().toCharArray());
    }

    @Test
    void createsCommittedAdminWithExistingEncoderAndCorrectFields() {
        String original = UUID.randomUUID().toString();
        char[] input = original.toCharArray();
        service.create("First", "Admin", " FIRST@EXAMPLE.COM ", "+254712345678", input);
        var saved = repository.findByEmail("first@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(SystemRole.ADMIN);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getFirstName()).isEqualTo("First");
        assertThat(saved.getLastName()).isEqualTo("Admin");
        assertThat(saved.getPhoneNumber()).isEqualTo("+254712345678");
        assertThat(saved.getPasswordHash().equals(original)).isFalse();
        assertThat(encoder.matches(original, saved.getPasswordHash())).isTrue();
        assertThat(input).containsOnly('\0');
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'", Long.class))
                .isEqualTo(1L);
        assertThat(context.getBeansOfType(JavaMailSender.class)).isEmpty();
    }

    @Test
    void rerunningCannotChangeOrCreateAnotherAdmin() {
        create("first@example.com");
        var before = repository.findByEmail("first@example.com").orElseThrow();
        String hash = before.getPasswordHash();
        UUID id = before.getId();
        assertThatThrownBy(() -> create("second@example.com")).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isEqualTo(1);
        var after = repository.findById(id).orElseThrow();
        assertThat(after.getPasswordHash().equals(hash)).isTrue();
    }

    @Test
    void inactiveAdminAlsoPreventsRebootstrap() {
        create("first@example.com");
        jdbc.update("UPDATE users SET status = 'BLOCKED'");
        assertThatThrownBy(() -> create("second@example.com")).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE)).isZero();
    }

    @Test
    void normalizedDuplicateEmailDoesNotPromoteOrOverwriteExistingUser() {
        var member = repository.saveAndFlush(new User("First", "Member", "member@example.com",
                "+254712345678", "existing-hash-fixture"));
        assertThatThrownBy(() -> create(" MEMBER@EXAMPLE.COM ")).isInstanceOf(IllegalStateException.class);
        var after = repository.findById(member.getId()).orElseThrow();
        assertThat(after.getRole()).isEqualTo(SystemRole.USER);
        assertThat(after.getStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(after.getPasswordHash().equals("existing-hash-fixture")).isTrue();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void creationRollsBackAtomicallyWithEnclosingTransaction() {
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            create("first@example.com");
            throw new IllegalStateException("controlled rollback fixture");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void snapshotTransactionIsRejected() {
        var tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> create("first@example.com")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void concurrentExecutionsCreateExactlyOneAdmin() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = pool.submit(() -> competingCreate("first@example.com", ready, start));
            Future<Boolean> second = pool.submit(() -> competingCreate("second@example.com", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE)).isEqualTo(1);
    }

    private boolean competingCreate(String email, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent fixture did not start");
        try {
            create(email);
            return true;
        } catch (IllegalStateException refused) {
            return false;
        }
    }
}
