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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.eclectics.worksync.auth.dto.ForgotPasswordRequest;
import com.eclectics.worksync.auth.dto.MessageResponse;
import com.eclectics.worksync.auth.dto.ResetPasswordConfirmRequest;
import java.util.Collections;
import com.eclectics.worksync.notifications.email.EmailNotificationService;
@Service
public class AuthService {

   private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenService passwordResetTokenService;

    @Autowired(required = false)
    private EmailNotificationService emailNotificationService;

    public AuthService(UserService userService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       PasswordResetTokenService passwordResetTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetTokenService = passwordResetTokenService;
    }

    /**
     * Handles public self-registration via  UserService.
     */
    public UserResponse register(RegistrationRequest req) {
        // 1. Hash the raw password before sending to UserService
        String hashedPassword = passwordEncoder.encode(req.password());

        // 2. Wrap into CreateUserCommand
        CreateUserCommand command = new CreateUserCommand(
                req.firstName(),
                req.lastName(),
                req.email(),
                req.phoneNumber(),
                hashedPassword
        );

        try {
            // 3. Delegate creation to UserService (returns the safe UserResponse DTO)
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
    }
    /**
     * Generates a 6-digit reset code if the account exists.
     * Always returns 200 OK to prevent email harvesting.
     */
   public MessageResponse requestPasswordReset(ForgotPasswordRequest req) {
        try {
            // 1. Verify user exists before generating code
            userService.getUserByEmail(req.email());

            // 2. Generate the code
            String rawCode = passwordResetTokenService.createResetCode(req.email());

            // 3. Send the code via email (if EmailNotificationService is available)
            if (emailNotificationService != null) {
                emailNotificationService.sendPasswordResetCode(req.email(), rawCode, 15);
            }

        } catch (UserNotFoundException ex) {
            // Silently ignore to prevent attackers from discovering registered emails
        }

        return new MessageResponse("If an account with this email exists, a password reset code has been sent.");
    }

    /**
     * Validates the 6-digit code, hashes the new password, and updates the user.
     */
    /**
     * Validates the 6-digit code, hashes the new password, updates the user, and consumes the code.
     */
    public MessageResponse confirmPasswordReset(ResetPasswordConfirmRequest req) {
        // 1. Validate the code without consuming it yet
        boolean isValid = passwordResetTokenService.validateCode(req.email(), req.resetCode());
        if (!isValid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset code.");
        }

        // 2. Hash the new password
        String hashedPassword = passwordEncoder.encode(req.newPassword());

        // 3. Update the password in the database
        try {
            userService.resetPassword(req.email(), hashedPassword);
        } catch (UserNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }

        // 4. Consume/invalidate code ONLY after DB update succeeds
        passwordResetTokenService.consumeCode(req.email());
        // 5. Send confirmation email that password was changed
        emailNotificationService.sendPasswordChangedConfirmation(req.email());

        
        return new MessageResponse("Password has been successfully reset. You can now log in with your new password.");
    }
}