# ADR 0001: Start with a modular monolith

- Status: Accepted
- Date: 2026-11-10

## Context

Two backend developers and one frontend developer are establishing an Eclectics project and issue management platform. The first milestone is authentication, user management and basic notifications. Operational capacity and domain boundaries are still developing.

## Decision

Use one Spring Boot application, domain-owned packages, PostgreSQL and a standalone Blazor WebAssembly client in one repository. Enforce service/interface boundaries between domains; keep repositories private to their owning domain in usage. The first planned domains are Auth, Users and Notifications; create their packages only when implementing them. Use Flyway for schema evolution.

Do not introduce microservices, an API gateway, RabbitMQ or Kubernetes. Defer MongoDB and external identity platforms until concrete requirements justify a reviewed decision. The Module 1 API draft proposes JWT Bearer authentication within the monolith; token infrastructure is future implementation work and does not require microservices.

## Consequences

The team can run and debug the platform locally, share atomic database transactions and deploy one backend. Domain boundaries require review discipline; all domains share scaling and deployment lifecycle. Revisit extraction only when measured scaling, isolation or independent ownership needs outweigh distributed-system costs. A future extraction should preserve established domain interfaces.
