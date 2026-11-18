package com.eclectics.worksync.users.repository;

import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.entity.SystemRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Users-domain persistence only. Other domains must call UserService instead. */
public interface UserRepository extends JpaRepository<User, UUID> {
    // Normalize lookup input too, so login cannot depend on email casing or surrounding spaces.
    @Query("select u from User u where lower(u.email) = lower(trim(:email))")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select (count(u) > 0) from User u where lower(u.email) = lower(trim(:email))")
    boolean existsByEmail(@Param("email") String email);

    // PostgreSQL transaction lock: shared by every Users update, released only at commit/rollback.
    // The two integers reserve the WorkSync Users lifecycle lock namespace.
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(1465075027, 1)", nativeQuery = true)
    int lockAccountLifecycle();

    boolean existsByRole(SystemRole role);

    long countByRoleAndStatus(SystemRole role, AccountStatus status);

    Page<User> findByStatus(AccountStatus status, Pageable pageable);
}
