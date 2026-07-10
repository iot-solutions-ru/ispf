> **Language:** Canonical English. Russian edition: [ru/collaboration.md](../ru/collaboration.md).

# Collaboration (Phase 11)

Mechanisms for safe parallel work by multiple engineers on a single ISPF object tree.

## 11.1 Foundation — revision + audit

### Optimistic concurrency

Each `object_nodes` row has a **`revision`** field (monotonically incremented on configuration changes).

| Header | Value |
|--------|----------|
| `If-Match` | Expected revision on PATCH/PUT of variables, metadata, functions, events |
| `X-ISPF-Force: true` | Admin-only: overwrite without revision check |

On mismatch → **409 Conflict**:

```json
{
  "error": "REVISION_CONFLICT",
  "objectPath": "root.platform.devices.pump-01",
  "expectedRevision": 3,
  "currentRevision": 5,
  "changedBy": "ivan",
  "changedAt": "2026-06-21T12:00:00Z"
}
```

Revision is **not** incremented on driver telemetry and computed bindings (`setDriverTelemetryValue`, `propagateBindings`).

### Config audit

Table `object_config_audit` — change log for metadata, variables, functions, events.

```
GET /api/v1/objects/by-path/audit?path=...&limit=50
```

Web Console: **Change history** tab in Object Properties Editor.

### Stale editor (Web Console)

WebSocket `/ws/objects` includes `revision` and `changedBy` in `UPDATED` / `VARIABLE_UPDATED` events.

If the editor is dirty and a higher revision arrives — banner with **Reload** / **Overwrite** (admin).

## 11.2 Live collaboration

### Presence (soft)

Client sends over WS:

```json
{ "type": "presence", "path": "root.platform.devices", "username": "ivan", "mode": "edit" }
```

Server responds with the list of active users on the path (TTL 30s). Does not block writes.

### Subtree lease (hard lock)

```
POST   /api/v1/objects/leases   { "pathPrefix": "...", "ttlMinutes": 120 }
DELETE /api/v1/objects/leases?pathPrefix=...
GET    /api/v1/objects/leases
```

When another holder has an active lease → **423 Locked** on write.

### Model merge

```
POST /api/v1/blueprints/merge-preview
POST /api/v1/blueprints/merge-apply
POST /api/v1/blueprints/{id}/upgrade-instances?dryRun=true
```

Preview shows variable conflicts between two models; apply requires resolution per conflict.

## 11.3 Subtree ownership ACL

Extended permissions on `object_acl_entries`:

| Permission | Rights |
|------------|-------|
| `OWNER` | read + write + invoke + grant ACL + lease |
| `EDITOR` | read + write + invoke |
| `VIEWER` | read |

Inherited upward by path (same as READ/WRITE/INVOKE). Assign via `PUT /api/v1/objects/by-path/acl?path=<prefix>`.

## 11.4 Change-sets and promotion

```
POST /api/v1/platform/change-sets
GET  /api/v1/platform/change-sets
POST /api/v1/platform/change-sets/{id}/preview
POST /api/v1/platform/change-sets/{id}/apply?force=false
```

Change-set — named ops package with optional `expectedRevision` per path. Preview shows conflicts; apply is transactional (or force).

Environment badge: `GET /api/v1/info` → `environment` (`ispf.environment`, default `dev`).

## Related documents

- [security](security.md) — RBAC and ACL
- [object-model](object-model.md) — object tree
- [blueprints](blueprints.md) — model diff/upgrade
- [roadmap](roadmap.md) — Phase 11
