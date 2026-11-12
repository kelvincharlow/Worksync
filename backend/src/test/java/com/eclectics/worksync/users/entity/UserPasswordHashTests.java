package com.eclectics.worksync.users.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPasswordHashTests {
    private static final String OLD_HASH = "old-encoded-hash-fixture";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingHashesWithoutChangingExistingHash(String invalidHash) {
        User user = user();

        assertThatThrownBy(() -> user.changePasswordHash(invalidHash))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getPasswordHash()).isEqualTo(OLD_HASH);
    }

    @Test
    void rejectsHashLongerThanStorageLimitWithoutIncludingItInError() {
        User user = user();
        String invalidHash = "x".repeat(513);

        assertThatThrownBy(() -> user.changePasswordHash(invalidHash))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(invalidHash);
        assertThat(user.getPasswordHash()).isEqualTo(OLD_HASH);
    }

    @Test
    void acceptsHashAtStorageLimitWithoutEncodingOrTrimmingIt() {
        User user = user();
        String encodedHash = " " + "x".repeat(510) + " ";

        user.changePasswordHash(encodedHash);

        assertThat(user.getPasswordHash()).isEqualTo(encodedHash);
    }

    private User user() {
        return new User("Jane", "Doe", "jane@example.com", "+254712345678", OLD_HASH);
    }
}
