> **Language:** Canonical English. Russian edition: [ru/plugins.md](../ru/plugins.md).

# Plugins and Application Solutions

> **Status:** Stable — Core vs packs vs bundles. Hub: [doc-status.md](doc-status.md).

## Principle

| Layer | Repository / branch | License |
|-------|---------------------|---------|
| **ISPF core** | `main` | GNU AGPL-3.0 (+ optional Enterprise EULA) |
| **Device driver packs** | `packages/ispf-driver-*` → `build/driver-packs/` | Per-pack `licenseType` in `driver-pack.json` |
| **Reference / industry demos** | Separate branch or repository (not `main`) | Specified in the deliverable |
| **Commercial plugins** | Separate repository or artifact | Commercial / EULA — **explicit in the package** |
| **Customer project** | Project repository | Per contract |

The core **does not contain** domain business logic in Java, **does not embed device drivers in the server JAR**, and **does not mix** with commercial modules without an explicit license boundary.

**Core ISPF principle:** solution business logic lives **on the platform** — in models, variables, events, functions, and workflows of the object tree. The core supplies only generic engines; the solution is declarative configuration. See [architecture](architecture.md).

## How to Connect Extensions (Without Java in the Core)

An application solution fills platform mechanisms **via API and bundle deploy**, not by merging domain Java into `ispf-server`:

1. `POST /api/v1/applications` — register `appId`
2. `POST /api/v1/applications/{appId}/data/migrate` — application SQL tables
3. `POST /api/v1/applications/{appId}/functions/deploy` — script functions
4. `POST /api/v1/applications/{appId}/deploy` — bundle in one request
5. Objects, dashboards, BPMN — via existing platform REST APIs

See [applications](applications.md).

## Commercial Plugin: Deliverable Requirements

Every commercial plugin **must** include:

- `LICENSE` or `license.json` with an explicit license type
- `README` with usage restrictions and copyright holder contact
- Version and list of compatible ISPF versions

Commercial bundle with a `license` section — see [commercial-licensing](commercial-licensing.md) and [0003-commercial-bundle-licensing](decisions/0003-commercial-bundle-licensing.md).

A plugin **must not** be committed to `packages/ispf-server/` and **must not** be merged into `main` without a separate open-source decision.

## LLM Providers (AI Layer, FW-40)

Like device drivers, LLM adapters live **outside** the `ispf-server` core:

| Module | Purpose |
|--------|---------|
| `packages/ispf-ai-api` | SPI `LlmProvider` |
| `packages/ispf-ai-openai-compatible` | OpenAI-compatible HTTP API |
| `packages/ispf-ai-ollama` | Ollama local API |

`ispf-server` contains only the registry, ToolRegistry, audit, and admin REST. Configuration — Spring profile/env (`ispf.ai.*`). See [ai-development](ai-development.md), [0004-ai-artifact-generation-gates](decisions/0004-ai-artifact-generation-gates.md).

## Checklist Before PR to `main`

- [ ] No domain Java and no industry-specific BFF routes.
- [ ] No Flyway migrations for application tables in `packages/ispf-server/.../db/migration/`
- [ ] No `examples/<industry-app>/` in the repository root
- [ ] New platform capabilities — generic REQ-PF / API / docs only

## Related Documents

- [license](license.md) — platform AGPL, driver packs, commercial modules
- [applications](applications.md) — deploy API
- [decisions/](decisions/) — ADR (0008 boundary, 0009 gate, 0010 licensing)
