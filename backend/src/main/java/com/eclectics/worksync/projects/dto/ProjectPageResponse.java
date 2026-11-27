package com.eclectics.worksync.projects.dto;
import java.util.List;
import org.springframework.data.domain.Page;
public record ProjectPageResponse(List<ProjectSummaryResponse> content, int page, int size, long totalElements) {
    public static ProjectPageResponse from(Page<ProjectSummaryResponse> page) {
        return new ProjectPageResponse(page.getContent(),page.getNumber(),page.getSize(),page.getTotalElements());
    }
}
