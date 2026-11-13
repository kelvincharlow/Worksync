package com.eclectics.worksync.notifications.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests only the pure guard; never invokes the live test method or any mail service. */
class ManualEmailTestGuardTests {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"false", "TRUE", "True", "1", " true", "true "})
    void cannotProceedWithoutExactOptInEvenWithValidRecipient(String optIn) {
        assertThat(SmtpEmailManualIntegrationTest.recipientIfOptedIn(optIn, "owned@example.invalid")).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "invalid", "person@", "a@example.com,b@example.com",
            "person@example.com\n", "person@example.com\r\nBcc: another@example.com"})
    void cannotProceedWithoutSingleValidRecipientEvenWithOptIn(String recipient) {
        assertThat(SmtpEmailManualIntegrationTest.recipientIfOptedIn("true", recipient)).isNull();
    }

    @Test
    void acceptsValidRecipientOnlyWhenOptInIsPresent() {
        assertThat(SmtpEmailManualIntegrationTest.recipientIfOptedIn("true", " owned@example.invalid "))
                .isEqualTo("owned@example.invalid");
    }

    @Test
    void rejectsOversizedRecipient() {
        assertThat(SmtpEmailManualIntegrationTest.recipientIfOptedIn("true", "x".repeat(254) + "@example.com"))
                .isNull();
    }
}
