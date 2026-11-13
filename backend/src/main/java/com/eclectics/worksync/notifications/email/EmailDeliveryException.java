package com.eclectics.worksync.notifications.email;

/** Safe delivery failure. Provider exceptions may contain reset codes, messages or credentials. */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException() {
        // Deliberately omit the provider cause and its potentially sensitive message/failed messages.
        super("Email could not be submitted to the SMTP server");
    }
}
