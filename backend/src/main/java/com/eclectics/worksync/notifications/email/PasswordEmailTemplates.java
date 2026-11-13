package com.eclectics.worksync.notifications.email;

/** Plain-text templates only. No code generation, validation, storage or delivery side effects. */
final class PasswordEmailTemplates {
    static final String RESET_SUBJECT = "Reset your WorkSync password";
    static final String CHANGED_SUBJECT = "Your WorkSync password was changed";

    private PasswordEmailTemplates() {
    }

    static String resetCode(String code, int minutes) {
        return """
                You requested a password reset for your WorkSync account.

                Your temporary reset code is: %s

                This code expires in %d minutes. Do not share it with anyone.
                If you did not request this reset, you can ignore this email.
                """.formatted(code, minutes);
    }

    static String passwordChanged() {
        return """
                Your WorkSync account password was changed successfully.

                If you did not authorize this change, contact your WorkSync administrator
                immediately to secure your account.
                """;
    }
}
