# First administrator: explicit operator bootstrap

This is first-install provisioning, not public registration, role management or account recovery.
Normal startup never runs it. No HTTP endpoint exposes it, and the normal application does not
register its service. All credentials are supplied in an interactive terminal at execution time.

## What it does

The separate `com.eclectics.worksync.users.bootstrap.FirstAdminBootstrap` entry point:

1. Requires exactly `--create-first-admin`, a real terminal and explicit database environment.
2. Requires the operator to type `CREATE FIRST ADMIN` after verifying the selected database.
3. Reads first name, last name, email, E.164 phone and password/confirmation without echoing them.
4. Starts only database/JPA/validation/transaction configuration, using the same password-encoder
   bean as Auth. It starts no web server, Auth controllers, JWT services or SMTP transport.
5. Acquires the existing PostgreSQL Users lifecycle advisory lock `(1465075027, 1)` inside a
   READ_COMMITTED transaction. Concurrent bootstrap commands cannot both pass the admin check.
6. Refuses if an ACTIVE ADMIN exists. It also refuses if any inactive ADMIN exists: recovering
   a blocked/rejected administrator requires a separate, reviewed process.
7. Refuses an already registered normalized email. It never promotes or overwrites an existing user.
8. Stores one new ADMIN/ACTIVE account with UUID and timestamps, using BCrypt through the unchanged
   shared PasswordEncoder bean. Ordinary registration continues to create USER/PENDING_APPROVAL.
9. Reports success only after commit. Failure exits nonzero without printing provider exceptions,
   SQL, connection values, rejected account fields, raw passwords or hashes. Password buffers are
   cleared best-effort; Java/encoder internal copies cannot be guaranteed erased from JVM memory.

