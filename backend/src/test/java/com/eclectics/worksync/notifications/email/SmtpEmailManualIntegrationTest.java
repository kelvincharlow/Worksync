package com.eclectics.worksync.notifications.email;

import jakarta.validation.Validation;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Developer-only live SMTP check, excluded from normal Maven tests.
 * Never run with a real recipient unless an actual email is explicitly intended.
 * No Spring context or SMTP attempt is made before both environment guards pass.
 */
class SmtpEmailManualIntegrationTest {
    private static final String DUMMY_RESET_CODE = "TEST-ONLY-482913";
    private static final AtomicBoolean SEND_ATTEMPTED = new AtomicBoolean();

    @Test
    @EnabledIfEnvironmentVariable(named = "WORKSYNC_SEND_LIVE_EMAIL_TEST", matches = "true")
    void submitsOneDummyPasswordResetEmail() {
        String recipient = recipientIfOptedIn(System.getenv("WORKSYNC_SEND_LIVE_EMAIL_TEST"),
                System.getenv("EMAIL_TEST_RECIPIENT"));
        assumeTrue(recipient != null, "Live SMTP test requires explicit opt-in and a valid EMAIL_TEST_RECIPIENT");

        try (var validators = Validation.buildDefaultValidatorFactory()) {
            new ApplicationContextRunner()
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("email"))
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    // Explicit mail auto-configuration only: no application scan, Auth, web server or database.
                    .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                    .withUserConfiguration(EmailConfiguration.class, SmtpEmailNotificationService.class)
                    .withBean(jakarta.validation.Validator.class, validators::getValidator)
                    .withPropertyValues("spring.profiles.active=email", "spring.mail.test-connection=false",
                            "spring.mail.properties[mail.debug]=false",
                            "spring.mail.properties[mail.debug.auth]=false")
                    .run(context -> {
                        if (context.getStartupFailure() != null) {
                            // Do not attach configuration/provider causes that could reveal secrets.
                            throw new IllegalStateException("Email context could not start; check SMTP configuration");
                        }
                        EmailNotificationService service = context.getBean(EmailNotificationService.class);
                        assertInstanceOf(SmtpEmailNotificationService.class, service);
                        assumeTrue(SEND_ATTEMPTED.compareAndSet(false, true),
                                "Only one live SMTP submission attempt is permitted per test process");
                        service.sendPasswordResetCode(recipient, DUMMY_RESET_CODE, 15);
                        System.out.println("SMTP submission completed; inbox delivery must be checked separately.");
                    });
        }
    }

    /** Pure guard used by automated tests; it never creates a mail sender or starts Spring. */
    static String recipientIfOptedIn(String optIn, String recipient) {
        if (!"true".equals(optIn) || recipient == null || recipient.isBlank()
                || recipient.contains("\r") || recipient.contains("\n")) {
            return null;
        }
        String normalized = recipient.trim();
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            return validators.getValidator().validateValue(EmailProperties.class, "from", normalized).isEmpty()
                    ? normalized : null;
        }
    }
}
