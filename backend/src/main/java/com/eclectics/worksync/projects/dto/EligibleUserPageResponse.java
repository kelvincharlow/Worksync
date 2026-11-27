package com.eclectics.worksync.projects.dto;
import com.eclectics.worksync.users.dto.UserSelection;
import java.util.List;
import org.springframework.data.domain.Page;
public record EligibleUserPageResponse(List<UserSelection> content, int page, int size, long totalElements) {
    public static EligibleUserPageResponse from(Page<UserSelection> page) {
        return new EligibleUserPageResponse(page.getContent(),page.getNumber(),page.getSize(),page.getTotalElements());
    }
}
