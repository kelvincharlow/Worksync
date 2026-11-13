package com.eclectics.worksync.users.service;

import com.eclectics.worksync.users.dto.CreateUserCommand;
import com.eclectics.worksync.users.dto.UpdateProfileRequest;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.exception.DuplicateEmailException;
import com.eclectics.worksync.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceIntegrationTests {
    @MockitoBean JavaMailSender mailSender;
    private static final String HASH_FIXTURE = "encoded-hash-fixture";
    @Autowired UserService service;
    @Autowired UserRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void createsAndFindsUserThroughRealRepository() {
        var created = service.createPendingUser(command());

        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(service.getUserById(created.id())).isEqualTo(created);
        assertThat(service.getUserByEmail(" SERVICE@EXAMPLE.COM ")).isEqualTo(created);
        assertThatThrownBy(() -> service.createPendingUser(command()))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void updateResponseContainsFlushedTimestampAndPreservesProtectedColumns() {
        var created = service.createPendingUser(command());
        jdbc.update("UPDATE users SET updated_at = created_at - INTERVAL '1 day' WHERE id = ?", created.id());
        entityManager.clear();
        var before = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", created.id());
        var oldUpdatedAt = repository.findById(created.id()).orElseThrow().getUpdatedAt();

        var updated = service.updateProfile(created.id(),
                new UpdateProfileRequest("Janet", "Smith", "+254700000001"));

        var after = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", created.id());
        for (String column : new String[]{"id", "email", "password_hash", "role", "status", "created_at"}) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(updated.firstName()).isEqualTo("Janet");
        assertThat(updated.lastName()).isEqualTo("Smith");
        assertThat(updated.phoneNumber()).isEqualTo("+254700000001");
        assertThat(updated.updatedAt()).isAfter(oldUpdatedAt);
        assertThat(after.get("updated_at")).isNotEqualTo(before.get("updated_at"));
    }

    @ParameterizedTest
    @CsvSource({
            "approve, PENDING_APPROVAL, ACTIVE",
            "reject, PENDING_APPROVAL, REJECTED",
            "block, ACTIVE, BLOCKED",
            "unblock, BLOCKED, ACTIVE"
    })
    void persistsStatusChangeAndUpdatedTimestampWithoutChangingProtectedFields(String operation,
            AccountStatus initialStatus, AccountStatus expectedStatus) {
        var created = service.createPendingUser(command());
        if (initialStatus == AccountStatus.ACTIVE || initialStatus == AccountStatus.BLOCKED) {
            service.approveUser(created.id());
        }
        if (initialStatus == AccountStatus.BLOCKED) {
            service.blockUser(created.id());
        }
        // Trusted fixture setup only; no role-promotion API is implemented.
        jdbc.update("""
                UPDATE users SET role = 'ADMIN', updated_at = created_at - INTERVAL '1 day'
                WHERE id = ?
                """, created.id());
        entityManager.clear();
        var before = repository.findById(created.id()).orElseThrow();
        var oldUpdatedAt = before.getUpdatedAt();
        var originalCreatedAt = before.getCreatedAt();

        var response = changeStatus(operation, created.id());
        entityManager.clear();
        var loaded = repository.findById(created.id()).orElseThrow();

        assertThat(response.status()).isEqualTo(expectedStatus);
        assertThat(loaded.getStatus()).isEqualTo(expectedStatus);
        assertThat(response.updatedAt()).isAfter(oldUpdatedAt);
        assertThat(loaded.getUpdatedAt()).isAfter(oldUpdatedAt);
        assertThat(loaded.getId()).isEqualTo(created.id());
        assertThat(loaded.getRole()).isEqualTo(SystemRole.ADMIN);
        assertThat(loaded.getEmail()).isEqualTo(created.email());
        assertThat(loaded.getPasswordHash()).isEqualTo(HASH_FIXTURE);
        assertThat(loaded.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(loaded.getFirstName()).isEqualTo(created.firstName());
        assertThat(loaded.getLastName()).isEqualTo(created.lastName());
        assertThat(loaded.getPhoneNumber()).isEqualTo(created.phoneNumber());
    }

    @Test
    void statusListingFiltersAndPaginatesPersistedUsers() {
        var first = service.createPendingUser(new CreateUserCommand(
                "Jane", "Doe", "a@example.com", "+254712345678", HASH_FIXTURE));
        var second = service.createPendingUser(new CreateUserCommand(
                "Jane", "Doe", "b@example.com", "+254712345678", HASH_FIXTURE));
        service.createPendingUser(new CreateUserCommand(
                "Jane", "Doe", "pending@example.com", "+254712345678", HASH_FIXTURE));
        service.approveUser(first.id());
        service.approveUser(second.id());
        entityManager.clear();
        var request = PageRequest.of(0, 1, Sort.by("email"));

        var page = service.getUsersByStatus(AccountStatus.ACTIVE, request);
        var next = service.getUsersByStatus(AccountStatus.ACTIVE, request.next());

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(UserResponse::email).containsExactly("a@example.com");
        assertThat(page.getContent()).extracting(UserResponse::status).containsExactly(AccountStatus.ACTIVE);
        assertThat(next.getContent()).extracting(UserResponse::email).containsExactly("b@example.com");
    }

    @Test
    void unfilteredListingPaginatesAcrossAccountStatuses() {
        var first = service.createPendingUser(new CreateUserCommand(
                "Jane", "Doe", "a@example.com", "+254712345678", HASH_FIXTURE));
        service.approveUser(first.id());
        service.createPendingUser(new CreateUserCommand(
                "Jane", "Doe", "b@example.com", "+254712345678", HASH_FIXTURE));
        entityManager.clear();
        var request = PageRequest.of(0, 1, Sort.by("email"));

        var page = service.getUsers(request);
        var next = service.getUsers(request.next());

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(UserResponse::status).containsExactly(AccountStatus.ACTIVE);
        assertThat(next.getContent()).extracting(UserResponse::status)
                .containsExactly(AccountStatus.PENDING_APPROVAL);
        assertThat(page.getContent()).extracting(UserResponse::email).containsExactly("a@example.com");
        assertThat(next.getContent()).extracting(UserResponse::email).containsExactly("b@example.com");
    }

    @Test
    void passwordResetPersistsNewHashForAuthWithoutChangingOtherAccountFields() {
        var created = service.createPendingUser(command());
        service.approveUser(created.id());
        service.blockUser(created.id());
        jdbc.update("""
                UPDATE users SET role = 'ADMIN', updated_at = created_at - INTERVAL '1 day'
                WHERE id = ?
                """, created.id());
        entityManager.clear();
        var before = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", created.id());
        var oldUpdatedAt = repository.findById(created.id()).orElseThrow().getUpdatedAt();
        String newHash = "new-encoded-hash-fixture";

        service.resetPassword(" SERVICE@EXAMPLE.COM ", newHash);
        entityManager.clear();

        var details = service.findForAuthentication("service@example.com");
        var profile = service.getUserById(created.id());
        var after = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", created.id());
        assertThat(details.passwordHash()).isEqualTo(newHash);
        assertThat(after.get("password_hash")).isEqualTo(newHash);
        assertThat(details.role()).isEqualTo(SystemRole.ADMIN);
        assertThat(details.status()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(profile.updatedAt()).isAfter(oldUpdatedAt);
        for (String column : new String[]{"id", "first_name", "last_name", "email", "phone_number",
                "role", "status", "created_at"}) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
    }

    private UserResponse changeStatus(String operation, UUID id) {
        return switch (operation) {
            case "approve" -> service.approveUser(id);
            case "reject" -> service.rejectUser(id);
            case "block" -> service.blockUser(id);
            case "unblock" -> service.unblockUser(id);
            default -> throw new IllegalArgumentException("Unknown test operation");
        };
    }

    private CreateUserCommand command() {
        return new CreateUserCommand("Jane", "Doe", "service@example.com", "+254712345678", HASH_FIXTURE);
    }
}
