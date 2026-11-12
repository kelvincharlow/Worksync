package com.eclectics.worksync.notifications.email;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads the real profile configuration and auto-configured sender, but never calls send(). */
class EmailSmtpSettingsTests {
    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void emailProfileBindsEnvironmentSettingsAndFiniteTimeoutsWithoutConnecting() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("email"))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(EmailConfiguration.class, SmtpEmailNotificationService.class)
                .withBean(jakarta.validation.Validator.class, () -> validatorFactory.getValidator())
                .withPropertyValues("SMTP_HOST=smtp.example.invalid", "SMTP_FROM=security@example.com",
                        "SMTP_PORT=2525", "SMTP_USERNAME=username-fixture", "SMTP_PASSWORD=credential-fixture",
                        "SMTP_AUTH=true", "SMTP_STARTTLS_ENABLED=true", "SMTP_STARTTLS_REQUIRED=true",
                        "SMTP_SSL_ENABLED=false", "SMTP_CONNECTION_TIMEOUT_MS=4000",
                        "SMTP_READ_TIMEOUT_MS=6000", "SMTP_WRITE_TIMEOUT_MS=7000")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmailNotificationService.class);
                    var sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("smtp.example.invalid");
                    assertThat(sender.getPort()).isEqualTo(2525);
                    assertThat(sender.getUsername()).isEqualTo("username-fixture");
                    assertThat(sender.getPassword()).isEqualTo("credential-fixture");
                    assertThat(sender.getDefaultEncoding()).isEqualTo("UTF-8");
                    var properties = sender.getJavaMailProperties();
                    assertThat(properties.getProperty("mail.smtp.auth")).isEqualTo("true");
                    assertThat(properties.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
                    assertThat(properties.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
                    assertThat(properties.getProperty("mail.smtp.ssl.enable")).isEqualTo("false");
                    assertThat(properties.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
                    assertThat(properties.getProperty("mail.smtp.connectiontimeout")).isEqualTo("4000");
                    assertThat(properties.getProperty("mail.smtp.timeout")).isEqualTo("6000");
                    assertThat(properties.getProperty("mail.smtp.writetimeout")).isEqualTo("7000");
                    assertThat(properties.getProperty("mail.debug")).isEqualTo("false");
                });
    }
}
