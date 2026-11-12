package com.eclectics.worksync.auth.service;

import com.eclectics.worksync.auth.dto.AuthResponse;
import com.eclectics.worksync.auth.dto.LoginRequest;
import com.eclectics.worksync.auth.dto.RegistrationRequest;
import com.eclectics.worksync.users.dto.CreateUserCommand;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.dto.AuthUserDetails;
import com.eclectics.worksync.users.exception.DuplicateEmailException;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserService userService,
                    
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userService = userService;
       
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
        // 1. Fetch credentials via UserService (returns 401 if user not found)
        AuthUserDetails user;
        try {
            user = userService.findForAuthentication(req.email());
        } catch (UserNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }

        // 2. Verify password
        if (!passwordEncoder.matches(req.password(), user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }

        // 3. Reject BLOCKED and REJECTED accounts
        if (user.status() == AccountStatus.BLOCKED || user.status() == AccountStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is " + user.status() + ".");
        }

        // 4. Generate JWT
        String token = jwtService.generateToken(
                user.id().toString(),
                user.email(),
                user.role().name(),
                user.status().name(),
                Collections.emptySet()
        );

        int expiresIn = jwtService.getExpirationSeconds();

        // 5. Fetch safe UserResponse for login response
        UserResponse safeUser = userService.getUserById(user.id());

        return new AuthResponse(token, "Bearer", expiresIn, safeUser);
    }}