# Notifications email delivery — Step 1

This is an internal SMTP delivery capability, prioritized for Auth password reset. It does not implement Auth, reset-code generation/validation/storage/consumption, rate limiting, account-enumeration policy, password changes, controllers or in-app notification persistence.

## Internal Java contract

Inject `com.eclectics.worksync.notifications.email.EmailNotificationService` into the Auth component when integrating on the shared application branch:

```java
emailNotificationService.sendPasswordResetCode(recipientEmail, codeFromAuth, expiresInMinutes);
emailNotificationService.sendPasswordChangedConfirmation(recipientEmail);
```

Auth supplies the recipient, its already-generated temporary code and the actual remaining lifetime in positive minutes. Notifications checks only delivery input (a single email address, a nonblank single-line code and positive expiration); it does not decide a code's format, validity or expiry policy. Addresses are trimmed, not rewritten. The sender comes from configuration, not a caller parameter. Confirmation content includes neither a code nor credentials. Templates are plain text in PasswordEmailTemplates; no template engine is needed.

These methods are synchronous and return void. Normal return means the configured SMTP operation completed without a reported error, not proof that the recipient's inbox received the message. Provider acceptance, later delivery, spam filtering and bounces are different stages. No public arbitrary-recipient/message API exists.

## Explicit activation and environment configuration

By default, no EmailNotificationService bean is registered. This is intentional: development without email needs no SMTP credentials, and there is no fake/no-op sender. To inject and use the SMTP implementation, activate the `email` Spring profile and provide SMTP_HOST and a valid SMTP_FROM. An Auth bean that requires this interface unconditionally will require that profile; coordinate an explicit application configuration rather than silently ignoring absent email delivery.

The Boot-managed mail starter configures JavaMailSender; `application-email.properties` applies only when the profile is active. It sets UTF-8, finite timeouts and mail protocol debug off. EmailProperties validates the configured sender. Startup with missing/invalid sender or without a configured mail sender fails; do not interpret that as a successful disabled service.

| Environment variable | Purpose / default when not set |
| --- | --- |
| SMTP_HOST | Required SMTP hostname when email is enabled |
| SMTP_FROM | Required authorized sender email; no fallback address |
| SMTP_PORT | Port; 587 |
| SMTP_USERNAME | Provider SMTP username; blank default for servers without authentication |
| SMTP_PASSWORD | Provider SMTP password/app password/token; blank default |
| SMTP_AUTH | Whether SMTP authentication is used; true |
| SMTP_STARTTLS_ENABLED | Negotiate STARTTLS; true |
| SMTP_STARTTLS_REQUIRED | Reject a connection without required STARTTLS; true |
| SMTP_SSL_ENABLED | Implicit TLS, typically used on port 465; false |
| SMTP_CONNECTION_TIMEOUT_MS | Connection timeout; 5000 milliseconds |
| SMTP_READ_TIMEOUT_MS | Read timeout; 5000 milliseconds |
| SMTP_WRITE_TIMEOUT_MS | Write timeout; 5000 milliseconds |

Keep timeouts positive and finite. TLS server identity checking is enabled. For STARTTLS use the provider's port (commonly 587) and keep STARTTLS enabled/required. For implicit TLS use the provider's documented port, enable SMTP_SSL_ENABLED and disable both STARTTLS flags. Do not disable certificate checks or trust arbitrary hosts. For a local SMTP catcher only, use its host/port, set SMTP_AUTH=false and both STARTTLS flags false, and keep SSL false. No SMTP catcher/container is added by this step.

`.env.example` contains variable names and empty credential/host/sender placeholders only. The real `.env` remains ignored. Do not put real usernames, passwords, app passwords or access tokens in Git, browser configuration or screenshots. Editing .env does not automatically configure a running Java process: export it into the same terminal before startup, as described in README.

From the repository root, after adding your actual settings to the ignored local .env:

```sh
set -a
. ./.env
set +a
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=email
```

Existing PostgreSQL setup is still required for normal backend startup. For a packaged application, activate via `SPRING_PROFILES_ACTIVE=email`; combine profile names explicitly if others are needed. No profile or credential changes are made automatically to the user's real .env.

Real sending requires an SMTP provider/account, its host/port and TLS mode, authentication credentials if required, and permission to send from SMTP_FROM. Domain/sender verification and provider DNS requirements must be completed with the selected provider. No real provider was configured or tested here. Do not claim production inbox delivery from unit tests or SMTP acceptance.

## Failure and security behavior

