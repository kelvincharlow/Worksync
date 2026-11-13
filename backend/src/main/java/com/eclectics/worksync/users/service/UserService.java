package com.eclectics.worksync.users.service;

import com.eclectics.worksync.users.dto.AuthUserDetails;
import com.eclectics.worksync.users.dto.CreateUserCommand;
import com.eclectics.worksync.users.dto.UpdateProfileRequest;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.exception.DuplicateEmailException;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.repository.UserRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared Java boundary for Users operations inside the monolith.
 * Auth and future controllers inject this Spring bean rather than accessing UserRepository.
 * UserResponse contains safe account data; AuthUserDetails is credential-bearing and internal only.
 * Callers must enforce current-user/admin authorization;
 * the status-changing methods below do not authenticate or authorize their caller.
 */
@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository repository;
    private final Validator validator;

    public UserService(UserRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /**
     * Creates a USER/PENDING_APPROVAL account from trusted Auth input.
     * The hash is already encoded by Auth; normalization applies only to email.
     * Validation happens here too because Java callers bypass HTTP request validation.
     */
    @Transactional
    public UserResponse createPendingUser(CreateUserCommand command) {
        Objects.requireNonNull(command, "command is required");
        var normalized = new CreateUserCommand(
                command.firstName(), command.lastName(), normalizeEmail(command.email()),
                command.phoneNumber(), command.passwordHash());
        validate(normalized);
        if (repository.existsByEmail(normalized.email())) {
            throw new DuplicateEmailException();
        }
        var user = new User(normalized.firstName(), normalized.lastName(), normalized.email(),
                normalized.phoneNumber(), normalized.passwordHash());
        // The unique index remains authoritative for concurrent registrations.
        // Let database failures escape for rollback; translate outside the transaction later.
        return UserResponse.from(repository.saveAndFlush(user));
    }

    /** Returns safe account details or throws UserNotFoundException. */
    public UserResponse getUserById(UUID userId) {
        return UserResponse.from(requireUser(userId));
    }

    /** Safe internal lookup only; this is not an authentication credential lookup. */
    public UserResponse getUserByEmail(String email) {
        String normalized = normalizeEmail(email);
        var violations = validator.validateValue(CreateUserCommand.class, "email", normalized);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return repository.findByEmail(normalized)
                .map(UserResponse::from)
                .orElseThrow(UserNotFoundException::new);
    }

    /**
     * Trusted Auth-only Java lookup; never expose this result through an HTTP endpoint.
     * Returns the stored hash and current state without verifying passwords or deciding access.
     */
    @Transactional(readOnly = true)
    public AuthUserDetails findForAuthentication(String email) {
        String normalized = normalizeEmail(email);
        var violations = validator.validateValue(CreateUserCommand.class, "email", normalized);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        User user = repository.findByEmail(normalized).orElseThrow(UserNotFoundException::new);
        return new AuthUserDetails(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getRole(), user.getStatus());
    }

    /**
     * Trusted Auth-only persistence after Auth has validated the reset workflow and encoded the password.
     * Does not verify reset codes, decide reset eligibility, hash passwords or invalidate tokens.
     * Never log arguments or expose this method through an unauthenticated controller.
     */
    @Transactional
    public void resetPassword(String email, String newPasswordHash) {
        String normalized = normalizeEmail(email);
        var emailViolations = validator.validateValue(CreateUserCommand.class, "email", normalized);
        if (!emailViolations.isEmpty()) {
            throw new ConstraintViolationException(emailViolations);
        }
        var hashViolations = validator.validateValue(User.class, "passwordHash", newPasswordHash);
        if (!hashViolations.isEmpty()) {
            throw new ConstraintViolationException(hashViolations);
        }
        User user = repository.findByEmail(normalized).orElseThrow(UserNotFoundException::new);
        user.changePasswordHash(newPasswordHash);
        repository.saveAndFlush(user);
    }

    /**
     * Replaces basic profile fields only. The caller must authorize access to userId.
     * Email, credentials, role and account status cannot be changed by this request.
     */
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        Objects.requireNonNull(request, "request is required");
        validate(request);
        User user = requireUser(userId);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        // Flush lifecycle callbacks so the response includes the new updatedAt value.
        return UserResponse.from(repository.saveAndFlush(user));
    }

    // Internal administrator operations only. Method names do not enforce authorization.
    // The future secured AdminUserController must authorize administrators before calling these.
    /** Approves a pending registration; repeated approval is an invalid transition. */
    @Transactional
    public UserResponse approveUser(UUID userId) {
        User user = requireUser(userId);
        user.approve();
        return UserResponse.from(repository.saveAndFlush(user));
    }

    /** Rejects a pending registration without changing its role or credentials. */
    @Transactional
    public UserResponse rejectUser(UUID userId) {
        User user = requireUser(userId);
        user.reject();
        return UserResponse.from(repository.saveAndFlush(user));
    }

    /** Blocks an active account; this cannot be used to reject a pending registration. */
    @Transactional
    public UserResponse blockUser(UUID userId) {
        User user = requireUser(userId);
        user.block();
        return UserResponse.from(repository.saveAndFlush(user));
    }

    /** Restores a blocked account to ACTIVE; this cannot approve pending/rejected users. */
    @Transactional
    public UserResponse unblockUser(UUID userId) {
        User user = requireUser(userId);
        user.unblock();
        return UserResponse.from(repository.saveAndFlush(user));
    }

    /**
     * Internal administrator listing; inherits the class's read-only transaction.
     * Page.map keeps pagination metadata while replacing entities with safe responses.
     */
    public Page<UserResponse> getUsersByStatus(AccountStatus status, Pageable pageable) {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(pageable, "pageable is required");
        return repository.findByStatus(status, pageable).map(UserResponse::from);
    }

    /** Internal administrator listing without a status filter; caller authorization is still required. */
    public Page<UserResponse> getUsers(Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is required");
        return repository.findAll(pageable).map(UserResponse::from);
    }

    private User requireUser(UUID userId) {
        Objects.requireNonNull(userId, "userId is required");
        return repository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private static String normalizeEmail(String email) {
        // Matches User's existing normalization; leave null for @NotBlank to reject.
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private <T> void validate(T input) {
        var violations = validator.validate(input);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
