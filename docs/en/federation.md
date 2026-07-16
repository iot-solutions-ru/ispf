> **Language:** Canonical English. Russian edition: [ru/federation.md](../ru/federation.md).

# Object federation (spike PF-13)

> **Status:** Beta — Hub / edge (maturity caveats). Hub: [doc-status.md](doc-status.md).

Spike REQ-PF-13 implementation: peer registry, proxy object read/write, and catalog sync.

Full concept — [federation](federation.md), roadmap [roadmap.md § Phase 4–8](roadmap.md).

## Principle

**Object path ≠ service endpoint.** Path `root.platform.devices.x` is a stable catalog identifier. Remote ISPF URL is stored in the peer registry.

## Components (spike)

| Component | Description |
|-----------|----------|
| `federation_peers` (V26, V29) | Peer table: name, baseUrl, authToken, pathPrefix, enabled, auth lifecycle (V29) |
| `federation_inbound_registrations` (V30) | One-time registration codes for inbound tunnel |
| `federation_outbound_agents` (V30) | Outbound tunnel agent config on edge |
| `GET/POST/PUT/DELETE /api/v1/federation/peers` | CRUD (admin) |
| `GET/POST /api/v1/federation/peers/{id}/auth-status`, `refresh-token` | Auth lifecycle for service account peers |
| `POST/GET/DELETE /api/v1/federation/inbound/registrations` | Hub: issue registration code |
| `GET /api/v1/federation/tunnels` | Hub: active tunnel sessions |
| `POST/GET/PUT/DELETE /api/v1/federation/outbound/agents`, `connect` | Edge: CRUD outbound agent |
| `/ws/federation/tunnel` | WebSocket tunnel (registration code or session reconnect) |
| `POST /api/v1/federation/remote-token` | Login to remote ISPF (server-side), returns Bearer for `authToken` |
| `POST /api/v1/security/users/{username}/federation-token` | Issue Bearer session for service user on **this** node |
| `POST /api/v1/federation/peers/{id}/sync-catalog` | Import remote object list into local tree (optional body: resolutions) |
| `GET /api/v1/federation/peers/{id}/catalog-sync-preview` | Preview: create/update counts + conflicts before sync |
| Proxy nodes | AGENT with variables `federationProxy`, `federationPeerId`, `federationRemotePath` |
| `GET /api/v1/federation/proxy/objects/by-path` | Direct proxy-read without catalog sync |
| `PATCH /api/v1/federation/proxy/objects/by-path/variables/value` | Proxy-write variable on remote peer |
| `POST /api/v1/federation/proxy/objects/by-path/functions/invoke` | Proxy-invoke function on remote peer |
| `GET /api/v1/objects/by-path` | For proxy nodes in tree — transparent read via peer |
| `GET /api/v1/dashboards/by-path` | Proxy layout; widget paths remapped to `root.platform.federation.{peer}.*` |
| `PUT /api/v1/dashboards/by-path/layout` | Local or federated: layout proxied to remote with widget path unmapping |
| `PUT /api/v1/dashboards/by-path/title` | Local or federated: title proxied to remote |
| `GET /api/v1/objects/by-path/variables/history*` | Proxy historian for federated paths |
| `FederationWebSocketFanoutService` | Fan-out events to federated path subscribers (basic notify) |
| `POST/PATCH/DELETE /api/v1/federation/binds` | Federation bind: overlay remote node on **local** path (REQ-PF-13c) |
| `POST /api/v1/federation/binds/probe` | Probe remote target before bind |

## Catalog sync vs federation bind (REQ-PF-13c)

| | Catalog sync | Federation bind |
|--|--------------|-----------------|
| Purpose | Bulk browse of entire edge catalog | Production structure: object is "ours" at local path |
| Local path | `root.platform.federation.{peer}.*` | Any operator path, e.g. `root.platform.devices.edge-pump` |
| Remote | Data source via metadata | Same |
| UX | Sync on peers panel | Inspector → **Federation bind**; mirror → **Place locally…** |

**Bind** ("overlay"): existing or new local node gets variables `federationProxy`, `federationPeerId`, `federationRemotePath`. Read/write/history/dashboard proxy via `FederationProxyService`. Local driver (DEVICE) stops on bind; local variables remain defined but bindings are hidden. Snapshot saved before overlay (`displayName`, `description`, `type`, driver-running flag); **unbind** restores those properties and restarts driver if it ran before bind.

