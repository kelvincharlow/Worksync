package com.eclectics.worksync.users.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable HTTP envelope; do not expose Spring's internal Page serialization contract. */
public record UserPageResponse(List<UserResponse> content, int page, int size, long totalElements) {
    public UserPageResponse {
        content = List.copyOf(content);
    }

    public static UserPageResponse from(Page<UserResponse> users) {
        return new UserPageResponse(users.getContent(), users.getNumber(), users.getSize(), users.getTotalElements());
    }
}
