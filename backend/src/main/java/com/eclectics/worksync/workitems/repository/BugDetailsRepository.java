package com.eclectics.worksync.workitems.repository;

import com.eclectics.worksync.workitems.entity.BugDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BugDetailsRepository extends JpaRepository<BugDetails, UUID> {

    List<BugDetails> findAllByFixerId(UUID fixerId);

    List<BugDetails> findAllByVerifierId(UUID verifierId);
}