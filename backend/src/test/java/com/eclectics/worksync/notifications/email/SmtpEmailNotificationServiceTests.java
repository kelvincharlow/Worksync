package com.eclectics.worksync.notifications.email;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailNotificationServiceTests {
    private static final String FROM = "security@example.com";
    private static final String RECIPIENT = "jane@example.com";
    private static final String CODE = "reset-code-fixture";
    private static ValidatorFactory validatorFactory;
    @Mock JavaMailSender mailSender;
    private EmailNotificationService service;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        service = new SmtpEmailNotificationService(mailSender, new EmailProperties(FROM),
                validatorFactory.getValidator());
    }

    @Test
    void submitsResetEmailWithConfiguredSenderCodeAndExpiration() {
        service.sendPasswordResetCode(" " + RECIPIENT + " ", CODE, 15);

        var message = capturedMessage();
        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly(RECIPIENT);
        assertThat(message.getSubject()).isEqualTo("Reset your WorkSync password");
        assertThat(message.getText()).contains(CODE, "expires in 15 minutes", "ignore this email")
                .doesNotContain("passwordHash");
        assertThat(message.getCc()).isNull();
        assertThat(message.getBcc()).isNull();
    }

    @Test
    void submitsChangeConfirmationWithoutResetCodeOrCredentials() {
        service.sendPasswordChangedConfirmation(RECIPIENT);

        var message = capturedMessage();
        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly(RECIPIENT);
        assertThat(message.getSubject()).isEqualTo("Your WorkSync password was changed");
        assertThat(message.getText()).contains("changed successfully", "did not authorize", "administrator")
                .doesNotContain(CODE, "reset code", "passwordHash", "encoded-hash-fixture");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid", "jane@", "a@example.com,b@example.com", "jane@example.com\r\nBcc: other@example.com", "jane@example.com\n"})
    void rejectsInvalidRecipientsWithoutSending(String recipient) {
        assertThatThrownBy(() -> service.sendPasswordResetCode(recipient, CODE, 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A valid single email address is required");
        assertThatThrownBy(() -> service.sendPasswordChangedConfirmation(recipient))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mailSender);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "secret\nvalue", "secret\rvalue"})
    void rejectsMissingOrMultilineCodesWithoutEchoingThem(String code) {
        assertThatThrownBy(() -> service.sendPasswordResetCode(RECIPIENT, code, 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A nonblank, single-line reset code is required");
        verifyNoInteractions(mailSender);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidExpiration(int minutes) {
        assertThatThrownBy(() -> service.sendPasswordResetCode(RECIPIENT, CODE, minutes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expiration must be greater than zero");
        verifyNoInteractions(mailSender);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid", "sender@example.com\n"})
    void rejectsInvalidSenderConfiguration(String from) {
        assertThatThrownBy(() -> new SmtpEmailNotificationService(mailSender,
                new EmailProperties(from), validatorFactory.getValidator()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mailSender);
    }

    @Test
    void sanitizesSmtpFailureIncludingFailedMessageAndProviderCredentials() {
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            throw new MailSendException("provider-credential-fixture " + CODE,
                    new IllegalStateException(message.toString()));
        }).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.sendPasswordResetCode(RECIPIENT, CODE, 15))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Email could not be submitted to the SMTP server")
                .hasNoCause()
                .satisfies(failure -> {
                    var text = new StringWriter();
                    failure.printStackTrace(new PrintWriter(text));
                    assertThat(text.toString()).doesNotContain(CODE, RECIPIENT, "provider-credential-fixture");
                });
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void confirmationAuthenticationFailureDoesNotPretendToSucceed() {
        doThrow(new MailAuthenticationException("smtp-password-fixture"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.sendPasswordChangedConfirmation(RECIPIENT))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageNotContaining("smtp-password-fixture")
                .hasNoCause();
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    private SimpleMailMessage capturedMessage() {
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
