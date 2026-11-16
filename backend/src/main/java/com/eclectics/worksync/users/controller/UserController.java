package com.eclectics.worksync.users.controller;

import com.eclectics.worksync.auth.security.WorkSyncPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.eclectics.worksync.users.dto.UpdateProfileRequest;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Auth's filter/security chain supplies the verified identity and allowed account states. */
@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/users/me")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse getMe(@AuthenticationPrincipal WorkSyncPrincipal principal) {
        return userService.getUserById(currentUserId(principal));
    }

    @PutMapping
    public UserResponse updateMe(@AuthenticationPrincipal WorkSyncPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(currentUserId(principal), request);
    }

    private UUID currentUserId(WorkSyncPrincipal principal) {
        // No request-supplied UUID/email may substitute for the verified principal.
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