```http
POST /api/v1/federation/binds
{
  "localPath": "root.platform.devices.edge-pump",
  "peerId": "<uuid>",
  "remotePath": "root.platform.devices.demo-sensor-01"
}
```

Create + bind: `parentPath` + `name` instead of `localPath`. Rebind: `PATCH /api/v1/federation/binds`. Unbind: `DELETE /api/v1/federation/binds?localPath=...`.

**Same remote path (typical scenario):** local `root.platform.devices.snmp-localhost` can bind to remote node with the same `remotePath` — data comes from Edge, local driver and variables frozen. Paths exist **on different instances**; that is normal.

**Loop protection:**

- Forbidden: `localPath` = `remotePath` on **same** ISPF (loopback peer → infinite proxy).
- Forbidden: `remotePath` points to **local** federated node (including mirror `root.platform.federation.*`), especially if its remote target already equals bind `localPath`.
- Single hop: cannot chain through another local federated path.

```http
POST /api/v1/federation/binds
{
  "localPath": "root.platform.devices.snmp-localhost",
  "peerId": "<edge-tunnel-peer>",
  "remotePath": "root.platform.devices.snmp-localhost"
}
```

## Catalog sync

### Preview and conflicts (BL-45)

Before sync Web Console opens **Catalog sync** dialog (`FederationCatalogSyncDialog`): `GET /api/v1/federation/peers/{peerId}/catalog-sync-preview`.

| Conflict type | When | Resolution |
|---------------|-------|------------|
| `LOCAL_NATIVE` | **Non-proxy** object already at local path | `SKIP` (default) or `BIND` (overwrite with proxy metadata) |
| `PROXY_MISMATCH` | Proxy exists but `peerId` / `remotePath` does not match import | `SKIP` or `BIND` |

Apply:

```http
POST /api/v1/federation/peers/{peerId}/sync-catalog
Content-Type: application/json

{
  "resolutions": [
    { "localPath": "root.platform.federation.site-a.devices.pump-01", "action": "BIND" }
  ]
}
```

`action`: `SKIP` | `BIND`. Empty body — sync without resolution: conflicts **fail** (safer than silent overwrite).

Response: `{ localRoot, created, updated, remoteCount, skipped }`.

### Import nodes

`POST /api/v1/federation/peers/{peerId}/sync-catalog` fetches object list from peer and creates local proxy nodes:

```text
root.platform.federation.{peer-name}.devices.demo-sensor-01
  federationPeerId = <uuid>
  federationRemotePath = root.platform.devices.demo-sensor-01
  federationProxy = true
```

Web Console: click **Sync catalog** on Federation Peers panel → preview dialog → **Sync catalog**.

Repeat sync is **idempotent** for matching proxies: nodes with same `peerId` + `remotePath` update metadata (`updated`). On reverse sync peer paths `root.platform.federation.*` on remote side are skipped to avoid nested catalog mirrors.

### Selective subtree sync (BL-119, S22)

Sync **part** of remote catalog instead of full `sync-catalog`:

| Endpoint | Description |
|----------|----------|
| `GET /api/v1/federation/peers/{id}/subtree-sync-preview?remoteSubtreePath=…&localParentPath=…` | Preview create/update/conflicts for subtree |
| `POST /api/v1/federation/peers/{id}/sync-subtree` | Apply subtree sync |

`sync-subtree` body:

```json
{
  "remoteSubtreePath": "root.platform.devices",
  "localParentPath": "root.platform.federation.site-a.devices",
  "resolutions": []
}
```

- `remoteSubtreePath` required, must be under peer `pathPrefix` (default `root.platform`).
- `localParentPath` optional; default mirror under `root.platform.federation.{peer}.*` with same suffix.
- Conflicts and resolutions — same as full catalog sync.

**Web Console:**

- Federation peers → **Sync devices subtree** (quick preset `root.platform.devices`).
- Explorer → federation mirror folder → Federation tab → **Sync this folder with peer**.

## Recovery runbook + SLO (BL-120, S22-05)

