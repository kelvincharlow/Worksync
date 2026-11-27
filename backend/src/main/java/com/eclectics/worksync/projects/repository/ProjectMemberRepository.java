package com.eclectics.worksync.projects.repository;
import com.eclectics.worksync.projects.entity.ProjectMember;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    org.springframework.data.domain.Page<ProjectMember> findByProjectId(UUID projectId, org.springframework.data.domain.Pageable page);
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);
}
