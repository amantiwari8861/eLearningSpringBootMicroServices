# ADR 0002 — Explicit gateway routes

**Status** Documented from the working configuration on 2026-11-25. The original decision date and rationale are unknown.

## Configuration in effect

The gateway enables its WebFlux server, disables the discovery locator and declares four path predicates. Each maps a public prefix to a load-balanced Eureka service ID. The notification service participates in discovery without a gateway route. [Gateway properties](../../api-gateway/src/main/resources/application.properties) · [Route inventory](../architecture.md)

## Consequences

Registering a new service provides discovery metadata. Exposing it through this gateway also requires an explicit route, a matching upstream handler, and a decision about authorization. Existing predicates forward the full path. A request can therefore match at the gateway and still return a missing-handler response upstream.

## Future review

If automatic discovery routes are proposed, compare the public paths they would introduce with the four explicit prefixes and test which services would become reachable. Record the authorization policy and intended API contract before approving that change.
