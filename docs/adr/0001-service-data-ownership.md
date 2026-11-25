# ADR 0001 — Service data ownership

**Status** Proposed for review on 2026-11-25.

## Context

Auth, course, and order already have separate PostgreSQL database names and JPA configuration. Course also has a MongoDB configuration. AI declares PostgreSQL properties without the corresponding persistence dependencies. The code contains no domain entities, service-owned migrations, or documented MongoDB collections. A separate SQL design in the repository models seven PostgreSQL databases, including a learning domain without a corresponding Maven module. These facts establish the current starting point. [Database inventory](../database.md) · [SQL design](../lms-platform.sql)

## Proposal

Give each implemented business service ownership of its tables, versioned migrations, and runtime database permissions. Enforce references between tables owned by the same service with local foreign keys. For records owned elsewhere, exchange stable IDs through an explicit service API or a versioned event. Apply the existing seven-database design incrementally as its service contracts are approved.

## Alternatives and trade-offs

- A shared schema permits database-enforced relationships and transactions across domains. Shared writes also couple schema changes and expand the privileges each participant may need.
- Service-owned databases can share a PostgreSQL instance while using separate credentials. This gives a service control over its migrations and write access. Cross-service references require application validation, and event-driven updates need retry and reconciliation.
- For course content, retaining MongoDB alongside PostgreSQL could serve a separately identified document workload. Before adopting two stores, specify which records each owns and how publishing and recovery behave if one store fails.

## What acceptance would require

1. Agree on the first service boundary and the authoritative store for its records, including the role of MongoDB in course-service.
2. Select a migration tool, establish a first migration and isolate the runtime login from the migration identity.
3. Define ownership and validation for at least one cross-service reference, including how the consumer handles duplicates and missing records.
4. Demonstrate an empty-database install and an upgrade using disposable local dependencies. Record the approved decision and its date here.

Adoption, dependency changes, migration work, and feature development require separate approval.
