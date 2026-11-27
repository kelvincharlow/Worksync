package com.eclectics.worksync.projects.service;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.repository.ProjectRepository;
import com.eclectics.worksync.projects.repository.ProjectMemberRepository;
import com.eclectics.worksync.users.service.UserDirectoryService;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional(readOnly=true)
public class ProjectConfigurationService {
    private final ProjectRepository projects;
    private final ProjectAccessService access;
    private final ProjectMemberRepository members;
    private final UserDirectoryService users;
    private final EntityManager em;
    public ProjectConfigurationService(ProjectRepository projects,ProjectAccessService access,UserDirectoryService users,EntityManager em,ProjectMemberRepository members) {
        this.projects=projects;this.access=access;this.users=users;this.em=em;this.members=members;
    }
    private ProjectMemberAccess participant(UUID id,UUID actor) {
        // A joined transaction may already manage a stale membership. Queries alone do not refresh it.
        // For PATCH this executes after lifecycle -> Project locks. Membership writers must use that order too.
        var membership=members.findByProjectIdAndUserId(id,actor).orElseThrow(ProjectNotFoundException::new);
        em.refresh(membership);
        try {return access.getMemberAccess(id,actor);}
        catch(ProjectAccessDeniedException hidden){throw new ProjectNotFoundException();}
    }
    public ProjectConfigurationResponse get(UUID id,UUID actor) {
        if(participant(id,actor).role()==ProjectRole.VIEWER)throw new ProjectAccessDeniedException();
        return ProjectConfigurationResponse.from(projects.findById(id).orElseThrow(ProjectNotFoundException::new));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ProjectConfigurationResponse update(UUID id,UUID actor,UpdateProjectConfigurationRequest request) {
        request.validate();
        try {users.lockActiveActorForMembershipWrite(actor);}
        catch(UserNotFoundException inactive){throw new ProjectAccessDeniedException();}
        var project=projects.lockById(id).orElseThrow(ProjectNotFoundException::new);
        em.refresh(project);
        if(participant(id,actor).role()!=ProjectRole.MANAGER)throw new ProjectAccessDeniedException();
        if(project.getStatus()==ProjectStatus.ARCHIVED)throw new ProjectArchivedException();
        project.updateConfiguration(request.views(),request.types());
        return ProjectConfigurationResponse.from(projects.saveAndFlush(project));
    }
}
