package com.eclectics.worksync.auth.repository;

import com.eclectics.worksync.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    // Finds the latest active, unconsumed reset token for this user
    Optional<PasswordResetToken> findTopByEmailAndConsumedFalseOrderByCreatedAtDesc(String email);

    // Checks 60-second cooldown rate limiting per email
    boolean existsByEmailAndCreatedAtAfter(String email, Instant cutoff);
}