| Scenario | Symptom | Action | SLO |
|----------|---------|----------|-----|
| Peer disabled | Proxy 400 "Peer disabled" | `PUT /peers/{id}` → `enabled: true` | ≤ 5 minutes to re-enable |
| Peer down (RED) | Health RED, proxy timeout | Check URL/tunnel/token; `refresh-token` for service account | MTTR &lt; 15 min (ops) |
| Partial catalog | Only devices needed | `sync-subtree` instead of full sync | — |
| Tunnel replay lag | Tunnel offline, buffered events | Wait for reconnect or `connect` outbound agent | Replay ≤ 60 s after reconnect (lab) |

**Chaos coverage:** `FederationChaosIntegrationTest` — disable peer → proxy/sync fail → re-enable → sync succeeds; subtree variant (`disabledPeerBlocksSubtreeSyncAndRecoversAfterReEnable`) — `sync-subtree` imports only devices, not dashboards.

### Integration test budget (S27)

Tunnel and store-forward ITs poll async connect/replay; budgets live in `FederationIntegrationTestSupport`:

| Constant | Local | CI | Used by |
| -------- | ----- | -- | ------- |
| `TUNNEL_CONNECT_TIMEOUT_SECONDS` | 60 | 120 | `FederationTunnelIntegrationTest`, `FederationStoreForwardIntegrationTest` |
| `PROXY_READY_TIMEOUT_SECONDS` | 30 | 60 | `FederationTunnelIntegrationTest` |
| `BUFFER_DRAIN_TIMEOUT_SECONDS` | 90 | 120 | `FederationStoreForwardIntegrationTest` |
| `CONNECT_RETRY_INTERVAL_MS` | 5000 | 5000 | tunnel connect retry |

Tests marked `@Isolated` (`FederationChaosIntegrationTest`, `FederationTunnelIntegrationTest`, `FederationStoreForwardIntegrationTest`) — no parallel run with other IT classes. Nightly gate: [ci-nightly.yml](../../.github/workflows/ci-nightly.yml) job **Federation integration gate (S27)**. On timeout failure triage per [ci-flaky-triage](ci-flaky-triage.md) (P1 if &lt;1×/week).

**Ops checklist:**

1. `GET /api/v1/federation/peers` — health badge GREEN/YELLOW.
2. `GET /api/v1/federation/peers/{id}/health` — `lastProxyError`, latency.
3. On degradation — disable peer (fail-fast), fix upstream, re-enable, `sync-subtree` or `sync-catalog`.
4. Tunnel peers — `GET /api/v1/federation/tunnels`, outbound buffer stats.

## Dashboard write via proxy (BL-46)

Federated dashboard (`federationProxy=true` under `root.platform.federation.*` or bind to `DASHBOARD`) previously returned **403** on `PUT layout/title`. Now:

1. Client saves layout with **local** widget paths (`root.platform.federation.{peer}.*`).
2. `DashboardController` → `FederationProxyService.proxyDashboardSaveLayout` / `SaveTitle`.
3. `FederationPathRemapper.unremapLayoutJson` translates paths back to remote prefix.
4. `PUT` on remote `/api/v1/dashboards/by-path/layout|title`.
5. Response remapped for local UI.

`refreshIntervalMs` still via variable PUT on proxy object (like other writable variables).

## Path prefix

If peer serves same hierarchy but console passes relative path:

- `path=devices.demo-sensor-01` + `pathPrefix=root.platform` → remote `root.platform.devices.demo-sensor-01`

## Cross-instance authorization

Peer stores `authToken` (service account on remote ISPF). Token not returned in list API (`hasAuthToken: true/false`).

If `authToken` unset, outbound peer requests use current user's Bearer (convenient for loopback on `127.0.0.1` with RBAC). For background jobs without HTTP context token on peer is required.

### Web Console

**Federation peers** panel (Explorer → Platform → Federation):

1. **Federation token (this node)** — select user and TTL, **Issue token**. Copy token to peer on other ISPF.
2. When creating peer:
   - **Current session token** — inserts your session bearer (loopback).
   - **Fetch token from remote** — `POST /api/v1/federation/remote-token` with `baseUrl` from form and remote credentials (password not stored in form).

Tokens are normal platform session tokens (default TTL 12 h, max 168 h), not long-lived API keys. Requires `local` profile or `ispf.security.token-auth-enabled: true`.

### Auth lifecycle (phase 7.1)

Peer can use `authMode`:

