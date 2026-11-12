package com.eclectics.worksync.users.repository;

import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses the existing disposable PostgreSQL test database; no H2 schema substitute. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTests {
    // Persistence-only fixture; not a real password or a usable authentication hash.
    private static final String HASH_FIXTURE = "encoded-hash-fixture";

    @Autowired UserRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsAllFieldsWithDefaultsAndNormalizedEmail() {
        User saved = repository.saveAndFlush(user("  Jane.Doe@Example.COM  "));
        UUID id = saved.getId();
        entityManager.clear();

        User loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getFirstName()).isEqualTo("Jane");
        assertThat(loaded.getLastName()).isEqualTo("Doe");
        assertThat(loaded.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(loaded.getPhoneNumber()).isEqualTo("+254712345678");
        assertThat(loaded.getPasswordHash()).isEqualTo(HASH_FIXTURE);
        assertThat(loaded.getRole()).isEqualTo(SystemRole.USER);
        assertThat(loaded.getStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
    }

    @Test
    void emailLookupsIgnoreCaseAndSurroundingSpaces() {
        User saved = repository.saveAndFlush(user("jane.doe@example.com"));
        entityManager.clear();

        assertThat(repository.findByEmail("  JANE.DOE@EXAMPLE.COM  "))
                .get().extracting(User::getId).isEqualTo(saved.getId());
        assertThat(repository.existsByEmail("  JANE.DOE@EXAMPLE.COM  ")).isTrue();
        assertThat(repository.findByEmail("missing@example.com")).isEmpty();
        assertThat(repository.existsByEmail("missing@example.com")).isFalse();
    }

    @Test
    void rejectsDuplicateEmailsAfterNormalization() {
        repository.saveAndFlush(user("jane.doe@example.com"));

        assertThatThrownBy(() -> repository.saveAndFlush(user("  JANE.DOE@EXAMPLE.COM  ")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMixedCaseDuplicateEvenWhenJavaIsBypassed() {
        repository.saveAndFlush(user("jane.doe@example.com"));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (first_name, last_name, email, phone_number, password_hash)
                VALUES ('Jane', 'Doe', 'JANE.DOE@EXAMPLE.COM', '+254712345678', ?)
                """, HASH_FIXTURE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseDefaultsApplyToDirectInserts() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, first_name, last_name, email, phone_number, password_hash)
                VALUES (?, 'Jane', 'Doe', 'direct@example.com', '+254712345678', ?)
                """, id, HASH_FIXTURE);

        User loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.getRole()).isEqualTo(SystemRole.USER);
        assertThat(loaded.getStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void listsUsersByStatusWithPagination() {
        repository.saveAndFlush(user("b@example.com"));
        repository.saveAndFlush(user("a@example.com"));
        PageRequest request = PageRequest.of(0, 1, Sort.by("email"));

        var page = repository.findByStatus(AccountStatus.PENDING_APPROVAL, request);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(User::getEmail).containsExactly("a@example.com");
        assertThat(repository.findByStatus(AccountStatus.ACTIVE, request)).isEmpty();
    }

    @Test
    void updateChangesTimestampAndKeepsCreationTime() {
        User saved = repository.saveAndFlush(user("jane.doe@example.com"));
        UUID id = saved.getId();
        entityManager.clear();
        Instant createdAt = repository.findById(id).orElseThrow().getCreatedAt();
        jdbc.update("UPDATE users SET updated_at = created_at - INTERVAL '1 day' WHERE id = ?", id);
        entityManager.clear();
        User loaded = repository.findById(id).orElseThrow();
        Instant oldUpdatedAt = loaded.getUpdatedAt();

        loaded.setFirstName("Janet");
        repository.flush();
        entityManager.clear();

        User updated = repository.findById(id).orElseThrow();
        assertThat(updated.getFirstName()).isEqualTo("Janet");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(oldUpdatedAt);
    }

    private User user(String email) {
        return new User("Jane", "Doe", email, "+254712345678", HASH_FIXTURE);
    }
}