The password must be nonblank, at least 12 characters, no more than 128 characters and no more
than 72 UTF-8 bytes (BCrypt's limit). Spaces are not silently trimmed from passwords. Other account
fields use the existing Users validation rules, including names at most 80 characters and E.164 phone.

No schema migration is added. The CLI validates the schema and deliberately does not run Flyway.
Deploy the normal application/migrations successfully before provisioning. Direct SQL and future
role-changing tools must respect the same locking protocol. An existing admin record blocks reruns;
this is not a permanent bootstrap audit-marker system if an operator deletes all administrators.

## Render limitations

A successful deploy must contain the newly built executable backend JAR. It must be run with Java 21.
The deployed service's environment must have `DB_URL`, `POSTGRES_USER` and `POSTGRES_PASSWORD`, matching
its actual database. DB_URL must be the PostgreSQL JDBC value used by the backend, not an unconverted
provider URL. Never print it or paste database/admin credentials into tickets, shell commands or chat.

Render's Shell/SSH features depend on the service/compute plan. Ephemeral shells require compatible
paid compute and a successful deploy; Free web services do not provide Shell/SSH. Check current
[Render SSH documentation](https://render.com/docs/ssh) and [Free service limitations](https://render.com/docs/free).
Use an interactive SSH terminal with a PTY. If `System.console()` is unavailable, the command refuses
before opening a database connection. Do not remove that protection or put credentials in arguments.

If no Render shell is available, use the external-database procedure below from a trusted operator
machine. An internal Render database hostname may not be reachable externally. Verify the database
provider's external endpoint, TLS requirements and network access controls. This procedure neither
changes those controls nor bypasses them.

## Production procedure A: interactive service shell/SSH

Do not configure this as the Render start/build/pre-deploy command. It is a separate one-time action.
No permanent ADMIN_* environment variables or enabled startup profile are required or supported.

1. Render currently deploys from `chore/render-backend`. This feature branch is for review only;
   deployment requires a separately authorized integration into that deployment branch.
   Deploy the reviewed JAR and normal database migrations. Confirm the service uses the intended
   production database. Do not infer this merely from a database name or the public API hostname.
2. Open the backend service's interactive shell/SSH session. Confirm the three required database
   variables exist using the Render environment settings; do not echo their values into the terminal.
3. The preserved Render `backend/Dockerfile` packages with Java 21 and copies the executable
   JAR to `/app/app.jar` in its Java 21 runtime image. Use that path in Render Shell. For a
   non-container Maven build, the alternative from `backend/` is
   `target/worksync-0.0.1-SNAPSHOT.jar`. Do not use the `.jar.original` archive.
4. Run this exact Render-container command; it does not replace the normal service start command:

```bash
WORKSYNC_BOOTSTRAP_JAR=/app/app.jar
java -Dloader.main=com.eclectics.worksync.users.bootstrap.FirstAdminBootstrap \
  -cp "$WORKSYNC_BOOTSTRAP_JAR" \
  org.springframework.boot.loader.launch.PropertiesLauncher --create-first-admin
```

5. Confirm the target when prompted, then enter the account fields and a password from the
   approved password manager. All field entry is hidden. No password appears in shell history,
   process arguments, application configuration or source control.
6. A zero exit with `First ACTIVE ADMIN committed successfully` means the transaction committed.
   Exit 2 means missing guard/input/environment or cancellation before database work. Exit 1 means
   failure/refusal. Do not keep retrying blindly after a network/commit failure: the insert might
   have committed before a connection error. Inspect administrator existence through an approved
   read-only database procedure first.
7. Verify login over HTTPS using the new account, then access an authorized administrator read
   endpoint. Use the correct frontend CORS origin. A local administrator is not copied to this DB.
   Do not record the password or token in logs or test scripts.
8. Close the operator session and remove any temporary credentials from the operator environment.
   No bootstrap switch remains enabled, and normal deployments cannot recreate the account.

The alternate main class uses PropertiesLauncher from the executable Spring Boot JAR. The normal
`java -jar ...` entry point remains WorksyncApplication; passing a bootstrap flag to that normal
entry point is not the provisioning procedure.

## Production procedure B: trusted operator machine without Render shell

Use the exact reviewed artifact built for deployment, Java 21 and a terminal. Do not point your
normal local application or its `.env` at production just to run bootstrap. Open a dedicated Bash
subshell, and supply the provider's external database settings through hidden input:

```bash
bash
read -r -s -p 'Production JDBC database value: ' DB_URL; printf '\n'; export DB_URL
read -r -s -p 'Production database username: ' POSTGRES_USER; printf '\n'; export POSTGRES_USER
read -r -s -p 'Production database password: ' POSTGRES_PASSWORD; printf '\n'; export POSTGRES_PASSWORD
WORKSYNC_BOOTSTRAP_JAR=/absolute/path/to/reviewed/worksync-0.0.1-SNAPSHOT.jar
java -Dloader.main=com.eclectics.worksync.users.bootstrap.FirstAdminBootstrap \
  -cp "$WORKSYNC_BOOTSTRAP_JAR" \
  org.springframework.boot.loader.launch.PropertiesLauncher --create-first-admin
unset DB_URL POSTGRES_USER POSTGRES_PASSWORD WORKSYNC_BOOTSTRAP_JAR
exit
```

Those are input prompts: never replace them with literal credential assignments in saved commands.
The operator is responsible for choosing the external endpoint of the *same* intended production
PostgreSQL database and preserving the provider's TLS options. The CLI has no local fallback and
ignores arbitrary Spring datasource/profile/debug overrides. It only imports the three explicit
DB environment values into its isolated configuration; it does not load the project's private .env.
Do not run either procedure during code review or automated builds.

## Tests and operational scope

Unit tests cover explicit-action/terminal/environment guards, cancelled input, password wiping,
account validation, encoder usage and refusal conditions. PostgreSQL tests invoke only the service
against disposable data, covering correct fields/hash, existing admins, no promotion, rollback,
snapshot rejection and concurrent first-admin creation. The isolated test configuration applies
migrations only to the test database. Normal-startup tests verify the service is absent.

Production CLI execution is intentionally not performed by tests or during implementation. There is
no production administrator created by this change. Existing credentials and all application users
remain unchanged until an operator deliberately executes one of the procedures above.
