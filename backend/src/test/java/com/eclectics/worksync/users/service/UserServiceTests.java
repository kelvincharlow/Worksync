package com.eclectics.worksync.users.service;

import com.eclectics.worksync.users.dto.CreateUserCommand;
import com.eclectics.worksync.users.dto.UpdateProfileRequest;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.exception.DuplicateEmailException;
import com.eclectics.worksync.users.exception.InvalidAccountStatusTransitionException;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.repository.UserRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {
    private static final String HASH_FIXTURE = "encoded-hash-fixture";
    private static ValidatorFactory validatorFactory;
    @Mock UserRepository repository;
    @Mock jakarta.persistence.EntityManager entityManager;
    private UserService service;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        service = new UserService(repository, validatorFactory.getValidator(), entityManager);
    }

    @Test
    void createsPendingUserWithNormalizedEmailAndUnchangedHash() {
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertThat(user.getPasswordHash()).isEqualTo(HASH_FIXTURE);
            // Simulate values normally assigned by JPA, without needing a database.
            assignPersistenceFields(user);
            return user;
        });

        var response = service.createPendingUser(command("  Jane@Example.COM  "));

        verify(repository).existsByEmail("jane@example.com");
        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.role()).isEqualTo(SystemRole.USER);
        assertThat(response.status()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void rejectsExistingEmailBeforeSaving() {
        when(repository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createPendingUser(command(" JANE@EXAMPLE.COM ")))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Email is already registered");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void databaseFailureEscapesRatherThanBeingSwallowedOrRetried() {
        var failure = new DataIntegrityViolationException("Database conflict fixture");
        when(repository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.createPendingUser(command("jane@example.com")))
                .isSameAs(failure);
        verify(repository, times(1)).saveAndFlush(any());
    }

    @Test
    void getsUserById() {
        User user = persistedUser();
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));

        var response = service.getUserById(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("jane@example.com");
    }

    @Test
    void throwsWhenIdDoesNotExist() {
        assertThatThrownBy(() -> service.getUserById(UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class).hasMessage("User not found");
    }

    @Test
    void normalizesEmailForInternalLookup() {
        User user = persistedUser();
        when(repository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        var response = service.getUserByEmail("\tJANE@EXAMPLE.COM ");

        assertThat(response.id()).isEqualTo(user.getId());
        verify(repository).findByEmail("jane@example.com");
    }

    @Test
    void throwsWhenEmailDoesNotExist() {
        assertThatThrownBy(() -> service.getUserByEmail("missing@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updatesOnlyProfileFieldsAndPreservesAccountAndCredentials() {
        User user = persistedUser();
        // An existing ADMIN/ACTIVE user must not be reset to registration defaults.
        ReflectionTestUtils.setField(user, "role", SystemRole.ADMIN);
        ReflectionTestUtils.setField(user, "status", AccountStatus.ACTIVE);
        UUID id = user.getId();
        Instant createdAt = user.getCreatedAt();
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        var response = service.updateProfile(id,
                new UpdateProfileRequest("Janet", "Smith", "+254700000001"));

        assertThat(response.firstName()).isEqualTo("Janet");
        assertThat(response.lastName()).isEqualTo("Smith");
        assertThat(response.phoneNumber()).isEqualTo("+254700000001");
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.role()).isEqualTo(SystemRole.ADMIN);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(user.getPasswordHash()).isEqualTo(HASH_FIXTURE);
    }

    @Test
    void cannotUpdateMissingUser() {
        assertThatThrownBy(() -> service.updateProfile(UUID.randomUUID(),
                new UpdateProfileRequest("Jane", "Doe", "+254712345678")))
                .isInstanceOf(UserNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void validatesCreationBeforeRepositoryAccess() {
        var invalid = new CreateUserCommand(" ", "Doe", "invalid", "0712345678", "");

        assertThatThrownBy(() -> service.createPendingUser(invalid))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void validatesProfileBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.updateProfile(UUID.randomUUID(),
                new UpdateProfileRequest(null, "Doe", "0712345678")))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void validatesEmailLookupBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.getUserByEmail(null))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @CsvSource({
            "approve, PENDING_APPROVAL, ACTIVE",
            "reject, PENDING_APPROVAL, REJECTED",
            "block, ACTIVE, BLOCKED",
            "unblock, BLOCKED, ACTIVE"
    })
    void changesStatusAndPreservesAccountFields(String operation,
            AccountStatus initialStatus, AccountStatus expectedStatus) {
        User user = userInStatus(initialStatus);
        ReflectionTestUtils.setField(user, "role", SystemRole.ADMIN);
        UUID id = user.getId();
        Instant createdAt = user.getCreatedAt();
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        if ("block".equals(operation)) {
            // Successful administrator blocking requires another active administrator.
            when(repository.countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE)).thenReturn(2L);
        }
        var response = changeStatus(operation, id);

        assertThat(response.status()).isEqualTo(expectedStatus);
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.role()).isEqualTo(SystemRole.ADMIN);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(user.getPasswordHash()).isEqualTo(HASH_FIXTURE);
        verify(repository).saveAndFlush(user);
    }

    @ParameterizedTest
    @CsvSource({
            "approve, ACTIVE",
            "reject, ACTIVE",
            "block, PENDING_APPROVAL",
            "unblock, ACTIVE"
    })
    void rejectsInvalidStatusChangeWithoutSaving(String operation, AccountStatus initialStatus) {
        User user = userInStatus(initialStatus);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> changeStatus(operation, user.getId()))
                .isInstanceOf(InvalidAccountStatusTransitionException.class);
        assertThat(user.getStatus()).isEqualTo(initialStatus);
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject", "block", "unblock"})
    void cannotChangeStatusForMissingUser(String operation) {
        assertThatThrownBy(() -> changeStatus(operation, UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void mapsStatusListingToSafeResponsesAndKeepsPageMetadata() {
        User user = userInStatus(AccountStatus.ACTIVE);
        var request = PageRequest.of(1, 1, Sort.by("email"));
        when(repository.findByStatus(AccountStatus.ACTIVE, request))
                .thenReturn(new PageImpl<>(List.of(user), request, 3));

        var result = service.getUsersByStatus(AccountStatus.ACTIVE, request);

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getSort()).isEqualTo(request.getSort());
        assertThat(result.getContent()).containsExactly(UserResponse.from(user));
        verify(repository).findByStatus(AccountStatus.ACTIVE, request);
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void authenticationLookupReturnsStoredDetailsForAuthToEvaluate(AccountStatus status) {
        User user = userInStatus(status);
        ReflectionTestUtils.setField(user, "role", SystemRole.ADMIN);
        when(repository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        var details = service.findForAuthentication("jane@example.com");

        assertThat(details.id()).isEqualTo(user.getId());
        assertThat(details.email()).isEqualTo("jane@example.com");
        assertThat(details.passwordHash()).isEqualTo(HASH_FIXTURE);
        assertThat(details.role()).isEqualTo(SystemRole.ADMIN);
        assertThat(details.status()).isEqualTo(status);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void authenticationLookupNormalizesWhitespaceAndCase() {
        User user = persistedUser();
        when(repository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        var details = service.findForAuthentication("\t JANE@EXAMPLE.COM \n");

        assertThat(details.id()).isEqualTo(user.getId());
        verify(repository).findByEmail("jane@example.com");
    }

    @Test
    void authenticationLookupThrowsForUnknownEmail() {
        assertThatThrownBy(() -> service.findForAuthentication("missing@example.com"))
                .isInstanceOf(UserNotFoundException.class).hasMessage("User not found");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-an-email", "jane@"})
    void authenticationLookupRejectsInvalidEmailBeforeRepositoryAccess(String email) {
        assertThatThrownBy(() -> service.findForAuthentication(email))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void resetsOnlyHashAndLeavesEligibilityDecisionsToAuth(AccountStatus status) {
        User user = userInStatus(status);
        ReflectionTestUtils.setField(user, "role", SystemRole.ADMIN);
        UserResponse originalProfile = UserResponse.from(user);
        String newHash = "new-encoded-hash-fixture";
        when(repository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        service.resetPassword("\t JANE@EXAMPLE.COM \n", newHash);

        assertThat(user.getPasswordHash()).isEqualTo(newHash);
        // Mocked persistence does not run JPA timestamp callbacks; all profile fields are unchanged.
        assertThat(UserResponse.from(user)).isEqualTo(originalProfile);
        assertThat(service.findForAuthentication("jane@example.com").passwordHash()).isEqualTo(newHash);
        verify(repository).saveAndFlush(user);
    }

    @Test
    void passwordResetThrowsForMissingUser() {
        assertThatThrownBy(() -> service.resetPassword("missing@example.com", "new-encoded-hash-fixture"))
                .isInstanceOf(UserNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void passwordResetRejectsInvalidHashBeforeRepositoryAccess(String hash) {
        assertThatThrownBy(() -> service.resetPassword("jane@example.com", hash))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void passwordResetEnforcesHashStorageLimit() {
        assertThatThrownBy(() -> service.resetPassword("jane@example.com", "x".repeat(513)))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-an-email"})
    void passwordResetRejectsInvalidEmailBeforeRepositoryAccess(String email) {
        assertThatThrownBy(() -> service.resetPassword(email, "new-encoded-hash-fixture"))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    private User userInStatus(AccountStatus status) {
        User user = persistedUser();
        switch (status) {
            case PENDING_APPROVAL -> { }
            case ACTIVE -> user.approve();
            case REJECTED -> user.reject();
            case BLOCKED -> {
                user.approve();
                user.block();
            }
        }
        return user;
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

    private CreateUserCommand command(String email) {
        return new CreateUserCommand("Jane", "Doe", email, "+254712345678", HASH_FIXTURE);
    }

    private User persistedUser() {
        User user = new User("Jane", "Doe", "jane@example.com", "+254712345678", HASH_FIXTURE);
        assignPersistenceFields(user);
        return user;
    }

    private void assignPersistenceFields(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-11-01T00:00:00Z"));
        ReflectionTestUtils.setField(user, "updatedAt", user.getCreatedAt());
    }
}
