package com.eclectics.worksync.users.bootstrap;

import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstAdminBootstrapServiceTests {
    private static ValidatorFactory validators;
    @Mock UserRepository repository;
    @Mock PasswordEncoder encoder;
    FirstAdminBootstrapService service;

    @BeforeAll static void open() { validators = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { validators.close(); }
    @BeforeEach void setup() { service = new FirstAdminBootstrapService(repository, encoder, validators.getValidator()); }

    @Test
    void acquiresLockBeforeCheckingAndSaving() {
        when(encoder.encode(any())).thenReturn("encoded-hash-fixture");
        char[] password = UUID.randomUUID().toString().toCharArray();
        service.create("First", "Last", " ADMIN@EXAMPLE.COM ", "+254712345678", password);
        var order = inOrder(repository);
        order.verify(repository).lockAccountLifecycle();
        order.verify(repository).countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE);
        order.verify(repository).existsByRole(SystemRole.ADMIN);
        order.verify(repository).existsByEmail("admin@example.com");
        order.verify(repository).saveAndFlush(argThat(user -> user.getRole() == SystemRole.ADMIN
                && user.getStatus() == AccountStatus.ACTIVE && user.getEmail().equals("admin@example.com")));
        assertThat(password).containsOnly('\0');
    }

    @Test
    void refusesAnExistingActiveAdminWithoutEncoding() {
        when(repository.countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE)).thenReturn(1L);
        assertThatThrownBy(() -> create()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(encoder);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveAdministratorDoesNotPermitRecoveryThroughBootstrap() {
        when(repository.existsByRole(SystemRole.ADMIN)).thenReturn(true);
        assertThatThrownBy(() -> create()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(encoder);
    }

    @Test
    void existingUserIsNeverPromoted() {
        when(repository.existsByEmail("admin@example.com")).thenReturn(true);
        assertThatThrownBy(() -> create()).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "            "})
    void invalidPasswordsAreRejectedBeforeDatabaseAccess(String input) {
        char[] password = input == null ? null : input.toCharArray();
        assertThatThrownBy(() -> service.create("First", "Last", "admin@example.com", "+254712345678", password))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, encoder);
        if (password != null && password.length > 0) assertThat(password).containsOnly('\0');
    }

    @Test
    void bcryptUtf8LimitIsEnforcedWithoutExposingInput() {
        char[] password = "€".repeat(30).toCharArray();
        assertThatThrownBy(() -> service.create("First", "Last", "admin@example.com", "+254712345678", password))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(password).containsOnly('\0');
        verifyNoInteractions(repository, encoder);
    }

    @Test
    void invalidAccountFieldsAreRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.create("", "Last", "invalid", "bad", UUID.randomUUID().toString().toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, encoder);
    }

    private void create() {
        service.create("First", "Last", "admin@example.com", "+254712345678", UUID.randomUUID().toString().toCharArray());
    }
}
