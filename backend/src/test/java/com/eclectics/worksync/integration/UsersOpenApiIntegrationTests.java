package com.eclectics.worksync.integration;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises generated Springdoc metadata and real public UI routes with the actual security chain. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class UsersOpenApiIntegrationTests {
    @Autowired MockMvc mvc;
    @MockitoBean JavaMailSender sender;

    private JsonNode specification() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        return JsonMapper.builder().build().readTree(response.getResponse().getContentAsString());
    }

    @Test
    void declaresExactlyOneHttpBearerJwtSchemeWithoutGlobalAuthentication() throws Exception {
        JsonNode spec = specification();
        var schemes = spec.path("components").path("securitySchemes");
        assertThat(schemes.size()).isEqualTo(1);
        var scheme = schemes.path("bearerAuth");
        assertThat(scheme.path("type").stringValue()).isEqualTo("http");
        assertThat(scheme.path("scheme").stringValue()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").stringValue()).isEqualTo("JWT");
        assertThat(spec.path("security").isMissingNode() || spec.path("security").isEmpty()).isTrue();
    }

    @Test
    void allSevenUsersOperationsRequireBearerInTheGeneratedSpec() throws Exception {
        JsonNode paths = specification().path("paths");
        for (String entry : List.of("get /api/v1/users/me", "put /api/v1/users/me",
                "get /api/v1/admin/users", "get /api/v1/admin/users/{userId}",
                "patch /api/v1/admin/users/{userId}/approve",
                "patch /api/v1/admin/users/{userId}/reject",
                "patch /api/v1/admin/users/{userId}/status")) {
            String[] parts = entry.split(" ", 2);
            var operation = paths.path(parts[1]).path(parts[0]);
            assertThat(operation.isMissingNode()).as(entry).isFalse();
            assertThat(operation.path("security").size()).as(entry).isEqualTo(1);
            assertThat(operation.path("security").get(0).has("bearerAuth")).as(entry).isTrue();
        }
    }

    @Test
    void publicAuthOperationsRemainDocumentedWithoutAuthentication() throws Exception {
        JsonNode paths = specification().path("paths");
        for (String endpoint : List.of("register", "login", "forgot-password", "reset-password")) {
            var operation = paths.path("/api/v1/auth/" + endpoint).path("post");
            assertThat(operation.isMissingNode()).as(endpoint).isFalse();
            var security = operation.path("security");
            assertThat(security.isMissingNode() || security.isEmpty()).as(endpoint).isTrue();
        }
    }

    @Test
    void swaggerUiAndConfigurationAreAvailableWithoutAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

    @Test
    void documentationAnnotationsDoNotPermitAnonymousUsersRequests() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/00000000-0000-0000-0000-000000000001/approve"))
                .andExpect(status().isUnauthorized());
    }
}
