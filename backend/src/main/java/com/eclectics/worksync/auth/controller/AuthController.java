package com.eclectics.worksync.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.eclectics.worksync.auth.dto.AuthResponse;
import com.eclectics.worksync.auth.dto.LoginRequest;
import com.eclectics.worksync.auth.dto.RegistrationRequest;
import com.eclectics.worksync.auth.service.AuthService;
import com.eclectics.worksync.auth.dto.ForgotPasswordRequest;
import com.eclectics.worksync.auth.dto.MessageResponse;
import com.eclectics.worksync.auth.dto.ResetPasswordConfirmRequest;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.auth.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

   private final AuthService authService;
    private final RateLimiterService rateLimiterService;

    public AuthController(AuthService authService, RateLimiterService rateLimiterService) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
    }
    /**
     * Public self-registration endpoint.
     * Path: POST /api/v1/auth/register
     */
  @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegistrationRequest request) {
        UserResponse registeredUser = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredUser);
    }

    /**
     * Public login endpoint.
     * Path: POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
  @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest) {

        // 1. Extract IP (supports proxies / cloud load balancers)
        String clientIp = servletRequest.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = servletRequest.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }

        // 2. Reject if this IP has flooded requests (returns 429 Too Many Requests)
        if (!rateLimiterService.isIpAllowed(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
        }

        // 3. Delegate to service
        MessageResponse response = authService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordConfirmRequest request) {
        MessageResponse response = authService.confirmPasswordReset(request);
        return ResponseEntity.ok(response);
    }
}