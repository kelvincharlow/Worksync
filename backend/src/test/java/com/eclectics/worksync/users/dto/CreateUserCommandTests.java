package com.eclectics.worksync.users.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CreateUserCommandTests {
    private static final String HASH_FIXTURE = "encoded-hash-fixture";

    @Test
    void stringRepresentationDoesNotDiscloseCommandValues() {
        assertThat(command().toString())
                .doesNotContain(HASH_FIXTURE, "jane@example.com", "+254712345678");
    }

    @Test
    void jsonSerializationDoesNotDisclosePasswordHash() {
        String json = JsonMapper.builder().build().writeValueAsString(command());

        assertThat(json).contains("\"firstName\":\"Jane\"")
                .doesNotContain("passwordHash", HASH_FIXTURE);
    }

    private CreateUserCommand command() {
        return new CreateUserCommand("Jane", "Doe", "jane@example.com", "+254712345678", HASH_FIXTURE);
    }
}