| Mode | Description |
|-------|----------|
| `STATIC_TOKEN` | Manual Bearer (default) |
| `SERVICE_ACCOUNT` | Login/password on remote; password encrypted (`ispf.security.secrets-key`) |

For `SERVICE_ACCOUNT`:

- `@Scheduled` refresh ~20% TTL (min 1 h) before expiry
- On 401 from peer — one immediate retry after refresh.
- `GET /api/v1/federation/peers/{id}/auth-status` — diagnostics
- `POST /api/v1/federation/peers/{id}/refresh-token` — manual refresh

Recommended dedicated user `federation-agent` with role operator/admin and tenant scope.

### Outbound tunnel (NAT boundary, phase 7.2)

Local site behind NAT — **no inbound** reachability. Edge initiates outbound WebSocket to public hub:

```text
Edge (NAT) ──WS outbound──► Hub (public)
Hub ──proxy_request──► Edge ──local services──► response
```

**Operator sequence:**

1. **Hub:** Federation → Inbound registration → name + pathPrefix → **registration code** (one-time).
2. **Edge:** Federation → Outbound agent → hub URL + code → Connect.
3. Hub auto-creates peer `connection_mode=TUNNEL_INBOUND`.
4. **Sync catalog** on hub → `root.platform.federation.{site}.*`.
5. Read/write/invoke via tunnel; `event_notify` replaces HTTP polling for WS subscription.

Product requirements: `ispf.security.secrets-key` on edge (registration code/session token storage); reverse proxy with `wss` support.

### Store-and-forward (BL-117)

When outbound edge tunnel drops, **platform change events** hub should receive via `event_notify` are buffered:

1. `FederationOutboundEventBufferRegistry` holds in-memory buffer per agent.
2. While WS disconnected, `FederationTunnelAgentService` enqueues `ObjectChangeEvent` instead of sending.
3. After reconnect — `replayBufferedEvents()` with ordering guard on hub (`lastEventSeqByPeer`).

Config (`application.yml`):

```yaml
ispf.federation:
  outbound-buffer:
    max-bytes: 2097152          # ISPF_FEDERATION_OUTBOUND_BUFFER_MAX_BYTES
    drop-policy: DROP_OLDEST    # or DROP_NEWEST
  health:
    stale-minutes: 15           # ISPF_FEDERATION_HEALTH_STALE_MINUTES
```

| Policy | Behavior on overflow |
|----------|----------------------------|
| `DROP_OLDEST` | Drops oldest events, accepts new |
| `DROP_NEWEST` | Rejects new event, keeps queue |

`event_notify` includes optional `seq` and `occurredAt` for deterministic replay.

Tests: `FederationOutboundEventBufferTest`, `FederationStoreForwardIntegrationTest`.

### Agent store-forward service (BL-145)

`AgentStoreForwardService` (`com.ispf.server.agent`) — named facade over in-memory buffer for edge outbound tunnel. Used by `FederationTunnelAgentService` on disconnect/reconnect.

Config:

```yaml
ispf:
  agent:
    store-forward:
      enabled: true                 # ISPF_AGENT_STORE_FORWARD_ENABLED
      max-bytes: 2097152            # ISPF_AGENT_STORE_FORWARD_MAX_BYTES
      drop-policy: DROP_OLDEST      # ISPF_AGENT_STORE_FORWARD_DROP_POLICY
```

| Key | Default | Description |
|------|---------|----------|
| `enabled` | `true` | When `false` events are not buffered or replayed |
| `max-bytes` | 2 MiB | Per-agent limit (delegates to `FederationOutboundEventBufferRegistry`) |
| `drop-policy` | `DROP_OLDEST` | Overflow policy |
| `persist-to-disk` | `true` | JSON snapshot in `{data-dir}/agent/store-forward-buffer.json` |

Legacy alias: `ispf.federation.outbound-buffer.*` — same registry; new edge deployments prefer `ispf.agent.store-forward.*`.

**Persistence (BL-145):** when `persist-to-disk=true` pending events save to `{ISPF_DATA_DIR}/agent/store-forward-buffer.json` and restore on restart.

**Metrics API (BL-145 HARDEN):**

```http
GET /api/v1/agent/store-forward/stats
Authorization: Bearer <read-token>
```

