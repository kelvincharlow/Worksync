package com.eclectics.worksync.projects.dto;
import java.util.List;
import org.springframework.data.domain.Page;
public record ProjectMemberPageResponse(List<ProjectMemberResponse> content, int page, int size, long totalElements) {
    public static ProjectMemberPageResponse from(Page<ProjectMemberResponse> page) {
        return new ProjectMemberPageResponse(page.getContent(),page.getNumber(),page.getSize(),page.getTotalElements());
    }
}
