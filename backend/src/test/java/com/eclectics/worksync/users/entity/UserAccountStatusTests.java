package com.eclectics.worksync.users.entity;

import com.eclectics.worksync.users.exception.InvalidAccountStatusTransitionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountStatusTests {
    private static final String HASH_FIXTURE = "encoded-hash-fixture";

    @ParameterizedTest(name = "{0} from {1}: allowed={3}")
    @CsvSource({
            "approve, PENDING_APPROVAL, ACTIVE, true",
            "approve, ACTIVE, ACTIVE, false",
            "approve, REJECTED, ACTIVE, false",
            "approve, BLOCKED, ACTIVE, false",
            "reject, PENDING_APPROVAL, REJECTED, true",
            "reject, ACTIVE, REJECTED, false",
            "reject, REJECTED, REJECTED, false",
            "reject, BLOCKED, REJECTED, false",
            "block, PENDING_APPROVAL, BLOCKED, false",
            "block, ACTIVE, BLOCKED, true",
            "block, REJECTED, BLOCKED, false",
            "block, BLOCKED, BLOCKED, false",
            "unblock, PENDING_APPROVAL, ACTIVE, false",
            "unblock, ACTIVE, ACTIVE, false",
            "unblock, REJECTED, ACTIVE, false",
            "unblock, BLOCKED, ACTIVE, true"
    })
    void permitsOnlyDefinedTransitionsAndPreservesOtherFields(String operation,
            AccountStatus initialStatus, AccountStatus targetStatus, boolean allowed) {
        User user = userInStatus(initialStatus);
        // Test that transitions also preserve an existing administrator role.
        ReflectionTestUtils.setField(user, "role", SystemRole.ADMIN);

        if (allowed) {
            apply(user, operation);
            assertThat(user.getStatus()).isEqualTo(targetStatus);
        } else {
            assertThatThrownBy(() -> apply(user, operation))
                    .isInstanceOf(InvalidAccountStatusTransitionException.class)
                    .hasMessage("Operation is not valid for the account's current status");
            assertThat(user.getStatus()).isEqualTo(initialStatus);
        }
        assertThat(user.getEmail()).isEqualTo("jane@example.com");
        assertThat(user.getRole()).isEqualTo(SystemRole.ADMIN);
        assertThat(user.getPasswordHash()).isEqualTo(HASH_FIXTURE);
        assertThat(user.getFirstName()).isEqualTo("Jane");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getPhoneNumber()).isEqualTo("+254712345678");
    }

    private User userInStatus(AccountStatus status) {
        User user = new User("Jane", "Doe", "jane@example.com", "+254712345678", HASH_FIXTURE);
        switch (status) {
            case PENDING_APPROVAL -> { }
            case ACTIVE -> user.approve();
            case REJECTED -> user.reject();
            case BLOCKED -> {
                user.approve();
                user.block();
            }
        }
        return user;
    }

    private void apply(User user, String operation) {
        switch (operation) {
            case "approve" -> user.approve();
            case "reject" -> user.reject();
            case "block" -> user.block();
            case "unblock" -> user.unblock();
            default -> throw new IllegalArgumentException("Unknown test operation");
        }
    }
}
