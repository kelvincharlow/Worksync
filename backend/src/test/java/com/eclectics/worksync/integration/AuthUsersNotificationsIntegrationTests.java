package com.eclectics.worksync.integration;

import com.eclectics.worksync.auth.dto.*;
import com.eclectics.worksync.auth.service.AuthService;
import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.auth.service.PasswordResetTokenService;
import com.eclectics.worksync.auth.service.RateLimiterService;
import com.eclectics.worksync.notifications.email.EmailNotificationService;
import com.eclectics.worksync.notifications.email.SmtpEmailNotificationService;
import com.eclectics.worksync.users.dto.UserResponse;
import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import com.eclectics.worksync.users.repository.UserRepository;
import com.eclectics.worksync.users.service.UserService;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real Auth -> Users -> PostgreSQL and Auth -> real email templates/service -> mock sender.
 * Deliberately not transactional: reset/confirmation assertions observe committed writes.
 * Never uses a live SMTP transport; MockMvc printing is disabled to protect credentials.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthUsersNotificationsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired UserService users;
    @Autowired UserRepository repository;
    @Autowired JwtService jwt;
    @Autowired PasswordResetTokenService codes;
    @Autowired RateLimiterService limiter;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    @MockitoBean JavaMailSender sender;
    @MockitoSpyBean EmailNotificationService emails;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<SimpleMailMessage> outbox = new ArrayList<>();
    private final String originalPassword = UUID.randomUUID().toString();
    private final String newPassword = UUID.randomUUID().toString();

    @BeforeEach
    void isolateFixtures() {
        // This suite is run only against the dedicated disposable PostgreSQL database.
        repository.deleteAllInBatch();
        ((Map<?, ?>) ReflectionTestUtils.getField(codes, "resetStore")).clear();
        ((Map<?, ?>) ReflectionTestUtils.getField(limiter, "ipRequestLog")).clear();
        outbox.clear();
        doAnswer(call -> {
            outbox.add(new SimpleMailMessage((SimpleMailMessage) call.getArgument(0)));
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        repository.deleteAllInBatch();
        ((Map<?, ?>) ReflectionTestUtils.getField(codes, "resetStore")).clear();
        ((Map<?, ?>) ReflectionTestUtils.getField(limiter, "ipRequestLog")).clear();
    }

    private UserResponse register(String email) {
        return auth.register(new RegistrationRequest("Jane", "Doe", email, "+254712345678", originalPassword));
    }

    private UserResponse active(String email) {
        return users.approveUser(register(email).id());
    }

    private String token(UserResponse user) {
        return auth.login(new LoginRequest(user.email(), originalPassword)).accessToken();
    }

    private String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    private String requestCode(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").with(request -> {
            request.setRemoteAddr("192.0.2.10");
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email))))
                .andExpect(status().isOk());
        verify(emails).sendPasswordResetCode(eq(email), anyString(), eq(15));
        SimpleMailMessage message = outbox.getFirst();
        assertThat(message.getTo()).containsExactly(email);
        assertThat(message.getText().contains("expires in 15 minutes")).isTrue();
        var matcher = Pattern.compile("temporary reset code is: (\\d{6})").matcher(message.getText());
        // No assertion prints the code, email body, hash, password or token on failure.
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private void confirm(String email, String code, int expected) throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "resetCode", code, "newPassword", newPassword))))
                .andExpect(status().is(expected));
    }

    private void statusOf(UUID id, AccountStatus status) {
        // Fixture-only SQL; this does not add any administrator endpoint.
        jdbc.update("UPDATE users SET status = ? WHERE id = ?", status.name(), id);
    }

    @Test
    void mergedEmailProfileCreatesRealServiceWithMockedTransport() {
        assertThat(Arrays.asList(context.getEnvironment().getActiveProfiles())).contains("email", "test");
        assertThat(emails).isInstanceOf(SmtpEmailNotificationService.class);
        assertThat(mockingDetails(sender).isMock()).isTrue();
        assertThat(context.getBean(JavaMailSender.class) == sender).isTrue();
        verifyNoInteractions(sender);
    }

    @Test
    void registrationPersistsDefaultsHashesPasswordAndRejectsDuplicate() throws Exception {
        var request = new RegistrationRequest("Jane", "Doe", "register@example.com", "+254712345678", originalPassword);
        var result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(request))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andReturn();
        var stored = users.findForAuthentication("REGISTER@example.com");
        assertThat(stored.role()).isEqualTo(SystemRole.USER);
        assertThat(stored.status()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(stored.passwordHash().equals(originalPassword)).isFalse();
        assertThat(context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
                .matches(originalPassword, stored.passwordHash())).isTrue();
        assertThat(result.getResponse().getContentAsString().contains(originalPassword)).isFalse();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(request))).andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "PENDING_APPROVAL"})
    void loginAndMeWorkForEligibleStatesButWrongPasswordFails(AccountStatus state) throws Exception {
        var user = register("login@example.com");
        statusOf(user.id(), state);
        var result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", user.email(), "password", originalPassword))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.status").value(state.name()))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        String accessToken = response.get("accessToken").stringValue();
        assertThat(jwt.validateToken(accessToken)).isTrue();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.id().toString()));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", user.email(), "password", UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownLoginIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "unknown@example.com", "password", originalPassword))))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"BLOCKED", "REJECTED"})
    void currentDatabaseStatusRevokesExistingTokenAndPreventsLogin(AccountStatus state) throws Exception {
        var user = active("disabled@example.com");
        String oldToken = token(user);
        statusOf(user.id(), state);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", user.email(), "password", originalPassword))))
                .andExpect(status().isForbidden());
    }

    @Test
    void liveRoleAndStatusOverrideStaleTokenClaims() throws Exception {
        var user = active("admin@example.com");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", user.id());
        String adminToken = token(users.getUserById(user.id()));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", user.id());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role = 'ADMIN', status = 'PENDING_APPROVAL' WHERE id = ?", user.id());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/probe").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileUpdateCommitsOnlyAllowedFieldsAndIgnoresQueryIdentity() throws Exception {
        var own = register("own@example.com");
        var other = register("other@example.com");
        String hash = users.findForAuthentication(own.email()).passwordHash();
        String accessToken = token(own);
        mvc.perform(put("/api/v1/users/me").param("userId", other.id().toString())
                .header("Authorization", "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("firstName", "Changed", "lastName", "Profile", "phoneNumber", "+254700000001"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(own.id().toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        var reloaded = users.getUserById(own.id());
        assertThat(reloaded.firstName()).isEqualTo("Changed");
        assertThat(reloaded.email()).isEqualTo(own.email());
        assertThat(reloaded.role()).isEqualTo(own.role());
        assertThat(reloaded.status()).isEqualTo(own.status());
        assertThat(users.findForAuthentication(own.email()).passwordHash().equals(hash)).isTrue();
        assertThat(users.getUserById(other.id()).firstName()).isEqualTo("Jane");
        mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("firstName", "Unsafe", "lastName", "Profile", "phoneNumber", "+254700000001",
                        "role", "ADMIN", "passwordHash", "untrusted-value")))).andExpect(status().isBadRequest());
        assertThat(users.getUserById(own.id()).firstName()).isEqualTo("Changed");
    }

    @Test
    void adminListingFiltersPaginatesBreaksTimestampTiesAndFindsTarget() throws Exception {
        var admin = active("admin@example.com");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.id());
        var first = register("first@example.com");
        var second = register("second@example.com");
        jdbc.update("UPDATE users SET created_at = TIMESTAMPTZ '2020-01-01 00:00:00+00'");
        String authorization = "Bearer " + token(users.getUserById(admin.id()));
        var expected = new ArrayList<>(List.of(first.id().toString(), second.id().toString()));
        expected.sort(Comparator.reverseOrder());
        for (int page = 0; page < 2; page++) {
            mvc.perform(get("/api/v1/admin/users").header("Authorization", authorization)
                    .param("status", "PENDING_APPROVAL").param("page", Integer.toString(page)).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(page)).andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(expected.get(page)));
        }
        mvc.perform(get("/api/v1/admin/users").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/admin/users/" + first.id()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.id().toString()));
        mvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID()).header("Authorization", authorization))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetThroughRealEmailBoundaryCommitsChangesRejectsOldLoginAndConsumesCode() throws Exception {
        var user = active("reset@example.com");
        String originalHash = users.findForAuthentication(user.email()).passwordHash();
        String code = requestCode(user.email());
        confirm(user.email().toUpperCase(Locale.ROOT), code, 200);
        String stored = users.findForAuthentication(user.email()).passwordHash();
        assertThat(stored.equals(originalHash)).isFalse();
        assertThat(context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
                .matches(newPassword, stored)).isTrue();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, user.id())
                .equals(stored)).isTrue();
        assertThat(assertThrows(ResponseStatusException.class,
                () -> auth.login(new LoginRequest(user.email(), originalPassword))).getStatusCode().value()).isEqualTo(401);
        assertThat(jwt.validateToken(auth.login(new LoginRequest(user.email(), newPassword)).accessToken())).isTrue();
        verify(emails).sendPasswordChangedConfirmation(user.email().toUpperCase(Locale.ROOT));
        confirm(user.email(), code, 400);
        assertThat(outbox.size()).isEqualTo(2);
        assertThat(outbox.get(1).getText().contains(code)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"PENDING_APPROVAL", "BLOCKED", "REJECTED"})
    void ineligibleAndUnknownResetRequestsHaveIdenticalGenericResponseWithoutEmail(AccountStatus state) throws Exception {
        var user = register("ineligible@example.com");
        statusOf(user.id(), state);
        var known = auth.requestPasswordReset(new ForgotPasswordRequest(user.email()));
        var unknown = auth.requestPasswordReset(new ForgotPasswordRequest("absent@example.com"));
        assertThat(known).isEqualTo(unknown);
        verifyNoInteractions(sender);
        verify(emails, never()).sendPasswordResetCode(anyString(), anyString(), anyInt());
    }

    @Test
    void invalidAndExpiredResetCodesDoNotUpdateHash() throws Exception {
        var user = active("expiry@example.com");
        String hash = users.findForAuthentication(user.email()).passwordHash();
        String code = requestCode(user.email());
        confirm(user.email(), "000000", 400);
        expireCode(user.email());
        confirm(user.email(), code, 400);
        assertThat(users.findForAuthentication(user.email()).passwordHash().equals(hash)).isTrue();
        assertThat(outbox.size()).isEqualTo(1);
    }

    @Test
    void resetDeliveryFailureIsGenericAndDoesNotLeakProviderDetails(CapturedOutput output) throws Exception {
        var user = active("failure@example.com");
        String code[] = new String[1];
        doAnswer(call -> {
            SimpleMailMessage message = call.getArgument(0);
            var matcher = Pattern.compile("temporary reset code is: (\\d{6})").matcher(message.getText());
            if (matcher.find()) code[0] = matcher.group(1);
            throw new MailSendException("sensitive-provider-marker", new IllegalStateException(message.toString()));
        }).when(sender).send(any(SimpleMailMessage.class));
        var known = auth.requestPasswordReset(new ForgotPasswordRequest(user.email()));
        var unknown = auth.requestPasswordReset(new ForgotPasswordRequest("absent@example.com"));
        assertThat(known).isEqualTo(unknown);
        assertThat(output.getAll().contains("sensitive-provider-marker")).isFalse();
        assertThat(code[0] != null && output.getAll().contains(code[0])).isFalse();
    }

    @Test
    void confirmationEmailFailureOccursAfterCommitAndCannotUndoReset(CapturedOutput output) throws Exception {
        var user = active("confirmation@example.com");
        String code = requestCode(user.email());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, user.id());
            assertThat(context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
                    .matches(newPassword, stored)).isTrue();
            throw new MailSendException("confirmation-provider-marker");
        }).when(sender).send(any(SimpleMailMessage.class));
        confirm(user.email(), code, 200);
        assertThat(jwt.validateToken(auth.login(new LoginRequest(user.email(), newPassword)).accessToken())).isTrue();
        assertThat(codes.validateCode(user.email(), code)).isFalse();
        assertThat(output.getAll().contains("confirmation-provider-marker")).isFalse();
    }

    @Test
    void ipLimiterReturns429AndCurrentlyTrustsClientForwardedHeader() throws Exception {
        String ip = "198.51.100.10";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/forgot-password").header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", "absent@example.com"))))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/auth/forgot-password").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", "absent@example.com"))))
                .andExpect(status().isTooManyRequests());
        // Documents a trust-boundary weakness; changing this header evades the same remote IP's bucket.
        mvc.perform(post("/api/v1/auth/forgot-password").header("X-Forwarded-For", "203.0.113.240")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", "absent@example.com"))))
                .andExpect(status().isOk());
        verifyNoInteractions(sender);
    }

    private void expireCode(String email) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> store = (Map<String, Object>) ReflectionTestUtils.getField(codes, "resetStore");
        Object entry = store.get(email);
        var constructor = entry.getClass().getDeclaredConstructor(String.class, Instant.class, int.class);
        constructor.setAccessible(true);
        var accessor = entry.getClass().getDeclaredMethod("hashedCode");
        accessor.setAccessible(true);
        store.put(email, constructor.newInstance(accessor.invoke(entry), Instant.now().minusSeconds(1), 0));
    }
}
