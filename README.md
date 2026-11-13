# Worksync

Eclectics project and issue management platform. This repository is a collaboration-ready project skeleton. Users steps 1–4 provide persistence, DTOs, basic profile operations and internal administrator status/listing operations; HTTP endpoints and Worksync authentication remain future work. See [Users persistence](docs/users-persistence.md).

- `backend/`: Java 21, Spring Boot 4.1.2, Maven Wrapper, modular monolith.
- `frontend/`: standalone Blazor WebAssembly, .NET 10 LTS, C# and HttpClient.
- `docs/`: architecture, planned API contract, workflow and decisions.

## Prerequisites

Install JDK 21, a supported .NET 10 SDK (10.0.403 or newer stable 10.0 feature band), Docker Engine/Desktop with Compose, and Git. Maven is downloaded by the committed wrapper on first use. Ensure Docker is running. Internet access is needed for the first dependency restore.

## Local setup

From the repository root:

```sh
cp .env.example .env
# Edit .env and replace POSTGRES_PASSWORD with a random local value.
docker compose up -d --wait postgres
```

Compose reads `.env` automatically. Spring Boot does not; export the same variables before starting the backend. The example file uses shell-compatible values. Quote values appropriately if adding special characters.

```sh
set -a
. ./.env
set +a
cd backend
./mvnw spring-boot:run
```

Windows: use `mvnw.cmd` and set the variables from `.env` in your shell before running it. The default backend port is 8080. Startup connects to PostgreSQL and initializes Flyway; V1 creates the users table. Spring Security uses its framework defaults, including CSRF protection and a generated development user/password printed at startup. This is not Worksync authentication or the initial System Administrator. No application API endpoints exist.

In a second terminal, from the root:

```sh
dotnet restore frontend/Worksync.Frontend.csproj
dotnet run --project frontend/Worksync.Frontend.csproj
```

Open http://localhost:5000. `frontend/wwwroot/appsettings.json` supplies the public API base URL. All WebAssembly configuration is downloadable: never put secrets there. Deployment must supply the correct public URL in this JSON before publishing; server environment variables do not automatically configure static WebAssembly files. Local backend environment overrides include `DB_URL` and `SERVER_PORT`. JWT Bearer authentication is documented as planned; no custom security or CORS configuration exists yet. Configure exact origins and review CSRF behavior when implementing authentication. Use HTTPS in deployment.

## Build and verification

Backend integration tests require a **separate, disposable PostgreSQL database**. The context smoke test exercises startup; Users repository integration tests cover persistence, defaults, email uniqueness/lookups, pagination and timestamps. Users entity/service tests cover basic operations, validation, allowed/invalid status transitions, pagination and persisted timestamps. Internal administrator operations still require authorization during future secured-controller integration. Security tests belong with the future Auth implementation. Do not point tests at a shared or production database. Example using the local PostgreSQL container:

```sh
docker compose exec postgres sh -c 'createdb -U "$POSTGRES_USER" worksync_test'
# Export POSTGRES_USER and POSTGRES_PASSWORD from .env as above.
export TEST_DB_URL=jdbc:postgresql://localhost:5432/worksync_test
export TEST_DB_USER="$POSTGRES_USER"
export TEST_DB_PASSWORD="$POSTGRES_PASSWORD"
(cd backend && ./mvnw verify)
dotnet build frontend/Worksync.Frontend.csproj --configuration Release
dotnet publish frontend/Worksync.Frontend.csproj --configuration Release --no-build
docker compose --env-file .env.example config --quiet
```

If you changed POSTGRES_PORT, change TEST_DB_URL accordingly. Create the test database once. To compile/package without executing database tests: `(cd backend && ./mvnw package -DskipTests)`. This is not a substitute for CI integration tests. Published frontend assets are in `frontend/bin/Release/net10.0/publish/wwwroot`; a static host must fall back to `index.html` for client routes.

Stop infrastructure with `docker compose down`. The database volume persists. Changing `.env` does not change credentials in an existing database volume; use SQL to rotate them. Only for disposable local data, `docker compose down -v` deletes the database completely.

## Team workflow and next milestone

Use short feature branches and reviewed pull requests into `main`. CI builds/tests the backend with PostgreSQL and builds/publishes the frontend. Each developer keeps an independent `.env` and local database. Review `docs/openapi.yaml` and agree contract changes before parallel implementation. See [development workflow](docs/development-workflow.md) and [architecture](docs/architecture.md).

The supplied Module 1 draft is recorded in [OpenAPI](docs/openapi.yaml) and summarized in the [API contract](docs/api-contract.md). It proposes JWT login, required phone numbers, profile updates, registration approval/rejection and account blocking. JWT revocation/logout, permission grants and role changes are later-phase work. Next, the team should review the draft and split implementation tasks; the Users module currently includes entity, repository, dto, service and exception packages. All milestone endpoints are [PLANNED](docs/api-contract.md). Future scope includes projects, work items, agile planning, workflows, reporting, collaboration, customization and selected integrations. MongoDB remains a future architecture decision.

Version references: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [.NET 10](https://dotnet.microsoft.com/en-us/download/dotnet/10.0).

## Optional password-reset email delivery

Notifications provides an internal EmailNotificationService for Auth integration. SMTP sending is explicitly enabled with the `email` Spring profile and environment settings; it is absent by default rather than a fake sender. See [email delivery setup, failure handling and tests](docs/email-delivery.md). No email endpoints, Auth workflow or in-app notification tables are implemented by this capability.
