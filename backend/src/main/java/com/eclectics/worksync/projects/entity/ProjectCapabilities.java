package com.eclectics.worksync.projects.entity;

import java.util.*;

/** Implemented Projects capabilities only. Callers first validate ACTIVE status and membership.
 * Work Items/delivery/configuration are not enabled by these presentation hints.
 */
public final class ProjectCapabilities {
    private ProjectCapabilities() { }

    public static boolean roleAllows(ProjectRole role,boolean isOwner,ProjectAction action) {
        Objects.requireNonNull(role);
        return switch(action) {
            case VIEW_PROJECT -> true;
            case VIEW_PROJECT_TEAM -> role!=ProjectRole.VIEWER;
            case UPDATE_PROJECT_DETAILS,SELECT_ELIGIBLE_USERS,ADD_PROJECT_MEMBER -> role==ProjectRole.MANAGER;
            case ADD_PROJECT_MANAGER,TRANSFER_PROJECT_OWNERSHIP -> role==ProjectRole.MANAGER && isOwner;
            default -> false; // Planned Work Items/delivery operations are never advertised here.
        };
    }
    public static boolean isRead(ProjectAction action) {
        return action==ProjectAction.VIEW_PROJECT || action==ProjectAction.VIEW_PROJECT_TEAM;
    }
    public static Set<ProjectAction> availableActions(ProjectRole role,boolean isOwner,ProjectStatus status) {
        Objects.requireNonNull(status);
        EnumSet<ProjectAction> actions=EnumSet.noneOf(ProjectAction.class);
        for(var action:ProjectAction.values()) {
            if(roleAllows(role,isOwner,action) && (status==ProjectStatus.ACTIVE || isRead(action))) actions.add(action);
        }
        return Collections.unmodifiableSet(actions);
    }
}
