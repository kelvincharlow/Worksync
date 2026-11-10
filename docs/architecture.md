# Architecture

## Current skeleton

One repository contains a Spring Boot application (`backend/`), a standalone Blazor WebAssembly application (`frontend/`) and team documentation (`docs/`). Java 21, Maven Wrapper, .NET 10, PostgreSQL, JPA and Flyway are configured. The frontend has a static starting page and an injected HttpClient.

There are no business-domain packages, controllers, services, repositories, entities, DTOs, domain tables or JWT implementation. Spring Security uses its default protection with CSRF enabled. Its generated development login is framework behavior, not the planned Worksync login or initial System Administrator. No custom CORS policy exists yet. There are no business migrations; add the first versioned migration when persistence work begins. Hibernate validates schemas rather than creating or updating them.

## Planned modular monolith

Build one backend deployable, organized by business domain when the team implements each feature:

| Future domain | Ownership | Boundary |
| --- | --- | --- |
| users | User records, account status, system roles and user persistence | Expose operations through domain services/interfaces |
| auth | Authentication, JWT handling and Spring Security integration | Use Users services, never Users repositories |
| notifications | In-app notifications, delivery and email integration | Expose notification operations through domain services/interfaces |

Create controller, service, repository, entity and dto subpackages inside a domain only when needed. Do not pre-create unused layers or a global layer structure. Cross-domain repository access is prohibited. Keep any future `common/` limited to genuinely shared infrastructure. Coordinate service boundaries before parallel implementation.

Planned flow: Blazor page → API service using HttpClient → Spring Security → domain controller and validated DTO → domain service/transaction → domain repository → PostgreSQL. Return safe DTOs, never persistence entities or password hashes. Notification email delivery is best effort and must not roll back successful registration or approval.

## Module 1 contract direction — planned

[OpenAPI 0.2.0](openapi.yaml) is the supplied draft for team review. All endpoints are planned. JWT Bearer tokens replace the earlier undecided session/cookie proposal **in documentation only**. The skeleton does not implement token issuance, verification or storage.

Registration requires firstName, lastName, email, E.164 phoneNumber and password. New accounts have PENDING_APPROVAL status, USER role and no additional permissions; registration issues no token. ACTIVE and PENDING_APPROVAL users may log in. Pending users may access only their own profile, profile update and account-related notifications. REJECTED and BLOCKED accounts cannot log in, and status changes must promptly remove effective access from existing tokens.

Users have a single system role USER or ADMIN. PROJECT_CREATE is a later additional organization permission, represented as a string. Project-specific Manager/Member/Viewer roles and invitations are separate future work. Admin-only registration approval/rejection and active-account blocking/unblocking have distinct status transitions; backend authorization is authoritative.

When authentication is implemented, validate JWT signature, issuer, API audience, expiry and effective account status/permissions. Agree signing-key management, browser token storage, exact CORS origins and CSRF treatment before changing Spring Security defaults. Avoid long-lived tokens in localStorage. Use HTTPS. Passwords require an adaptive one-way encoder and must never be stored or logged in plaintext. Provision the first administrator separately through controlled setup, with no public role selection or committed password.

Server-side JWT logout/revocation, permission grants and role changes are later-phase operations. Discarding a browser token does not revoke it. Production readiness requirements are recorded in the [contract review notes](module1-contract-review-notes.md); they are not implemented by this skeleton.

Future domains include projects, work items, agile planning, workflows, reporting, collaboration, customization and selected integrations. No microservices, API gateway, RabbitMQ or Kubernetes are planned for this stage. MongoDB remains a future architecture decision. See [ADR 0001](decisions/0001-modular-monolith.md).
