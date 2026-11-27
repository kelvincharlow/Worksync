package com.eclectics.worksync.projects.repository;
import com.eclectics.worksync.projects.entity.Project;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Project p where p.id=:id")
    java.util.Optional<Project> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
