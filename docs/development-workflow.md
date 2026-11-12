# Development workflow

Use `main` as the shared integration branch. Work on short-lived `feat/<topic>`, `fix/<topic>` or `docs/<topic>` branches; do not push directly to main. Keep pull requests focused, explain behavior and validation, update API contracts with changes and obtain at least one teammate review. Configure required CI checks and branch protection in GitHub after pushing; workflow files alone do not configure protection.

With two backend developers, assign domain ownership per change and agree public service interfaces before parallel work. Create domain packages and layers only as their implementation begins; the starting skeleton deliberately contains none. The frontend developer works against reviewed API DTOs; document pending integration instead of adding fake production authentication. Each developer runs an independent PostgreSQL volume and keeps secrets in ignored local configuration.

Java packages use lowercase domain names under `com.eclectics.worksync`; classes use PascalCase and methods camelCase. C# uses PascalCase types/public members, camelCase locals and async methods ending in Async. Prefer explicit DTOs at API boundaries, constructor injection and domain-owned transactions. Do not access another domain's repositories. Add layers only when needed.

Use versioned Flyway migrations `V<number>__description.sql`; coordinate numbering and never edit a migration already applied on shared environments. Do not use Hibernate schema auto-update. Store only safe example config in Git; browser configuration is public.

Run `(cd backend && ./mvnw verify)` against a disposable PostgreSQL test database, `dotnet build frontend/Worksync.Frontend.csproj -c Release` and `dotnet publish frontend/Worksync.Frontend.csproj -c Release --no-build` before requesting review. See README for TEST_DB variables. The backend has an application-context smoke test, Users repository/service integration tests and Users entity/DTO/service unit tests. CI runs the full suite with PostgreSQL. Endpoint and security tests will accompany their implementations. Add focused service/controller tests with each business behavior and frontend component/browser tests as interactive functionality appears. No frontend behavior test suite exists for the static foundation page.

Review authorization, pending-account restrictions, input validation, migration safety, API compatibility and absence of secrets. CI must pass; never treat skipped integration tests as a full verification. Use descriptive imperative commits. Agents must not commit, push or modify remote branches unless explicitly requested.

The Module 1 OpenAPI draft is in `docs/openapi.yaml`, with review context in `docs/module1-contract-review-notes.md` and a summary in `docs/api-contract.md`. Keep all three consistent. JWT and the domain endpoints are planned documentation only. Review the draft together before assigning endpoint ownership, including later-phase markers, DTO fields and error shapes.
