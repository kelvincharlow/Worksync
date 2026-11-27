package com.eclectics.worksync.projects.dto;
import com.eclectics.worksync.projects.entity.Project;
import java.util.List;
public record ProjectConfigurationResponse(List<String> enabledViews, List<String> enabledWorkTypes) {
    public static ProjectConfigurationResponse from(Project project) {
        return new ProjectConfigurationResponse(project.getEnabledViews().stream().map(Enum::name).sorted().toList(),
                project.getEnabledWorkTypes().stream().map(Enum::name).sorted().toList());
    }
}
