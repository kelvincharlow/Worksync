package com.eclectics.worksync.users.controller;

import com.eclectics.worksync.auth.security.JwtFilter;
import com.eclectics.worksync.auth.security.SecurityConfig;
import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses Auth's real JWT verification and filter chain, not synthetic mock authentication. */
@WebMvcTest(controllers = {UserController.class, AdminUserController.class},
        properties = {"app.jwt.secret=users-controller-test-key-at-least-32-bytes",
                "logging.level.org.springframework.web=INFO"})
@Import({SecurityConfig.class, JwtFilter.class, JwtService.class})
class UserControllerSecurityTests {
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final String PROFILE = """
            {"firstName":"Updated","lastName":"Name","phoneNumber":"+254712345678"}
            """;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean UserService service;

    private UserResponse user(UUID id, SystemRole role, AccountStatus status) {
        return new UserResponse(id, "First", "Last", "person@example.com", "+254712345678",
                role, status, Instant.EPOCH, Instant.EPOCH);
    }

    private String bearer(SystemRole role, AccountStatus status) {
        when(service.getUserById(ACTOR)).thenReturn(user(ACTOR, role, status));
        // Deliberately stale privileged claims: Auth must use current service data.
        return "Bearer " + jwt.generateToken(ACTOR.toString(), "old@example.com",
                "ADMIN", "ACTIVE", Set.of());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "PENDING_APPROVAL"})
    void ownProfileUsesPrincipalAndAllowsPendingAccounts(AccountStatus state) throws Exception {
        mvc.perform(get("/api/v1/users/me").param("userId", TARGET.toString())
                .header("Authorization", bearer(SystemRole.USER, state)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ACTOR.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(service, never()).getUserById(TARGET);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "PENDING_APPROVAL"})
    void updateUsesPrincipalAndOnlyProfileDto(AccountStatus state) throws Exception {
        String authorization = bearer(SystemRole.USER, state);
        when(service.updateProfile(eq(ACTOR), any())).thenReturn(user(ACTOR, SystemRole.USER, state));
        mvc.perform(put("/api/v1/users/me").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(service).updateProfile(ACTOR, new UpdateProfileRequest("Updated", "Name", "+254712345678"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "userId", "email", "role", "status", "passwordHash",
            "password", "permissions", "credentials"})
    void rejectsMassAssignment(String field) throws Exception {
        String body = PROFILE.strip().replace("}", ",\"" + field + "\":\"sensitive-input\"}");
        mvc.perform(put("/api/v1/users/me")
                .header("Authorization", bearer(SystemRole.USER, AccountStatus.ACTIVE))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("sensitive-input"))));
        verify(service, never()).updateProfile(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"firstName\":\" \",\"lastName\":\"Name\",\"phoneNumber\":\"123\"}",
            "{\"firstName\":\"First\",\"lastName\":\"Name\"}"})
    void invalidProfileIsRejected(String body) throws Exception {
        mvc.perform(put("/api/v1/users/me")
                .header("Authorization", bearer(SystemRole.USER, AccountStatus.ACTIVE))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verify(service, never()).updateProfile(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/me", "/api/v1/admin/users",
            "/api/v1/admin/users/00000000-0000-0000-0000-000000000001"})
    void anonymousIsUnauthorized(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousUpdateIsUnauthorized() throws Exception {
        mvc.perform(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryUserCannotUseAdminRoutesDespiteAdminTokenClaim() throws Exception {
        String auth = bearer(SystemRole.USER, AccountStatus.ACTIVE);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users/" + TARGET).header("Authorization", auth))
                .andExpect(status().isForbidden());
        verify(service, never()).getUserById(TARGET);
        verify(service, never()).getUsers(any());
    }

    @Test
    void pendingAdminIsForbidden() throws Exception {
        String auth = bearer(SystemRole.ADMIN, AccountStatus.PENDING_APPROVAL);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users/" + TARGET).header("Authorization", auth))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"BLOCKED", "REJECTED"})
    void blockedAndRejectedAreNotAuthenticatedByExistingFilter(AccountStatus state) throws Exception {
        String auth = bearer(SystemRole.ADMIN, state);
        mvc.perform(get("/api/v1/users/me").header("Authorization", auth)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(PROFILE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", auth)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users/" + TARGET).header("Authorization", auth))
                .andExpect(status().isUnauthorized());
        verify(service, never()).updateProfile(any(), any());
    }

    @Test
    void activeAdminCanReadUserAndMissingUserIs404() throws Exception {
        String auth = bearer(SystemRole.ADMIN, AccountStatus.ACTIVE);
        when(service.getUserById(TARGET)).thenReturn(user(TARGET, SystemRole.USER, AccountStatus.ACTIVE));
        mvc.perform(get("/api/v1/admin/users/" + TARGET).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(TARGET.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        when(service.getUserById(TARGET)).thenThrow(new UserNotFoundException());
        mvc.perform(get("/api/v1/admin/users/" + TARGET).header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void unfilteredListingHasStableEnvelopeAndBoundedDeterministicPagination() throws Exception {
        String auth = bearer(SystemRole.ADMIN, AccountStatus.ACTIVE);
        when(service.getUsers(any())).thenAnswer(call -> {
            Pageable page = call.getArgument(0);
            assertThat(page.getPageNumber()).isEqualTo(1);
            assertThat(page.getPageSize()).isEqualTo(2);
            assertThat(page.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            return new PageImpl<>(List.of(user(TARGET, SystemRole.USER, AccountStatus.ACTIVE)), page, 6);
        });
        mvc.perform(get("/api/v1/admin/users").param("page", "1").param("size", "2")
                .header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(TARGET.toString()))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void filteredListingUsesExistingServiceAndDefaultPage() throws Exception {
        String auth = bearer(SystemRole.ADMIN, AccountStatus.ACTIVE);
        when(service.getUsersByStatus(eq(AccountStatus.PENDING_APPROVAL), any())).thenAnswer(call -> {
            Pageable page = call.getArgument(1);
            assertThat(page.getPageNumber()).isZero();
            assertThat(page.getPageSize()).isEqualTo(20);
            return new PageImpl<>(List.of(user(TARGET, SystemRole.USER, AccountStatus.PENDING_APPROVAL)), page, 1);
        });
        mvc.perform(get("/api/v1/admin/users").param("status", "PENDING_APPROVAL")
                .header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"));
        verify(service, never()).getUsers(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "status=INVALID", "page=abc"})
    void invalidPaginationOrStatusIs400(String query) throws Exception {
        mvc.perform(get("/api/v1/admin/users?" + query)
                .header("Authorization", bearer(SystemRole.ADMIN, AccountStatus.ACTIVE)))
                .andExpect(status().isBadRequest());
        verify(service, never()).getUsers(any());
        verify(service, never()).getUsersByStatus(any(), any());
    }

    @Test
    void invalidUuidIs400() throws Exception {
        mvc.perform(get("/api/v1/admin/users/not-a-uuid")
                .header("Authorization", bearer(SystemRole.ADMIN, AccountStatus.ACTIVE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredAndWronglySignedTokensAre401() throws Exception {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "users-controller-test-key-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expired = io.jsonwebtoken.Jwts.builder().subject(ACTOR.toString()).issuer("worksync-api")
                .expiration(java.util.Date.from(Instant.now().minusSeconds(60))).signWith(key).compact();
        var otherIssuer = new JwtService("another-test-signing-key-at-least-32-bytes", 900, "worksync-api");
        String wrongKey = otherIssuer.generateToken(ACTOR.toString(), "person@example.com", "ADMIN", "ACTIVE", Set.of());
        for (String token : List.of(expired, wrongKey)) {
            mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void invalidTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
