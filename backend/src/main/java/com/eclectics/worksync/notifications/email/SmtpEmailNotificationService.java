package com.eclectics.worksync.notifications.email;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Profile("email")
public class SmtpEmailNotificationService implements EmailNotificationService {
    private final JavaMailSender mailSender;
    private final Validator validator;
    private final String from;

    public SmtpEmailNotificationService(JavaMailSender mailSender, EmailProperties properties,
            Validator validator) {
        this.mailSender = mailSender;
        this.validator = validator;
        this.from = validatedAddress(properties.from());
    }

    @Override
    public void sendPasswordResetCode(String recipientEmail, String resetCode, int expiresInMinutes) {
        String recipient = validatedAddress(recipientEmail);
        // Only delivery-input checks: Auth decides code format, validity and actual expiration.
        if (resetCode == null || resetCode.isBlank() || resetCode.contains("\r") || resetCode.contains("\n")) {
            throw new IllegalArgumentException("A nonblank, single-line reset code is required");
        }
        if (expiresInMinutes <= 0) {
            throw new IllegalArgumentException("Expiration must be greater than zero");
        }
        send(recipient, PasswordEmailTemplates.RESET_SUBJECT,
                PasswordEmailTemplates.resetCode(resetCode, expiresInMinutes));
    }

    @Override
    public void sendPasswordChangedConfirmation(String recipientEmail) {
        send(validatedAddress(recipientEmail), PasswordEmailTemplates.CHANGED_SUBJECT,
                PasswordEmailTemplates.passwordChanged());
    }

    private String validatedAddress(String value) {
        String address = value == null ? null : value.trim();
        if (value == null || value.contains("\r") || value.contains("\n")
                || !validator.validateValue(EmailProperties.class, "from", address).isEmpty()) {
            // Do not attach rejected values or a message/code-bearing object to the exception.
            throw new IllegalArgumentException("A valid single email address is required");
        }
        return address;
    }

    private void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException failure) {
            // Do not log or retain failure: MailSendException may embed the entire failed email.
            throw new EmailDeliveryException();
        }
    }
}
