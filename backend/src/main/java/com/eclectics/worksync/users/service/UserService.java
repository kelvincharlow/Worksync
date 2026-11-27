package com.eclectics.worksync.users.service;

import com.eclectics.worksync.users.dto.AuthUserDetails;
import com.eclectics.worksync.users.dto.CreateUserCommand;
import com.eclectics.worksync.users.dto.UpdateProfileRequest;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.exception.AdministratorAccessDeniedException;
import com.eclectics.worksync.users.exception.ProtectedAdministratorOperationException;
import com.eclectics.worksync.users.exception.DuplicateEmailException;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import com.eclectics.worksync.users.dto.UpdatePermissionsRequest;
import com.eclectics.worksync.users.entity.OrganizationPermission;
import com.eclectics.worksync.users.entity.UserPermission;
import com.eclectics.worksync.users.repository.UserPermissionRepository;
import com.eclectics.worksync.users.exception.InvalidPermissionRequestException;
import com.eclectics.worksync.users.exception.InvalidPermissionTargetException;
import com.eclectics.worksync.users.exception.OrganizationPermissionDeniedException;
import com.eclectics.worksync.users.exception.PermissionTransactionRequiredException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Connection;

/**
 * Shared Java boundary for Users operations inside the monolith.
 * Auth and future controllers inject this Spring bean rather than accessing UserRepository.
 * UserResponse contains safe account data; AuthUserDetails is credential-bearing and internal only.
 * Callers must enforce current-user/admin authorization;
 * actor-aware administrator methods also recheck current database authorization under the lifecycle lock.
 */
