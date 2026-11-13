package com.eclectics.worksync.users.controller;

import com.eclectics.worksync.users.dto.UserPageResponse;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SecurityConfig requires ROLE_ADMIN and STATUS_ACTIVE, populated from fresh database state. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private static final Sort ORDER = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserPageResponse listUsers(@RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Fixed ordering prevents arbitrary sort/property traversal and breaks timestamp ties by UUID.
        var pageable = PageRequest.of(page, size, ORDER);
        var users = status == null ? userService.getUsers(pageable)
                : userService.getUsersByStatus(status, pageable);
        return UserPageResponse.from(users);
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable UUID userId) {
        return userService.getUserById(userId);
    }
}
