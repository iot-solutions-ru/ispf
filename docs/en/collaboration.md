> **Language:** Canonical English. Russian edition: [ru/collaboration.md](../ru/collaboration.md).

# Multi-Engineer Collaboration, Auditing, and Change Sets

> **Status:** Stable — optimistic concurrency control (`revision`), configuration audit trail, subtree edit leases, ownership ACLs, and transactional change sets. Status tags: [doc-status.md](doc-status.md).

When multiple engineers work on the same ISPF object tree simultaneously, the platform prevents lost updates and accidental overwrites through node revision checks, live presence indicators, subtree leases, and atomic change sets.

---

## 1. Optimistic Concurrency Control (`revision`)

Every node in `object_nodes` carries a monotonically increasing **`revision`** counter that increments whenever its metadata, variables, functions, or events are modified.

| HTTP Request Header | Behavior |
|---------------------|----------|
| `If-Match: <expectedRevision>` | Sent on `PATCH` / `PUT` requests; if another user has already saved a newer revision, the server rejects the write |
| `X-ISPF-Force: true` | Admin-only override: saves without checking `revision` |

When `If-Match` does not match the current database revision, the server responds with **`409 Conflict`**:

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

> High-frequency driver telemetry updates (`setDriverTelemetryValue`) and reactive binding propagations (`propagateBindings`) **do not increment** `revision`, so live telemetry never interferes with engineers editing object configuration.

In the Web Console, if another user updates an object while you have unsaved edits open, a warning banner appears offering **Reload** or **Overwrite** (`admin`).

---

## 2. Configuration Audit Log (`object_config_audit`)

Every configuration change to object metadata, variables, functions, and events is recorded in `object_config_audit`:

```http
GET /api/v1/objects/by-path/audit?path=root.platform.devices.pump-01&limit=50
```

In the Web Console, open the **Change history** tab in the Object Inspector to review who changed an object and when.

---

## 3. Live Presence and Subtree Edit Leases (`lease`)

### Soft Presence Indicator
When an engineer opens a tree path in edit mode, the Web Console sends a WebSocket presence message:

```json
{ "type": "presence", "path": "root.platform.devices", "username": "ivan", "mode": "edit" }
```

The server broadcasts active editors on that path (30-second TTL) without blocking writes.

### Hard Subtree Lease (`423 Locked`)
To exclusively lock a branch of the tree during major refactoring:

```http
POST   /api/v1/objects/leases   { "pathPrefix": "root.platform.devices.shop-1", "ttlMinutes": 120 }
DELETE /api/v1/objects/leases?pathPrefix=root.platform.devices.shop-1
GET    /api/v1/objects/leases
```

While a lease is active, write attempts by other users under `pathPrefix` fail with **`423 Locked`**.

---

## 4. Blueprint Diff, Merge, and Instance Upgrade

When evolving blueprints across team branches, use the blueprint merge preview and instance upgrade endpoints:

```http
POST /api/v1/blueprints/merge-preview
POST /api/v1/blueprints/merge-apply
POST /api/v1/blueprints/{id}/upgrade-instances?dryRun=true
```

---

## 5. Subtree Ownership ACLs (`OWNER`, `EDITOR`, `VIEWER`)

In addition to granular `READ` / `WRITE` / `INVOKE` permissions, `object_acl_entries` supports composite subtree roles that inherit downward:

| Permission | Granted Capabilities |
|------------|----------------------|
| **`OWNER`** | `READ` + `WRITE` + `INVOKE` + manage ACLs + acquire subtree leases |
| **`EDITOR`** | `READ` + `WRITE` + `INVOKE` |
| **`VIEWER`** | `READ` only |

Assign via `PUT /api/v1/objects/by-path/acl?path=<prefix>` or the **Access** tab in the Inspector.

---

## 6. Transactional Change Sets (`change-sets`)

A **change set** batches multiple tree operations with optional `expectedRevision` checks per path so you can preview conflicts and apply the batch atomically:

```http
POST /api/v1/platform/change-sets
GET  /api/v1/platform/change-sets
POST /api/v1/platform/change-sets/{id}/preview
POST /api/v1/platform/change-sets/{id}/apply?force=false
```

> Use **`bundle.json`** ([Applications](applications.md)) to package and ship complete application solutions across environments; use **change sets** for controlled incremental promotions on an existing live tree.

**Related guides:** [Security & RBAC](security.md) · [Object model](object-model.md) · [Blueprints](blueprints.md).
