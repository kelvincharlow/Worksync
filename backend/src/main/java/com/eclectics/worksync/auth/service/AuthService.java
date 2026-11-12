package com.eclectics.worksync.auth.service;

import com.eclectics.worksync.auth.dto.AuthResponse;
import com.eclectics.worksync.auth.dto.LoginRequest;
import com.eclectics.worksync.auth.dto.RegistrationRequest;
import com.eclectics.worksync.users.dto.CreateUserCommand;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.exception.DuplicateEmailException;
import com.eclectics.worksync.users.repository.UserRepository;
import com.eclectics.worksync.users.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserService userService,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Handles public self-registration via teammate's UserService.
     */
    public UserResponse register(RegistrationRequest req) {
        // 1. Hash the raw password before sending to UserService
        String hashedPassword = passwordEncoder.encode(req.password());

        // 2. Wrap into teammate's CreateUserCommand
        CreateUserCommand command = new CreateUserCommand(
                req.firstName(),
                req.lastName(),
                req.email(),
                req.phoneNumber(),
                hashedPassword
        );

        try {
            // 3. Delegate creation to UserService (returns their safe UserResponse DTO)
            return userService.createPendingUser(command);
        } catch (DuplicateEmailException ex) {
            // Translate to OpenAPI 409 Conflict
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        }
    }

    /**
     * Authenticates with email and password, returning a JWT for ACTIVE or PENDING_APPROVAL users.
     */
    public AuthResponse login(LoginRequest req) {
        // 1. Look up user by email (OpenAPI: 401 if missing)
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password."));

        // 2. Verify password (OpenAPI: 401 if mismatch)
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }

        // 3. Enforce OpenAPI status rule: BLOCKED or REJECTED accounts are denied access (403 Forbidden)
        if (user.getStatus() == AccountStatus.BLOCKED || user.getStatus() == AccountStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is " + user.getStatus() + ".");
        }

        // 4. Generate JWT access token with user claims
        String token = jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                Collections.emptySet()
        );

        int expiresIn = jwtService.getExpirationSeconds();

        // 5. Return AuthResponse containing token and safe UserResponse
        return new AuthResponse(token, "Bearer", expiresIn, UserResponse.from(user));
    }
}