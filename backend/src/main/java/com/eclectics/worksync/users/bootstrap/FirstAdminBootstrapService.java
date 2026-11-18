package com.eclectics.worksync.users.bootstrap;

import com.eclectics.worksync.users.dto.CreateUserCommand;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.repository.UserRepository;
import jakarta.validation.Validator;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Not a Spring component: available only when explicitly imported by the operator CLI. */
public class FirstAdminBootstrapService {
    private final UserRepository repository;
    private final PasswordEncoder encoder;
    private final Validator validator;

    public FirstAdminBootstrapService(UserRepository repository, PasswordEncoder encoder, Validator validator) {
        this.repository = repository;
        this.encoder = encoder;
        this.validator = validator;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void create(String firstName, String lastName, String email, String phone, char[] password) {
        try {
            Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
            if (isolation != null && isolation != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalStateException("Bootstrap requires READ_COMMITTED isolation");
            }
            if (password == null || password.length < 12 || password.length > 128
                    || CharBuffer.wrap(password).isEmpty()
                    || StandardCharsets.UTF_8.encode(CharBuffer.wrap(password)).remaining() > 72
                    || CharBuffer.wrap(password).chars().allMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Password must have 12–128 characters and at most 72 UTF-8 bytes");
            }
            String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
            // Validate the existing account-field contract without retaining/logging rejected values.
            var fields = new CreateUserCommand(firstName, lastName, normalizedEmail, phone, "validation-only-placeholder");
            if (!validator.validate(fields).isEmpty()) {
                throw new IllegalArgumentException("Invalid administrator account fields");
            }
            repository.lockAccountLifecycle();
            if (repository.countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE) > 0) {
                throw new IllegalStateException("An active administrator already exists; no changes made");
            }
            if (repository.existsByRole(SystemRole.ADMIN)) {
                throw new IllegalStateException("An administrator already exists; use a separately reviewed recovery procedure");
            }
            if (repository.existsByEmail(normalizedEmail)) {
                throw new IllegalStateException("Email is already registered; bootstrap never promotes existing accounts");
            }
            String hash = encoder.encode(CharBuffer.wrap(password));
            User admin = User.initialAdministrator(firstName, lastName, normalizedEmail, phone, hash);
            if (!validator.validate(admin).isEmpty()) {
                throw new IllegalArgumentException("Invalid administrator account fields");
            }
            repository.saveAndFlush(admin);
            // Transaction commit occurs before the CLI can report success.
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }
}
