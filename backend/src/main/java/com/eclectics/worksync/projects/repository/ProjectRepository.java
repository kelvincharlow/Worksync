package com.eclectics.worksync.projects.repository;
import com.eclectics.worksync.projects.entity.Project;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project, UUID> { }
