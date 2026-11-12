package com.eclectics.worksync.users.repository;

import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.User;
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

    Page<User> findByStatus(AccountStatus status, Pageable pageable);
}
