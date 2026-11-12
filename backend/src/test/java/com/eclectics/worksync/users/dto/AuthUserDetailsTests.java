package com.eclectics.worksync.users.dto;

import com.eclectics.worksync.users.entity.AccountStatus;
import com.eclectics.worksync.users.entity.SystemRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUserDetailsTests {
    private static final String HASH_FIXTURE = "encoded-hash-fixture";
    private static final UUID ID = UUID.fromString("a1ce3696-7892-4e29-a203-84b0dd15ac43");

    @Test
    void toStringRedactsAllAccountDetails() {
        String text = details().toString();

        assertThat(text).isEqualTo("AuthUserDetails[redacted]")
                .doesNotContain(HASH_FIXTURE, "jane@example.com", ID.toString(), "ADMIN", "BLOCKED");
    }

    @Test
    void jsonOmitsHashWhileTrustedJavaAccessStillWorks() {
        AuthUserDetails details = details();
        var json = JsonMapper.builder().build().valueToTree(details);

        assertThat(json.has("passwordHash")).isFalse();
        assertThat(json.toString()).doesNotContain(HASH_FIXTURE);
        assertThat(json.get("id").stringValue()).isEqualTo(ID.toString());
        assertThat(details.passwordHash()).isEqualTo(HASH_FIXTURE);
    }

    private AuthUserDetails details() {
        return new AuthUserDetails(ID, "jane@example.com", HASH_FIXTURE, SystemRole.ADMIN, AccountStatus.BLOCKED);
    }
}
