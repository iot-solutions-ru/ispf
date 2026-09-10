# Agent guidance (ISPF)

## Logic objects (hard rule)

The object that holds application / orchestration / twin logic must **not** be `ObjectType.DEVICE`. DEVICE = drivers and telemetry only.

| Role | Blueprint kind | Notes |
|------|----------------|-------|
| Unique orchestrator (1 per solution/cluster) | **SINGLETON** | Prefer `root.platform.singleton-blueprints.{name}` + `ensure_singleton_instance` |
| Digital twin with per-twin logic (N) | **INSTANCE** | `instantiate_instance_type`; each instance holds twin logic |
| Telemetry / I/O | — | **DEVICE** children under hub or plant folder |

Cluster layout (hub parent + DEVICE children under `devices.*`) is an **implementation choice**; the hub’s **type** must not be DEVICE.

Details: `docs/en/application-principles.md` § Logic objects vs DEVICE, `docs/en/blueprints.md`, `docs/en/agent-knowledge.md`.

## Hosted application UI (external IDE)

Product operator UI is a **React SPA** in a separate repo (Cursor/VS Code), shipped as a ui-pack and hosted at `/apps/<appId>/`. Do not add industry pages to `apps/web-console` or domain Java to `ispf-server`. Data: `POST /api/v1/bff/invoke` on a SINGLETON/INSTANCE hub. How-to: `docs/en/external-ide.md`.