Response: `{ enabled, persistToDisk, maxBytes, dropPolicy, agents: { "<agentId>": { pendingCount, pendingBytes, dropped } }, totalPending, totalBytes, totalDropped, capturedAt }`.

Use for field/lab soak and alerting on rising `totalPending` / `totalDropped`.

### Field soak — 30-day buffer replay (BL-145)

Accelerated lab soak (30 sim-days in 30 wall-minutes):

```bash
export ISPF_SOAK_BASE_URL=https://edge-site.example
export ISPF_SOAK_TOKEN=<admin-or-read-token>
export ISPF_SOAK_OUTBOUND_AGENT_ID=<uuid>   # optional disconnect/reconnect cycles
export ISPF_DATA_DIR=/var/lib/ispf          # optional persist file check
bash deploy/local/tools/agent-store-forward-soak.sh
```

| Env | Default | Description |
|-----|---------|----------|
| `ISPF_SOAK_SIM_DAYS` | `30` | Simulated timeline length |
| `ISPF_SOAK_WALL_MINUTES` | `30` | Wall-clock duration (86400× accel vs 30d) |
| `ISPF_SOAK_POLL_SEC` | `10` | Stats poll interval |
| `ISPF_SOAK_OUTBOUND_AGENT_ID` | — | Weekly simulated maintenance disconnect/reconnect |
| `ISPF_SOAK_EVENT_PATH` / `ISPF_SOAK_EVENT_VAR` | demo sensor | Variable patches while tunnel offline |

**Production field soak:** run same script with `ISPF_SOAK_WALL_MINUTES=43200` (30 real days) on each edge node after hub registration. Review `build/agent-store-forward-soak/soak-report.md` + CSV; GA gate at zero data loss (204 stable, pending drain after reconnect).

**Limits (v1):** buffer tracks only tunnel `event_notify`; HTTP-only peers without outbound tunnel are not buffered.

### Peer health SLO (BL-118)

Per-peer diagnostics for operator and alerting:

| Signal | Source |
|--------|----------|
| Tunnel connected | `FederationTunnelHubService` |
| Auth status | `FederationPeer.authStatus` |
| Last proxy success / latency / error | `FederationPeerHealthService` (HTTP + tunnel proxy) |
| Pending buffered events | outbound buffer registry |

**Limits (v1):** proxy snapshots and buffer stats are in-memory only (reset on server restart).

**Auto-alerting (S27):** `FederationPeerHealthMonitor` fires `peerHealthDegraded`/`peerHealthRecovered` on `root.platform.federation` when enabled peer transitions RED ↔ GREEN. Configure alert rules on these events for webhook or email.

**API:**

```http
GET /api/v1/federation/peers/{id}/health
```

Response: `{ peerId, level, tunnelConnected, lastProxySuccessAt, lastProxyLatencyMs, lastProxyError, pendingBufferedEvents, summary }`.

`level`: `GREEN` | `YELLOW` | `RED` — stale proxy &gt; N min, tunnel down, auth failed, pending buffer.

List peers (`GET /api/v1/federation/peers`) also returns `healthLevel` and `healthSummary`.

Web Console: health column badge on **Federation peers** panel (green/yellow/red).

## Manager-of-managers — federation hub operator shell (BL-188)

**Manager-of-managers (MoM)** is a central **hub** ISPF instance that federates many edge sites so operators need not log into each site separately. Edge nodes run behind NAT; hub runs operator HMI over federated proxy paths.

### Topology

```text
                    ┌─────────────────────────────────────┐
                    │  Hub ISPF (public)                  │
                    │  Operator HMI: ?mode=operator&app=  │
                    │    federation-hub                   │
                    │  root.platform.federation.{site}.*  │
                    └──────────────┬──────────────────────┘
           tunnel WS              │              tunnel WS
     ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
     │ Edge site A  │    │ Edge site B  │    │ Edge site C  │
     │ (ARM / NAT)  │    │ (ARM / NAT)  │    │ (ARM / NAT)  │
     └──────────────┘    └──────────────┘    └──────────────┘
```

Deploy profiles: hub uses standard VPS stack; edges use [docker-compose.edge-arm.yml](../../deploy/docker-compose.edge-arm.yml) with outbound tunnel enabled.

### Hub setup checklist

