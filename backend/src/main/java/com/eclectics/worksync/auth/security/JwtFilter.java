package com.eclectics.worksync.auth.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.service.UserService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import java.util.UUID;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    public JwtFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Check for the Authorization header
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // Remove "Bearer " prefix

            // 2. Validate token signature and expiration
           if (jwtService.validateToken(token)) {
                try {
                    // 1. Extract and parse user UUID
                    String subject = jwtService.extractUserId(token);
                    UUID userId = UUID.fromString(subject);

                    // 2. Fetch fresh user details from database
                    UserResponse user = userService.getUserById(userId);

                    // 3. Reject if account is BLOCKED or REJECTED in database
                    if (user.status() != AccountStatus.BLOCKED && user.status() != AccountStatus.REJECTED) {
                        
                        // 4. Create WorkSyncPrincipal (without password hash)
                        WorkSyncPrincipal principal = new WorkSyncPrincipal(user.id(), user.email());

                        // 5. Authorities based on fresh database role and status
                        List<SimpleGrantedAuthority> authorities = List.of(
                                new SimpleGrantedAuthority("ROLE_" + user.role().name()),
                                new SimpleGrantedAuthority("STATUS_" + user.status().name())
                        );

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (IllegalArgumentException | UserNotFoundException ex) {
                    // Invalid UUID or user no longer exists in DB: clear context
                    SecurityContextHolder.clearContext();
                }}
            } 
        

        // 5. Let the request continue to the controller
        filterChain.doFilter(request, response);
    }
}