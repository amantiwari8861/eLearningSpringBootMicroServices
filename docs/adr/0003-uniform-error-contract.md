# ADR 0003 — Uniform error contract

**Status** Accepted on 2026-11-26 for the current LMS increment.

## Context

Auth, course and order each carried a small `ApiError` record with only `code`, `message` and `timestamp`. Spring Security and the gateway answered some `401`/`403`/`404` errors with empty bodies or framework-specific JSON. AI and notification had no error DTO or handler. The frontend BFF already expected `{code, message}` from upstream failures, so changing that browser-facing shape would be a separate frontend contract change.

## Decision

Every implemented service and the gateway writes this body for handled errors:

```json
{
  "timestamp": "2026-11-26T13:20:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "The request contains invalid fields.",
  "path": "/api/v1/auth/register",
  "errors": [
    { "field": "email", "message": "must be a well-formed email address" }
  ]
}
```

- `timestamp`, `status`, `code`, `message`, `path` and `errors` are always present.
- `message` is mandatory and falls back to a safe sentence. Field validation also joins the field messages into `message` so the existing BFF can keep forwarding a useful human-readable value.
- `errors` contains `{field, message}` pairs for bean/constraint validation and is empty otherwise.
- Codes are stable strings; the current status/code matrix is recorded in [architecture.md](../architecture.md#uniform-error-contract).
- Servlet `401`/`403` responses keep the `WWW-Authenticate: Basic realm="lms"` challenge and add the same JSON body. Gateway identity failures use distinct `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `INVALID_IDENTITY` and `UPSTREAM_UNAVAILABLE` codes.
- Unexpected exceptions are logged server-side; clients receive only the generic `INTERNAL_ERROR` or gateway `UPSTREAM_ERROR` sentence.
- The record is duplicated in each service package instead of introducing a shared module. The frontend BFF keeps its `{code, message}` response shape.

## Consequences

Clients can read one error shape from auth, course, order, AI, notification and gateway routes, including framework failures before a request reaches a business handler. Field-level validation becomes machine-readable without breaking the older joined-text `message`.

Duplication means a future shared-library release must update every service. The BFF still loses `timestamp`, `path` and `errors[]`, and deliberately maps upstream `5xx` to `502 UPSTREAM_ERROR`; exposing the full gateway body in the browser requires a separate frontend change.

## Alternatives considered

- Keeping framework defaults would require clients to parse several incompatible bodies and would leave security failures empty.
- A new shared error library would remove duplication but adds a published artifact and coordinated release process to a reactor that currently has no shared library module.
- Making the BFF forward the complete body would be a visible frontend API change; it was left outside this increment because no frontend test harness is checked in.

## Verification

`.\mvnw.cmd -fae verify` on 2026-11-26 ran all 8 reactor modules and 44 tests with no failures, errors or skips. Tests assert the complete error body for representative validation, security, framework and upstream-failure paths in auth, course, order and the gateway. AI and notification were compiled and context-loaded with the new handlers but have no business endpoints to exercise yet.