1. **Inbound registration** — Federation → Inbound registration → issue registration code per edge site.
2. **Edge connect** — each edge: Federation → Outbound agent → hub URL + code → Connect.
3. **Peer health** — `GET /api/v1/federation/peers` — all enabled peers GREEN before operator rollout.
4. **Catalog sync** — full sync or `sync-subtree` for `root.platform.devices` per site (BL-119).
5. **Operator app** — create operator app manifest `federation-hub`:
   - Dashboards reference federated paths (remapped widgets).
   - `alarmBar` enabled — federated event fan-out via `FederationWebSocketFanoutService`.
   - Optional per-site BFF (invoke via proxy when configured).

### Operator shell UI

| Element | Hub behavior |
|---------|--------------|
| Dashboard tabs | One tab per site or per production area (`site-a-overview`, `site-b-overview`) |
| Object table | Rows from federated devices subtree; selectionKey drills into site dashboard |
| Live values | Proxy read + WS fan-out; stale badge when peer YELLOW/RED |
| Federation site picker | Peer health badges (green/yellow/red) + 2-minute poll refresh |
| Event journal | Aggregated WARNING+ from all federated prefixes |
| Agent copilot | Scoped to hub operator app prefixes only ([operator-guide](operator-guide.md)) |

Operators **never** edit federation nodes or binds — admin console only.

### Federation bind vs catalog mirror (MoM)

| Pattern | MoM usage |
|---------|------------|
| **Catalog sync / subtree** | Default — bulk mirror under `root.platform.federation.{peer}.*` |
| **Federation bind** | When hub must show remote device at **canonical local path** (e.g. same path as Edge for training docs) |

Prefer catalog mirror for MoM scale; use bind for ≤10 critical assets needing fixed paths on shared dashboards.

### Health and SLO

Follow [Recovery runbook + SLO (BL-120)](#recovery-runbook--slo-bl-120-s22-05):

- Disable peer on prolonged RED → operator sees last known values + banner.
- Re-enable after fix → `sync-subtree` if catalog drift suspected.
- Alert on `peerHealthDegraded` events ([Peer health SLO (BL-118)](#peer-health-slo-bl-118)).

### Limits (v1)

- No cross-peer computed bindings on hub (aggregate in hub CUSTOM manually if needed).
- Dashboard layout writes proxy to **remote** dashboard (BL-46).
- MoM operator app is read-only for config; agent copilot read-only ([roadmap](roadmap.md)).

See also: [operator-guide](operator-guide.md), [deploy/docker-compose.edge-arm.yml](../../deploy/docker-compose.edge-arm.yml), [roadmap](roadmap.md) BL-188.

## Spike / production backlog

- Proxy write — variable patch, function invoke, **dashboard layout/title**; full bidirectional position sync not planned.
- Catalog sync — import + operator resolutions; without `BIND` conflicting local native/proxy mismatches **do not overwrite**.
- ~~Edge store-forward~~ — **Done (BL-117, S17):** in-memory buffer + replay; disk persistence — backlog.
- ~~Peer health SLO~~ — **Done (BL-118, S17):** Health API + UI badges; **alerting (S27):** `peerHealthDegraded` / `peerHealthRecovered` events.
- ~~Federated dashboards read-only~~ — **Done (BL-46):** layout/title write proxied to remote.
- ~~Catalog sync without merge criteria~~ — **Done (BL-45):** preview + SKIP/BIND in UI.
- ~~Tenant scope in federation API~~ — **Done v0.3.0** (`FederationAccessService`, peer CRUD admin-only, proxy scoping).
- ~~WS subscription by path~~ — **Done v0.3.0** (`ObjectWebSocketHandler` + `FederationSubscribePollService` + Web Console intercept).

## Example

```http
POST /api/v1/federation/peers
{ "name": "site-a", "baseUrl": "https://ispf-site-a.example", "pathPrefix": "root.platform" }

GET /api/v1/federation/proxy/objects/by-path?peerId=<uuid>&path=devices.demo-sensor-01
Authorization: Bearer <admin-token>

PATCH /api/v1/federation/proxy/objects/by-path/variables/value?peerId=<uuid>&path=devices.demo-sensor-01&name=temperature
Authorization: Bearer <admin-token>
{ "schema": { "name": "temperature", "fields": [{"name": "value", "type": "DOUBLE"}] }, "rows": [{"value": 22.5}] }
```
