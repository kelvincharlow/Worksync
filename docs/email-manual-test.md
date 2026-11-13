# One real SMTP email — developer-only manual test

This manual check calls the real WorkSync EmailNotificationService through its SMTP implementation. It is not an Auth password-reset flow. The supplied test-only code is not generated, stored or verified by Auth and cannot reset an account.

## Safety gates and default behavior

`SmtpEmailManualIntegrationTest` is excluded from normal Maven `test` and `verify` discovery by the Surefire `*ManualIntegrationTest.java` exclusion. It is test-source code only: it is not an application runner, controller or startup hook.

Even when explicitly selected, it skips before starting Spring or creating a sender unless BOTH conditions hold:

1. WORKSYNC_SEND_LIVE_EMAIL_TEST is exactly `true` (case-sensitive, no extra spaces).
2. EMAIL_TEST_RECIPIENT is a nonblank, valid single email address no longer than 254 characters, without CR/LF header injection. Surrounding spaces are trimmed. Use an address you own; syntactic validation cannot establish ownership.

A method-level JUnit environment condition and an in-method guard enforce opt-in. If the recipient is absent/invalid, the test aborts as skipped, not as a failed send. Missing SMTP credentials with valid gates is a configuration failure, not a skip or pretend success.

The class has one test method and calls sendPasswordResetCode once, with a clearly labeled dummy code and 15-minute expiry. An in-process attempt guard prevents another send attempt in that JVM. The command below disables Surefire failure reruns. Do not wrap it in a retry loop, set other test-repeat options or run it again without intending another real email. A timeout after provider acceptance can be ambiguous; inspect Brevo before retrying.

## Exact command — run yourself from backend/

Before running: verify the root private .env contains your real SMTP_HOST, SMTP_USERNAME (Brevo SMTP login), SMTP_PASSWORD (Brevo SMTP key, not API key/account password) and SMTP_FROM (authorized sender), plus the intended port/auth/TLS settings. For Brevo port 587 use STARTTLS enabled and required, SMTP_AUTH=true and implicit SSL=false. No credentials or recipient are stored in this document or the test source.

**The following command is intended to send ONE real email. Do not run it until you are ready.**

```sh
# Start in the backend/ directory. Load the existing root .env without displaying it.
set -a
. ../.env
set +a

WORKSYNC_SEND_LIVE_EMAIL_TEST=true \
EMAIL_TEST_RECIPIENT='replace-with-an-address-you-own@example.com' \
./mvnw -Dtest=SmtpEmailManualIntegrationTest \
  -Dsurefire.rerunFailingTestsCount=0 test
```

Replace the recipient placeholder before running. The flags apply only to that Maven invocation; do not permanently export the opt-in or put it into .env/CI. Maven's explicit `-Dtest` selection overrides normal includes/excludes. See [Surefire selection behavior](https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html).

The test activates the actual `email` Spring profile and loads application-email.properties. There is no need to start the full backend, Docker, PostgreSQL or Auth. Its small ApplicationContextRunner imports only mail auto-configuration, EmailConfiguration, SmtpEmailNotificationService and a Jakarta Validator. It does not scan the application. It forces protocol/auth debug and test-connection behavior off, so startup does not separately probe SMTP and secrets/code-bearing bodies are not deliberately logged.

Do not print SMTP variables, enable mail debugging, use Maven -X to share credentials or paste .env into a ticket/chat. The test does not read .env itself: the shell export step is required. Your private .env is neither modified nor copied by the test.

## Interpreting the outcome

- **Tests run: 1, Skipped: 1:** a gate is missing. No mail context or SMTP submission was attempted. Check opt-in and recipient locally without printing credentials.
- **Email context could not start; check SMTP configuration:** check exported variables and authorized sender syntax. No database/Auth setup is needed. The wrapper intentionally does not attach possibly sensitive startup causes.
- **Email could not be submitted to the SMTP server:** the existing sanitized EmailDeliveryException is propagated. Check host, port, network access, SMTP login/key, provider activation, sender authorization and compatible TLS settings. Use Brevo transactional logs/account status to narrow the issue; the generic exception intentionally omits provider credentials and message contents. A failure does not guarantee the provider rejected the mail before accepting it.
- **SMTP submission completed; inbox delivery must be checked separately**, and one passing test: the real Java service completed without a reported SMTP error. Check the intended inbox/spam folder and Brevo transactional logs. This is evidence of submission, not proof of final inbox delivery.

A timeout might happen after acceptance; avoid repeatedly rerunning until you inspect the provider logs. No automatic retry, delivery polling, notification storage or password modification is implemented by this test.

## Automated verification without live mail

Normal email tests use mocked sending or only inspect configuration. ManualEmailTestGuardTests invokes only the pure gating helper with synthetic values, never the real manual method, Spring context or mail sender. It covers missing/false/malformed opt-in, missing/invalid/multiple/header-injected recipients, a length violation and valid input acceptance. A valid helper result alone sends nothing.

```sh
./mvnw -Dtest=SmtpEmailNotificationServiceTests,EmailConfigurationTests,EmailSmtpSettingsTests,ManualEmailTestGuardTests test
```

To explicitly demonstrate a skipped manual check without allowing any live send, remove both environment gates for that invocation:

```sh
env -u WORKSYNC_SEND_LIVE_EMAIL_TEST -u EMAIL_TEST_RECIPIENT \
  ./mvnw -Dtest=SmtpEmailManualIntegrationTest \
  -Dsurefire.rerunFailingTestsCount=0 test
```

The implementation verification uses only these non-sending paths. No live run is authorized as part of implementation. Ordinary full backend tests still require their existing disposable PostgreSQL TEST_DB_* configuration; those are separate from this database-free manual SMTP check.

## Files in this step

- `backend/src/test/java/com/eclectics/worksync/notifications/email/SmtpEmailManualIntegrationTest.java`: guarded, developer-selected real-service test.
- `backend/src/test/java/com/eclectics/worksync/notifications/email/ManualEmailTestGuardTests.java`: non-sending guard tests.
- `backend/pom.xml`: default manual-test exclusion; plugin version remains Boot-managed.
- `docs/email-manual-test.md`: this runbook.
- `docs/email-delivery.md`: link to this runbook.

No production email behavior, private .env, Auth, Users, database schema or SMTP credentials are changed. No commit or push is part of this task.

## Implementation verification results

On 2026-11-12, 49 targeted email/guard tests passed (30 existing email tests and 19 guard cases). Explicit selection with both environment gates removed produced one skipped manual test before any mail context was created. The ordinary full backend suite passed 143 tests with no failures/errors/skips and excluded the manual class. PostgreSQL verification used a temporary isolated container that was removed afterward. No live email was sent, no private .env was read/modified, and no SMTP credentials were inspected. The actual Brevo send remains for the developer's explicit review and execution.
