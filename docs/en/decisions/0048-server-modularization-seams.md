# ADR-0048: Server modularization seams (ObjectTreePort → AI module → ObjectManager)

## Status

**Accepted** (2026-07-18) — Waves 2–4 and 7 landed; Wave 6 evidence path documented. Remaining lab-only: multi-day cluster soak under plant ingress (see [cluster-chaos-soak-runbook](../cluster-chaos-soak-runbook.md) — not a code gap).

**Progress:** Wave 2 `ObjectTreePort` · Wave 3 `packages/ispf-ai-agent` · Wave 4 `TreeCrudService` / `ObjectVariableService` / `ObjectTreeBootstrapFacade` / `ObjectTreeLoadSyncService` / `ObjectMetadataService` · Wave 6 runbook+smoke · Wave 7 web-console folder hygiene.

## Context

`ispf-server` concentrates a large share of platform code. The in-memory object tree hub [`ObjectManager`](../../../packages/ispf-server/src/main/java/com/ispf/server/object/ObjectManager.java) is a god-class (~1.3K LOC, many `ObjectProvider` collaborators). The tree-first AI agent ([0005](0005-tree-first-ai-agent.md)) lives under `com.ispf.server.ai` (~27K LOC) and calls `ObjectManager` directly from ~16 files — LLM SPI is already split (`ispf-ai-api` + adapters), but agent/tools are not.

Driver catalog honesty / BL-191 is **out of scope** for this program: driver work proceeds only under a named field or integrator task ([field-pilot ready-for-field gate](../field-pilot-playbook.md#ready-for-field-gate-policy)).

## Decision

Implement seams in this order (do not skip ahead to ObjectManager surgery before the AI seam exists):

### 1. `ObjectTreePort` (Wave 2)

Introduce a narrow port used by AI (and later other automation clients):

- Read: tree find / require / children (or equivalent read API)
- Write: create / delete / `persistNodeTree` / createVariable / setVariableValue / upsertFunction (minimal set actually used by agent tools)

Adapter delegates to `ObjectManager`. AI sources must not import `ObjectManager` except the adapter.

### 2. AI Gradle module (Wave 3)

Move `com.ispf.server.ai.*` to `packages/ispf-ai-agent` (name may vary; module must be separate from `ispf-server` main sources). Wire Spring component scan from the server app; keep REST paths `/api/v1/ai/**` stable. Document as an amendment note under [0005](0005-tree-first-ai-agent.md) when the move lands.

Do **not** carve `ApplicationBundleDeployService` / `ReportService` solely for AI — they serve REST/deploy/HMI; AI consumes them via existing service APIs.

### 3. `ObjectManager` decomposition (Wave 4)

After the port is stable, split responsibilities along existing package lines:

- Tree CRUD persistence
- Variable create/set/history hooks (without duplicating telemetry hot path)
- Bootstrap / blueprint providers facade

`ObjectManager` becomes a thin facade or is deprecated for new call-sites. Cluster replica sync classes stay; only ownership of CRUD APIs moves.

### 4. Frontend folder hygiene (Wave 7)

Path-only moves under `apps/web-console/src/components` and `utils` into domain folders. No visual redesign; no driver picker/maturity badge changes.

### Explicit non-goals

- Driver maturity matrix / `fieldReady` API / BL-191 downgrades
- Embedding Camunda (see [0047](0047-custom-bpmn-subset-engine.md))
- Rewriting REST controllers onto the port in the same PR as AI migration (optional later)

## Consequences

- Later PRs have a documented seam: port first, extract AI, then shrink ObjectManager.
- Circular dependency pressure (`ObjectProvider` sprawl) should fall as bootstrap and tree CRUD separate.
- Agent behaviour and ACL ([0005](0005-tree-first-ai-agent.md), `ObjectAccessService`) must remain equivalent after moves.

### Risks

- Large Gradle/Spring move (Wave 3) can break component scan — mitigate with module tests + server smoke.
- Over-narrow port forces constant expansion — start from real AI call-sites, not a speculative god-interface.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| Split ObjectManager first | Higher risk; AI still coupled to whatever remains; poor ROI for AI LOC |
| Leave AI in `ispf-server` forever | Keeps ~23% of server LOC and largest classes in the hottest module |
| Full hexagonal rewrite | Out of proportion; seams above are enough for the stated risks |

## Related

- [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md) — agent runs on platform; tools are the behavioural boundary
- [0001-app-platform-boundary](0001-app-platform-boundary.md) — platform engines vs solution config
- [0047-custom-bpmn-subset-engine](0047-custom-bpmn-subset-engine.md) — BPMN program (parallel)
- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md) — cluster proof is separate (soak/evidence, not OM split)
- [cluster-chaos-soak-runbook](../cluster-chaos-soak-runbook.md) — Wave 6 cluster proof (chaos/soak + smoke; no OM/AI/BPMN changes)
