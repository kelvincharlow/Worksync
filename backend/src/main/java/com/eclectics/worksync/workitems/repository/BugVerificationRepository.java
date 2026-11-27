package com.eclectics.worksync.workitems.repository;

import com.eclectics.worksync.workitems.entity.BugVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BugVerificationRepository extends JpaRepository<BugVerification, UUID> {

    List<BugVerification> findAllByBugIdOrderByVerifiedAtDesc(UUID bugId);

    long countByBugId(UUID bugId);
}