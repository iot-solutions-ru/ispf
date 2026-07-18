> **Language:** Canonical English. Russian summary: [ru/cluster-chaos-soak-runbook.md](../ru/cluster-chaos-soak-runbook.md).

# Cluster chaos / soak runbook (Wave 6)

> **Status:** Lab — cluster proof via reproducible evidence. Hub: [doc-status.md](doc-status.md).

Operator runbook for **active-active cluster proof** without changing industrial driver protocols or refactoring ObjectManager / AI / BPMN. Complements ADRs [0028](decisions/0028-horizontal-active-active-cluster.md), [0029](decisions/0029-cluster-live-variable-replica-sync.md), [0030](decisions/0030-cluster-config-structure-replica-sync.md).

Product guides: [cluster](cluster.md) · [deployment](deployment.md) · [testing](testing.md) · [load-testing](load-testing.md).

## Evidence classes

Same vocabulary as the roadmap scorecard:

| Class | Meaning | May promote BL / claim HA? |
| ----- | ------- | -------------------------- |
| **REAL** | Runtime path exercised + automated assert + reproducible steps here | Yes (for that claim only) |
| **PARTIAL** | Foundation or short lab gate exists; duration, load, or plant conditions missing | No — keep Partial |
| **STUB** | Docs/scripts only, or stubbed runtime | No |

Do **not** treat a green weekly JDBC unit gate as multi-hour soak evidence.

## What CI can vs cannot prove

| Claim | CI (`.github/workflows/cluster-load-test.yml`) | Lab / soak (this runbook) |
| ----- | ---------------------------------------------- | ------------------------- |
| JDBC ownership acquire / expire / reclaim (in-process) | **REAL** — `DriverOwnershipServiceTest`, `ClusterFailoverIntegrationTest` (weekly + dispatch) | Same classes; optional re-run under load |
| Round-robin REST ≥2 replicas, REST stays 200 when one replica stopped | **REAL** — `deploy/cluster-smoke-test.sh` + `deploy/nginx-cluster.conf` API upstream (round-robin; WS stays `ip_hash`) | Repeat on lab compose / VPS |
| Kill driver-lock owner → reclaim within SLO | **REAL** on compose defaults (TTL 15s + scan 5s; smoke SLO default **45s**) when fixtures hold locks | **PARTIAL→REAL** under sustained ingress when journal below is filled |
| Config/structure create+delete visible on all LB replicas (ADR-0030) | **REAL** — smoke `--config-sync` | Same |
| API/config variable value consistent across LB after write (ADR-0029 path) | **REAL** — smoke `--live-var-lag` (lag SLO default **5s**) | **PARTIAL** for high-rate **driver telemetry** under plant load |
| Scale-out throughput ≥1.8× (1 vs 3 replicas) | **REAL** when compose gate run — `cluster-scale-load-test.py` | Re-pin after hardware change |
| Kill owner **under sustained MQTT/ingress load** | **Cannot** — no multi-hour load in GHA | Lab § Kill owner under load |
| 30–60 min combined soak / multi-day HA | **Cannot** | Lab § Soak window |
| Industrial protocol correctness / BL-191 matrix | **Out of scope** (Wave 6) | Field / interop tracks only |

## Prerequisites

```bash
# Lab compose (3 replicas + nginx :8088) — see cluster quickstart
bash deploy/cluster-quickstart.sh
# or: docker compose -f deploy/docker-compose.cluster.yml up -d
```

Defaults in `deploy/docker-compose.cluster.yml`:

| Knob | Value | Role |
| ---- | ----- | ---- |
| `ISPF_CLUSTER_DRIVER_LOCK_TTL_SECONDS` | `15` | Lock expiry after owner death |
| `ISPF_CLUSTER_DRIVER_FAILOVER_SCAN_MS` | `5000` | Reaper scan period |
| `ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS` | `500` | Live-value fan-out coalesce |

Smoke SLO env (override as needed):

| Env | Default | Assert |
| --- | ------- | ------ |
| `ISPF_CLUSTER_RECLAIM_SLO_SEC` | `45` | Owner kill → new holder |
| `ISPF_CLUSTER_LIVE_VAR_LAG_SLO_MS` | `5000` | PUT value → consistent LB reads |
| `ISPF_CLUSTER_REQUIRE_DRIVER_LOCKS` | `0` (`1` in CI compose) | Fail if no active locks |

## Automated smoke (CI-safe)

```bash
export ISPF_CLUSTER_REQUIRE_DRIVER_LOCKS=1   # compose fixtures should hold locks
bash deploy/cluster-smoke-test.sh --config-sync --live-var-lag
python deploy/cluster-scale-load-test.py --scale-factor-floor 1.8
```

Expected: script prints reclaim elapsed seconds ≤ SLO, `config-sync PASSED`, `live-var-lag PASSED`, scale factor ≥ floor.

## Chaos scenarios

### 1. Kill driver owner → lock reclaim SLO

**Evidence:** **REAL** (compose smoke) · **PARTIAL** until repeated under load (§2).

1. Confirm locks: `GET /api/v1/platform/cluster/health` (admin) and/or  
   `SELECT holder_id, COUNT(*) FROM platform_driver_locks WHERE expires_at > NOW() GROUP BY 1;`
