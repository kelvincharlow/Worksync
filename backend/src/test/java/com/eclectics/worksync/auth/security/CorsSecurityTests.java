package com.eclectics.worksync.auth.security;

import com.eclectics.worksync.auth.controller.AuthController;
import com.eclectics.worksync.auth.service.AuthService;
import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.auth.service.RateLimiterService;
import com.eclectics.worksync.auth.service.TokenBlocklistService;
import com.eclectics.worksync.users.controller.AdminUserController;
import com.eclectics.worksync.users.controller.UserController;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.service.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring Security CorsFilter/JwtFilter chain; no database or email transport. */
@WebMvcTest(controllers = {AuthController.class, UserController.class, AdminUserController.class},
        properties = "app.cors.allowed-origins=http://localhost:5000, http://127.0.0.1:5000, https://worksync-frontend-gx8mj5zah-kelvins-projects-e3a5059b.vercel.app, https://app.example.invalid")
@Import({SecurityConfig.class, JwtFilter.class, TokenBlocklistService.class})
class CorsSecurityTests {
    private static final String PREVIEW = "https://worksync-frontend-gx8mj5zah-kelvins-projects-e3a5059b.vercel.app";
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean AuthService auth;
    @MockitoBean UserService users;
    @MockitoBean RateLimiterService limiter;

    @ParameterizedTest
    @ValueSource(strings = {PREVIEW, "https://app.example.invalid", "http://localhost:5000", "http://127.0.0.1:5000"})
    void exactAllowedOriginGetsSuccessfulLoginPreflight(String origin) throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        verifyNoInteractions(jwt, auth, users);
    }

    @Test
    void preflightIsCompletedBeforeJwtFilterEvenWithAnInvalidBearerHeader() throws Exception {
        mvc.perform(options("/api/v1/admin/users/00000000-0000-0000-0000-000000000001/status")
                .header("Origin", PREVIEW).header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .header("Authorization", "Bearer fixture-not-a-real-token"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", PREVIEW));
        verifyNoInteractions(jwt, auth, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://attacker.example", "https://unapproved.vercel.app", "null",
            "https://worksync-frontend-gx8mj5zah-kelvins-projects-e3a5059b.vercel.app.attacker.example"})
    void unapprovedOriginIsRejected(String origin) throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(jwt, auth, users);
    }

    @Test
    void unnecessaryMethodAndHeaderAreNotAllowed() throws Exception {
        mvc.perform(options("/api/v1/users/me").header("Origin", PREVIEW)
                .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/auth/login").header("Origin", PREVIEW)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "x-unapproved-header"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginAndRegistrationRemainUnauthenticatedAndReturnCorsHeaders() throws Exception {
        // Invalid bodies yield controller validation 400, not a security authentication rejection.
        for (String endpoint : new String[]{"login", "register"}) {
            mvc.perform(post("/api/v1/auth/" + endpoint).header("Origin", PREVIEW)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Access-Control-Allow-Origin", PREVIEW));
        }
        verifyNoInteractions(jwt, auth, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/me", "/api/v1/admin/users"})
    void allowedCorsOriginDoesNotGrantProtectedAccess(String path) throws Exception {
        mvc.perform(get(path).header("Origin", PREVIEW)).andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", PREVIEW));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    void authenticatedOrdinaryUserStillCannotAccessAdminRoutes() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwt.validateToken("fixture-token")).thenReturn(true);
        when(jwt.extractUserId("fixture-token")).thenReturn(id.toString());
        when(users.getUserById(id)).thenReturn(new UserResponse(id, "First", "Last", "fixture@example.invalid",
                "+254712345678", SystemRole.USER, AccountStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH));
        mvc.perform(get("/api/v1/admin/users").header("Origin", PREVIEW)
                .header("Authorization", "Bearer fixture-token"))
                .andExpect(status().isForbidden()).andExpect(header().string("Access-Control-Allow-Origin", PREVIEW));
        verify(users, never()).getUsers(any());
    }
}
