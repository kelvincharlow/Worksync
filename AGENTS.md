# Repository instructions

- Inspect existing code before changing it. Keep backend/, frontend/ and docs/ in one repository.
- Java 21 / Spring Boot modular monolith. When implementation is requested, organize under com.eclectics.worksync by auth, users, notifications and future business domains; do not pre-create placeholder domain packages. Add controller/service/repository/entity/dto subpackages only as needed.
- Users owns records/status/persistence; Auth owns authentication/Spring Security; Notifications owns delivery/email. Cross-domain calls use defined services/interfaces, never repositories. Keep common infrastructure minimal.
- Frontend is standalone Blazor WebAssembly on .NET 10. Use injected HttpClient in API services and explicit models. Browser configuration is public.
- Do not implement planned features unless asked. Keep this starting point a skeleton. No fake login, hardcoded admin credentials, permit-all shortcuts, plaintext password storage, Keycloak, microservices, gateway, RabbitMQ, Kubernetes or MongoDB. JWT is planned in docs/openapi.yaml, not authorized for implementation by its presence in the draft.
- Keep backend authorization authoritative. Retain CSRF protection until a reviewed authentication design says otherwise. Keep framework security defaults until real security implementation is requested.
- Use Flyway migrations, never Hibernate schema updates. Never modify applied shared migrations.
- Never commit secrets or real .env files. Do not commit, push or change remote branches without explicit authorization.
- Backend check: cd backend && ./mvnw verify (requires TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD pointing to disposable PostgreSQL; see README). Windows: mvnw.cmd.
- Frontend checks from root: dotnet build frontend/Worksync.Frontend.csproj -c Release; dotnet publish frontend/Worksync.Frontend.csproj -c Release --no-build.
- Infrastructure check: docker compose --env-file .env.example config --quiet.
- The supplied Module 1 draft is documented in docs/openapi.yaml; keep docs/api-contract.md, architecture and contract review notes consistent. All business endpoints remain planned.
- Add meaningful tests for implemented behavior and update docs/contracts. Clearly report checks blocked by unavailable tools/services.
