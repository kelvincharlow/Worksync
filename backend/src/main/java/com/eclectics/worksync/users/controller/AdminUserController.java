package com.eclectics.worksync.users.controller;

import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.eclectics.worksync.users.dto.ReasonRequest;
import com.eclectics.worksync.users.dto.ChangeAccountStatusRequest;
import com.eclectics.worksync.users.dto.UserPageResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
@SecurityRequirement(name = "bearerAuth")
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

    @PatchMapping("/{userId}/approve")
    public UserResponse approve(@AuthenticationPrincipal WorkSyncPrincipal principal,
            @PathVariable UUID userId) {
        return userService.approveUser(actorId(principal), userId);
    }

    @PatchMapping("/{userId}/reject")
    public UserResponse reject(@AuthenticationPrincipal WorkSyncPrincipal principal,
            @PathVariable UUID userId, @Valid @RequestBody(required = false) ReasonRequest request) {
        // The draft accepts an optional reason; no reason storage/delivery exists yet.
        return userService.rejectUser(actorId(principal), userId);
    }

    @PatchMapping("/{userId}/status")
    public UserResponse changeStatus(@AuthenticationPrincipal WorkSyncPrincipal principal,
            @PathVariable UUID userId, @Valid @RequestBody ChangeAccountStatusRequest request) {
        // Requested destination is constrained input, never a caller authority or general status setter.
        return "BLOCKED".equals(request.status())
                ? userService.blockUser(actorId(principal), userId)
                : userService.unblockUser(actorId(principal), userId);
    }

    private UUID actorId(WorkSyncPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
