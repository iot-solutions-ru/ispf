# ADR-0001: App vs platform boundary

## Status

Accepted (2026-06-19; formalized 2026-06-22)

## Context

ISPF is a middleware platform (framework), not a turnkey vertical solution. Application teams want to deliver logic through the deploy API; the platform team must extend only generic mechanisms, otherwise `main` grows industry-specific Java.

## Decision

1. **Platform (`main`, Apache 2.0)** implements generic engines once: object tree, CEL, BPMN, script runtime, drivers, bundle deploy, BFF gateway.
2. **Solution** fills mechanisms with **declarative configuration**: models, variables, events, functions, workflows, dashboards — via bundle deploy and REST API.
3. **Forbidden in `main`:**
   - industry-specific Java in `packages/ispf-server/`;
   - Flyway migrations for application tables in the platform repo;
   - hardcoded BFF routes for a vertical;
   - duplicating business logic outside the object tree.
4. **Bundle deploy** is packaging and delivery of configuration into the object tree and app schema, not a separate runtime.
5. New platform capabilities are tracked as **REQ-PF** (see [roadmap](../roadmap.md)).

## Consequences

- Reference apps (`examples/*`) and commercial bundles are outside mandatory core or carry a separate license ([0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md)).
- PR checklist: [plugins](../plugins.md).
- Application API is documented in [applications](../applications.md).

## Related

- [architecture.md § Core principle](../architecture.md#core-principle-business-logic-in-platform-mechanisms)
- [0002-dogfooding-gate](0002-dogfooding-gate.md)
