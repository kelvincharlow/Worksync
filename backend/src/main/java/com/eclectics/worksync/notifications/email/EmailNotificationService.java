package com.eclectics.worksync.notifications.email;

/** Internal Java delivery boundary. Auth owns codes, verification, rate limits and reset policy. */
public interface EmailNotificationService {
    /** Submits the supplied code to SMTP; normal return does not prove inbox delivery. */
    void sendPasswordResetCode(String recipientEmail, String resetCode, int expiresInMinutes);

    /** Call only after a password change commits; does not change the account itself. */
    void sendPasswordChangedConfirmation(String recipientEmail);
}
