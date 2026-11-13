package com.eclectics.worksync.notifications.email;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmailConfigurationTests {
    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(EmailConfiguration.class, SmtpEmailNotificationService.class)
                .withBean(jakarta.validation.Validator.class, () -> validatorFactory.getValidator());
    }

    @Test
    void defaultProfileNeedsNoSmtpSettingsAndProvidesNoFakeSender() {
        runner().run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(EmailNotificationService.class)
                    .doesNotHaveBean(EmailProperties.class).doesNotHaveBean(JavaMailSender.class);
        });
    }

    @Test
    void emailProfileRegistersServiceWithExplicitSenderWithoutSending() {
        JavaMailSender sender = mock(JavaMailSender.class);
        runner().withPropertyValues("spring.profiles.active=email", "worksync.email.from=security@example.com")
                .withBean(JavaMailSender.class, () -> sender)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmailNotificationService.class);
                    assertThat(context.getBean(EmailProperties.class).from()).isEqualTo("security@example.com");
                    verifyNoInteractions(sender);
                });
    }

    @Test
    void emailProfileWithoutSenderConfigurationFailsInsteadOfUsingDefaultAddress() {
        runner().withPropertyValues("spring.profiles.active=email")
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void emailProfileWithoutMailSenderFailsInsteadOfProvidingNoOpImplementation() {
        runner().withPropertyValues("spring.profiles.active=email", "worksync.email.from=security@example.com")
                .run(context -> assertThat(context).hasFailed());
    }
}