Invalid inputs raise fixed IllegalArgumentException messages without echoing rejected values or codes. SMTP MailException becomes EmailDeliveryException with the fixed message `Email could not be submitted to the SMTP server`. It intentionally omits the original cause: MailSendException can retain entire failed messages, codes and provider credentials. No message bodies, codes or provider exceptions are logged. Do not reintroduce cause logging or enable mail protocol debugging when diagnosing production incidents.

There are no automatic retries, durable queue, delivery confirmation or persistence. Failures are raised to the trusted caller; no successful send is fabricated. Auth must decide how to surface or monitor a failure safely, while preserving its account-enumeration and rate-limit protections. A provider failure can be ambiguous (for example, a disconnect after acceptance), so a manual retry can duplicate a message; do not promise exactly-once delivery.

The code-generation/storage transaction and email submission need an explicit integration order. Auth must ensure that the code it emails is already valid and committed. For password-change confirmations, send only after the **outermost** password-update transaction commits. Calling mail immediately after UserService.resetPassword inside an enclosing Auth transaction is too early. The delivery service does not start database transactions or alter the user; Auth owns after-commit orchestration. Email failure must not undo a committed password change. Simple in-memory callbacks are not durable delivery; an outbox/retry design requires a separate reviewed task.

## Tests and verification

SmtpEmailNotificationServiceTests uses mocked JavaMailSender. It checks configured from/to, exact subjects, code and lifetime text, confirmation instructions, invalid addresses/header injection, invalid codes/expiry, invalid sender and sanitized send/authentication failures. The failure fixture deliberately contains sensitive-looking text to prove it is absent from the safe exception and cause chain.

EmailConfigurationTests checks explicit profile enablement, absence of a fake service by default, and failure when enabled without required dependencies/settings. EmailSmtpSettingsTests loads the real profile with fixture properties and an auto-configured sender but **never calls send or opens a connection**; it verifies binding of authentication, TLS, timeouts and debug settings. No real emails are sent in automated tests. Full backend tests run without the email profile or real SMTP credentials.

```sh
cd backend
./mvnw -Dtest=SmtpEmailNotificationServiceTests,EmailConfigurationTests,EmailSmtpSettingsTests test
# Full backend suite needs a disposable PostgreSQL test database and TEST_DB_*:
./mvnw test
```

This service does not change Users, Auth or their migrations. Existing user validation, persistence and security ownership remain intact. The Auth developer still needs to inject this interface, activate/configure SMTP in the intended environment, supply the actual code lifetime, orchestrate commit ordering, handle safe failures and add end-to-end reset-workflow tests. No Auth code needs to be present to unit-test Notifications itself.

## Files for this implementation

Created under `backend/src/main/java/com/eclectics/worksync/notifications/email/`:

- EmailNotificationService.java: trusted internal interface.
- SmtpEmailNotificationService.java: validated SMTP adapter with constructor injection.
- EmailDeliveryException.java: sanitized failure without a sensitive provider cause.
- EmailProperties.java: validated sender configuration.
- EmailConfiguration.java: profile-gated properties registration.
- PasswordEmailTemplates.java: plain-text content and fixed subjects.

Created `backend/src/main/resources/application-email.properties` and three corresponding test classes under `backend/src/test/java/com/eclectics/worksync/notifications/email/`: SmtpEmailNotificationServiceTests.java, EmailConfigurationTests.java and EmailSmtpSettingsTests.java.

Modified backend/pom.xml (Boot-managed mail starter), .env.example (empty SMTP placeholders), README.md and docs/architecture.md (email setup/status links), and docs/notifications-design.md (historical in-app assessment scope note). This document is new. No changes to Auth, Users source, frontend, database migrations or the user's real .env are part of this implementation.

References: [Spring Boot mail configuration and timeout guidance](https://docs.spring.io/spring-boot/reference/io/email.html), [Spring email abstraction](https://docs.spring.io/spring-framework/reference/integration/email.html).

## Executed results

Verified on 2026-11-12 with Java 21. Compilation passed. The complete backend suite passed against disposable PostgreSQL 17: **124 tests, zero failures/errors/skips**, including 30 new email tests. The temporary database container and anonymous volumes were removed afterward; the application's database was not used. No real SMTP connection or email delivery was attempted. Expected startup warnings in two negative configuration tests verify fail-fast behavior and are not suite failures.

## Developer-only real SMTP check

A guarded [manual integration test](email-manual-test.md) can send one real reset-code test email through this service when explicitly selected with both opt-in and a valid owned recipient. It is excluded from normal Maven test discovery and application startup. Read the runbook before running it; automated tests and implementation verification do not send live mail.
