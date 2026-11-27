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

    @Query("""
        select new com.eclectics.worksync.users.dto.UserSelection(u.id,u.firstName,u.lastName,u.email)
        from User u where u.status = com.eclectics.worksync.users.entity.AccountStatus.ACTIVE
        and (locate(:search,lower(concat(u.firstName,' ',u.lastName))) > 0 or locate(:search,lower(u.email)) > 0)
        """)
    Page<com.eclectics.worksync.users.dto.UserSelection> searchActive(@Param("search") String search, Pageable pageable);

    // Approved read-only coupling to V5 project_members(project_id,user_id). No Projects repository access.
    // Identical predicates in content/count keep exclusion and pagination totals correct in PostgreSQL.
    @Query(value = """
        SELECT u.id,u.first_name,u.last_name,u.email FROM users u
        WHERE u.status='ACTIVE'
        AND (strpos(lower(u.first_name || ' ' || u.last_name),:search)>0 OR strpos(lower(u.email),:search)>0)
        AND NOT EXISTS (SELECT 1 FROM project_members m WHERE m.project_id=:projectId AND m.user_id=u.id)
        ORDER BY lower(u.last_name),lower(u.first_name),u.id
        """, countQuery = """
        SELECT count(*) FROM users u WHERE u.status='ACTIVE'
        AND (strpos(lower(u.first_name || ' ' || u.last_name),:search)>0 OR strpos(lower(u.email),:search)>0)
        AND NOT EXISTS (SELECT 1 FROM project_members m WHERE m.project_id=:projectId AND m.user_id=u.id)
        """, nativeQuery = true)
    Page<com.eclectics.worksync.users.dto.UserSelection> searchActiveNonMembers(
            @Param("projectId") UUID projectId, @Param("search") String search, Pageable pageable);

    @Query("""
        select new com.eclectics.worksync.users.dto.UserSelection(u.id,u.firstName,u.lastName,u.email)
        from User u where u.id in :ids
        """)
    java.util.List<com.eclectics.worksync.users.dto.UserSelection> findSelections(@Param("ids") java.util.Collection<UUID> ids);

    @Query("""
        select new com.eclectics.worksync.users.dto.UserSelection(u.id,u.firstName,u.lastName,u.email)
        from User u where u.id=:id and u.status=com.eclectics.worksync.users.entity.AccountStatus.ACTIVE
        """)
    Optional<com.eclectics.worksync.users.dto.UserSelection> findActiveSelection(@Param("id") UUID id);
}