@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository repository;
    private final UserPermissionRepository permissions;
    private final Validator validator;
    private final EntityManager entityManager;

    public UserService(UserRepository repository, Validator validator, EntityManager entityManager, UserPermissionRepository permissions) {
        this.repository = repository;
        this.permissions = permissions;
        this.validator = validator;
        this.entityManager = entityManager;
    }

    /**
     * Creates a USER/PENDING_APPROVAL account from trusted Auth input.
     * The hash is already encoded by Auth; normalization applies only to email.
     * Validation happens here too because Java callers bypass HTTP request validation.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        return response(repository.saveAndFlush(user));
    }

    /** Returns safe account details or throws UserNotFoundException. */
    public UserResponse getUserById(UUID userId) {
        return response(requireUser(userId));
    }

    /** Safe internal lookup only; this is not an authentication credential lookup. */
    public UserResponse getUserByEmail(String email) {
        String normalized = normalizeEmail(email);
        var violations = validator.validateValue(CreateUserCommand.class, "email", normalized);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return repository.findByEmail(normalized)
                .map(this::response)
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        lockAccountLifecycle();
        User user = repository.findByEmail(normalized).orElseThrow(UserNotFoundException::new);
        entityManager.refresh(user);
        user.changePasswordHash(newPasswordHash);
        repository.saveAndFlush(user);
    }

    /**
     * Replaces basic profile fields only. The caller must authorize access to userId.
     * Email, credentials, role and account status cannot be changed by this request.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        Objects.requireNonNull(request, "request is required");
        validate(request);
        lockAccountLifecycle();
        User user = requireLifecycleUser(userId);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        // Flush lifecycle callbacks so the response includes the new updatedAt value.
        return response(repository.saveAndFlush(user));
    }

    // Trusted internal one-argument methods retain existing lifecycle behavior.
    // HTTP workflows use actor-aware overloads and Auth's existing secured route chain.
    // Every update uses the same transaction lock, preventing stale profile/password writes
    // from overwriting a concurrently changed role/status in Hibernate's full-row updates.
    /** Approves a pending registration; repeated approval is an invalid transition. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse approveUser(UUID userId) {
        lockAccountLifecycle();
        User user = requireLifecycleUser(userId);
        user.approve();
        return response(repository.saveAndFlush(user));
    }

    /** Rejects a pending registration without changing its role or credentials. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse rejectUser(UUID userId) {
        lockAccountLifecycle();
        User user = requireLifecycleUser(userId);
        user.reject();
        return response(repository.saveAndFlush(user));
    }

    /** Blocks an active account; this cannot be used to reject a pending registration. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse blockUser(UUID userId) {
        lockAccountLifecycle();
        User user = requireLifecycleUser(userId);
        if (user.getStatus() == AccountStatus.ACTIVE && user.getRole() == SystemRole.ADMIN
                && repository.countByRoleAndStatus(SystemRole.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new ProtectedAdministratorOperationException();
        }
        user.block();
        return response(repository.saveAndFlush(user));
    }

    /** Restores a blocked account to ACTIVE; this cannot approve pending/rejected users. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse unblockUser(UUID userId) {
        lockAccountLifecycle();
        User user = requireLifecycleUser(userId);
        user.unblock();
        return response(repository.saveAndFlush(user));
    }

    /** Auth's principal supplies actorId; recheck authorization after acquiring the shared lock. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse approveUser(UUID actorId, UUID userId) {
        requireActiveAdministrator(actorId);
        return approveUser(userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse rejectUser(UUID actorId, UUID userId) {
        requireActiveAdministrator(actorId);
        return rejectUser(userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse blockUser(UUID actorId, UUID userId) {
        requireActiveAdministrator(actorId);
        if (actorId.equals(userId)) {
            throw new ProtectedAdministratorOperationException();
        }
        return blockUser(userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse unblockUser(UUID actorId, UUID userId) {
        requireActiveAdministrator(actorId);
        return unblockUser(userId);
    }

    /** Complete direct-grant replacement; same lock/order as account lifecycle mutations. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse updatePermissions(UUID actorUserId, UUID targetUserId, UpdatePermissionsRequest request) {
        Objects.requireNonNull(request, "request is required");
        validate(request);
        if (new HashSet<>(request.permissions()).size() != request.permissions().size()) {
            throw new InvalidPermissionRequestException();
        }
        Set<OrganizationPermission> desired = Set.copyOf(request.permissions());
        requireActiveAdministrator(actorUserId); // lock first, refresh actor, then refresh target
        User target = requireLifecycleUser(targetUserId);
        Set<OrganizationPermission> stored = permissions.findPermissionsByUserId(targetUserId);
        Set<OrganizationPermission> additions = new HashSet<>(desired);
        additions.removeAll(stored);
        // Inactive accounts can be stripped of grants; only new grants require an ACTIVE target.
        if (!additions.isEmpty() && target.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidPermissionTargetException();
        }
        for (OrganizationPermission existing : stored) {
            if (!desired.contains(existing)) permissions.deleteGrant(targetUserId, existing);
        }
        for (OrganizationPermission addition : additions) {
            permissions.save(new UserPermission(targetUserId, addition, actorUserId));
        }
        permissions.flush();
        return response(target);
    }

    /** Stored grants for trusted callers; not effective authorization and not a new HTTP endpoint. */
    public Set<OrganizationPermission> getGrantedPermissions(UUID userId) {
        requireUser(userId);
        return Set.copyOf(permissions.findPermissionsByUserId(userId));
    }

    /** Current, serialized read for display/decisions; its transaction ends on return if called alone.
     * Writes must use requirePermissionForWrite inside their own transaction instead.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = false)
    public boolean hasPermission(UUID userId, OrganizationPermission permission) {
        Objects.requireNonNull(permission, "permission is required");
        lockAccountLifecycle();
        User user = requireLifecycleUser(userId);
        return user.getStatus() == AccountStatus.ACTIVE
                && permissions.findPermissionsByUserId(userId).contains(permission);
    }

    /** Trusted cross-module write guard. MUST join the caller's writable READ_COMMITTED transaction.
     * Acquire before Projects/Work Items row locks. The Users lock stays held until the OUTER commit.
     * Refresh account state and query scalar grants after locking; never trust JWT/DTO/cache snapshots.
     * MANDATORY prevents checking in a short separate transaction then writing after revocation.
     */
    @Transactional(propagation = Propagation.MANDATORY, isolation = Isolation.READ_COMMITTED, readOnly = false)
    public void requirePermissionForWrite(UUID userId, OrganizationPermission permission) {
        Objects.requireNonNull(permission, "permission is required");
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED).equals(
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new PermissionTransactionRequiredException();
        }
        lockAccountLifecycle();
        User user = requireLifecycleUser(userId);
        if (user.getStatus() != AccountStatus.ACTIVE
                || !permissions.findPermissionsByUserId(userId).contains(permission)) {
            throw new OrganizationPermissionDeniedException();
        }
    }

    private UserResponse response(User user) {
        return UserResponse.from(user, permissions.findPermissionsByUserId(user.getId()));
    }

    private void lockAccountLifecycle() {
        // Joining an outer snapshot transaction could make the count stale even after locking.
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (isolation != null && isolation != Connection.TRANSACTION_READ_COMMITTED) {
            throw new ProtectedAdministratorOperationException();
        }
        repository.lockAccountLifecycle();
    }

    private User requireLifecycleUser(UUID userId) {
        User user = requireUser(userId);
        // Refresh cached entities if a trusted Java caller already loaded them in this transaction.
        entityManager.refresh(user);
        return user;
    }

    private void requireActiveAdministrator(UUID actorId) {
        lockAccountLifecycle();
        User actor = requireLifecycleUser(actorId);
        if (actor.getRole() != SystemRole.ADMIN || actor.getStatus() != AccountStatus.ACTIVE) {
            throw new AdministratorAccessDeniedException();
        }
    }

    /**
     * Internal administrator listing; inherits the class's read-only transaction.
     * Page.map keeps pagination metadata while replacing entities with safe responses.
     */
    public Page<UserResponse> getUsersByStatus(AccountStatus status, Pageable pageable) {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(pageable, "pageable is required");
        return responsePage(repository.findByStatus(status, pageable));
    }

    /** Internal administrator listing without a status filter; caller authorization is still required. */
    public Page<UserResponse> getUsers(Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is required");
        return responsePage(repository.findAll(pageable));
    }

    /** Only the current page's IDs are queried; no grant query is needed for an empty page. */
    private Page<UserResponse> responsePage(Page<User> page) {
        if (page.isEmpty()) return page.map(UserResponse::from);
        var userIds = page.getContent().stream().map(User::getId).toList();
        Map<UUID, Set<OrganizationPermission>> byUser = new HashMap<>();
        for (var grant : permissions.findPermissionsForUsers(userIds)) {
            byUser.computeIfAbsent(grant.getUserId(), id -> EnumSet.noneOf(OrganizationPermission.class))
                    .add(grant.getPermission());
        }
        return page.map(user -> UserResponse.from(user, byUser.getOrDefault(user.getId(), Set.of())));
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