2. Note `holder_id` (e.g. `replica-3` / `io` profile).
3. `docker stop` (or `kill`) that replica’s container; start wall clock.
4. Poll until a **different** `holder_id` appears with non-expired locks.
5. Pass if reclaim ≤ `TTL + failover_scan + ~15s margin` (compose default budget **45s**).
6. Confirm LB `GET /api/v1/info` stays HTTP 200 while owner is down.
7. Restart stopped replica; confirm cluster health `nodesUp` recovers.

Scripted: `bash deploy/cluster-smoke-test.sh` (ownership block).

### 2. Kill driver owner under load

**Evidence:** **PARTIAL** in CI (not run) · lab **REAL** when journal row completed.

1. Start a sustained API or MQTT ingress load ([load-testing](load-testing.md); do **not** reset DB mid-window).
2. While RPS/MQTT is non-trivial, run §1 kill/reclaim.
3. Record: reclaim seconds, LB error rate during failover, whether live tags on followers catch up within lag SLO after reclaim.
4. File a journal row (template below).

### 3. Config / structure sync (ADR-0030)

**Evidence:** **REAL** via smoke `--config-sync`.

1. Create a temp `DEVICE` under `root.platform.devices` through the LB.
2. Poll `GET /api/v1/objects/by-path` until every round-robin hit returns 200 (script samples the pool).
3. Delete the object; poll until no replica returns 200 (no RAM ghost).
4. Optional manual: save a mimic/diagram on one session, open explorer on another replica — layout present without factory reset.

### 4. Live-variable lag (ADR-0029)

**Evidence:**

| Path | Class |
| ---- | ----- |
| API/config `PUT` variable → LB reads agree within coalesce + margin | **REAL** (smoke `--live-var-lag`) |
| High-rate driver telemetry under plant MQTT load | **PARTIAL** until lab §2 journal |

Smoke path creates a temp device + writable variable, writes a nonce value, then requires consecutive LB reads to observe that value within `ISPF_CLUSTER_LIVE_VAR_LAG_SLO_MS`.

Lab extension: under ingress load, sample a hot fixture tag from multiple replicas (or many LB GETs) and record p95 cross-replica lag.

## Soak window (lab only)

**Evidence:** **PARTIAL** until a completed journal for the chosen duration · never claimed by CI.

### Scripted soak (recommended)

With the compose cluster already up (`deploy/cluster-quickstart.sh` or equivalent):

```bash
# 30 minutes: background LB load + mid-window chaos (smoke under load) + markdown journal
bash deploy/cluster-soak-lab.sh

# Short dry-run
bash deploy/cluster-soak-lab.sh --duration-min 2

# Load only (no mid-window kill/smoke)
bash deploy/cluster-soak-lab.sh --duration-min 60 --no-chaos
```

Journals land in `deploy/journals/cluster-soak-<UTC>.md` (gitignored content; keep `.gitkeep`). Set `OPERATOR=name` to stamp the row.

### Manual steps

1. Single reset/bootstrap at start ([load-testing](load-testing.md) Wave 6 note).
2. Run **30–60 minutes** combined load (API + optional MQTT). Do not wipe DB mid-window.
3. Once during the window: execute §2 (kill owner under load) — or let `cluster-soak-lab.sh` run smoke at mid-window.
4. End: cluster health all `UP`, no persistent config ghosts, reclaim met SLO, lag samples within budget.
5. Multi-day HA remains a **field/ops** exercise — out of Wave 6 CI scope.

### Soak / chaos journal (copy per run)

| Date | Duration | Topology | Reclaim s | LB errors during kill | Live-var lag note | Evidence class | Operator |
| ---- | -------- | -------- | --------- | --------------------- | ----------------- | -------------- | -------- |
| 20260718T124549Z | 5m (requested) / ~7m wall | lab 6 (`lab-cluster-compose.yml` :8000) | skipped (no driver locks; `REQUIRE_DRIVER_LOCKS=0`) | load `ok=1700 fail=0` | 1630ms PASSED | REAL (config-sync + live-var; reclaim n/a) | cursor-lab |
| | 30–60m / … | compose 3 / lab 6 | | | | REAL / PARTIAL | |

## Related commands

| Gate | Command |
| ---- | ------- |
| JDBC ownership | `./gradlew :packages:ispf-server:test --tests com.ispf.server.driver.DriverOwnershipServiceTest --tests com.ispf.server.driver.ClusterFailoverIntegrationTest` |
| Compose smoke | `bash deploy/cluster-smoke-test.sh --config-sync --live-var-lag` |
| Lab soak + journal | `bash deploy/cluster-soak-lab.sh` (default 30m; not CI) |
| Scale 1.8× | `python deploy/cluster-scale-load-test.py --scale-factor-floor 1.8` |
| CI workflow | [`.github/workflows/cluster-load-test.yml`](../../.github/workflows/cluster-load-test.yml) |

## Non-goals (Wave 6)

- No industrial driver protocol changes.
- No ObjectManager / AI agent / BPMN refactors ([0048](decisions/0048-server-modularization-seams.md), [0047](decisions/0047-custom-bpmn-subset-engine.md)).
- No claim that weekly JVM gates replace lab soak.
