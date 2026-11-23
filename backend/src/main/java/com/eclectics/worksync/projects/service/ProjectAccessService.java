package com.eclectics.worksync.projects.service;
import com.eclectics.worksync.projects.dto.ProjectMemberAccess;
import com.eclectics.worksync.projects.entity.ProjectAction;
import java.util.UUID;
/** Trusted in-process contract. Actor UUID comes from WorkSyncPrincipal, never an HTTP body.
 * Capability checks do not authorize a particular work record or enable optional work features.
 * M3 must enforce enabled-type policy, assignee ownership, designated verifier and bug state.
 * Lead-to-QA-only verifier designation needs actor/candidate snapshots; eligible != authorized to designate.
 * Guard methods without an actor parameter never replace requireAction/getMemberAccess.
 */
public interface ProjectAccessService {
    void requireAction(UUID projectId, UUID actorUserId, ProjectAction action);
    void requireWritable(UUID projectId);
    void requireAssignableMember(UUID projectId, UUID targetUserId);
    void requireEligibleVerifier(UUID projectId, UUID fixerUserId, UUID verifierUserId);
    ProjectMemberAccess getMemberAccess(UUID projectId, UUID actorUserId);
}
