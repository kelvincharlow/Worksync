package com.eclectics.worksync.projects.repository;
import com.eclectics.worksync.projects.entity.Project;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Project p where p.id=:id")
    java.util.Optional<Project> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query("""
        select new com.eclectics.worksync.projects.dto.ProjectSummaryResponse(p.id,p.name,p.status,m.role,m.responsibility,
            case when p.ownerUserId=:actor then true else false end)
        from Project p join ProjectMember m on m.projectId=p.id
        where m.userId=:actor and (:status is null or p.status=:status)
        """)
    org.springframework.data.domain.Page<com.eclectics.worksync.projects.dto.ProjectSummaryResponse> findAccessible(
            @org.springframework.data.repository.query.Param("actor") UUID actor,
            @org.springframework.data.repository.query.Param("status") com.eclectics.worksync.projects.entity.ProjectStatus status,
            org.springframework.data.domain.Pageable page);
}
