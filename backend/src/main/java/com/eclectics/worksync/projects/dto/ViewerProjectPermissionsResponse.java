package com.eclectics.worksync.projects.dto;

import com.eclectics.worksync.projects.entity.*;
import java.util.List;

/** Separate closed Viewer shape: never includes internal configuration or account identifiers. */
public record ViewerProjectPermissionsResponse(ProjectRole role,ProjectResponsibility responsibility,
        boolean isOwner,List<String> actions,List<String> availableDeliveryFeatures) {
    public static ViewerProjectPermissionsResponse from(ProjectMemberAccess member) {
        if(member.role()!=ProjectRole.VIEWER || member.isOwner()) throw new IllegalArgumentException("Viewer membership required");
        return new ViewerProjectPermissionsResponse(ProjectRole.VIEWER,null,false,
                ProjectCapabilities.availableActions(member.role(),false,member.projectStatus()).stream().map(Enum::name).toList(),
                List.of()); // Delivery/publication APIs are not implemented; never imply their availability.
    }
}
