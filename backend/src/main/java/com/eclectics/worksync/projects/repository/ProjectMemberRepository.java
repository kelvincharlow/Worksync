package com.eclectics.worksync.projects.repository;
import com.eclectics.worksync.projects.entity.ProjectMember;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    org.springframework.data.domain.Page<ProjectMember> findByProjectId(UUID projectId, org.springframework.data.domain.Pageable page);
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from ProjectMember m where m.projectId=:projectId and m.userId in :ids order by m.userId")
    java.util.List<ProjectMember> lockParticipants(@org.springframework.data.repository.query.Param("projectId") UUID projectId,
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
}
