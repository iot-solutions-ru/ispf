> **Language:** Canonical English. Russian edition: [ru/deployment.md](../ru/deployment.md).

# Deployment and configuration

## Docker Compose

```bash
docker compose up -d
```

| Service | Image | Ports | Credentials |
|--------|-------|-------|-------------|
| postgres | timescale/timescaledb:latest-pg16 | 5432 | ispf/ispf |
| redis | redis:7-alpine | 6379 | — |
| nats | nats:2.10-alpine (-js) | 4222, 8222 | — |
| mosquitto | eclipse-mosquitto:2 | 1883 | config: deploy/mosquitto/ |
| keycloak | keycloak:26.0 dev | 8180 | admin/admin |

Volume: `ispf_pg_data`.

Compose profiles **are not used** — all services start together.

## Spring Boot Server

Artifact: `:packages:ispf-server:bootRun` or JAR from `build/libs/`.

### Environment variables

| Variable | Default | Description |
|------------|---------|----------|
| `ISPF_DB_URL` | `jdbc:postgresql://localhost:5432/ispf` | JDBC URL |
| `ISPF_DB_USER` | ispf | DB user |
| `ISPF_DB_PASSWORD` | ispf | DB password |
| `ISPF_SERVER_PORT` | 8080 | HTTP port |
| `ISPF_OAUTH_ISSUER` | Keycloak realm URL | JWT issuer |
| `ISPF_MQTT_ENABLED` | false | MQTT integration |
| `ISPF_MQTT_BROKER` | tcp://localhost:1883 | Broker URL |
| `ISPF_NATS_ENABLED` | false | NATS integration |
| `ISPF_NATS_URL` | nats://localhost:4222 | NATS URL |
| `ISPF_BOOTSTRAP_FIXTURES_ENABLED` | true | Demo/lab fixture models and demo nodes (`mqtt-sensor-v1`, …). See [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md). **Prod VPS:** `vps-deploy-direct.ps1` sets `false`; lab demo — `vps-factory-reset.sh --fixtures`. |

### Profiles

| Profile | File | Scenario |
|---------|------|----------|
| default | application.yml | PostgreSQL + JWT |
| local | application-local.yml | H2 file, X-ISPF-Role |
| dev | application-dev.yml | Full stack + MQTT/NATS |
| test | application-test.yml | H2 memory, tests |

### Database

- **Flyway** — migrations on startup (`ddl-auto: validate`); locations per `RelationalDialect` (see [0037-relational-core-portability](decisions/0037-relational-core-portability.md))
- **local:** H2 `./data/ispf-local` (PostgreSQL compatibility mode)
- **prod:** PostgreSQL; TimescaleDB extension (docker image `timescale/timescaledb`) — hypertables `variable_samples` and `event_history`, retention 90d ([0009-timescaledb-retention](decisions/0009-timescaledb-retention.md), [0015-event-history-timescale](decisions/0015-event-history-timescale.md))

#### Storage modes

| Mode | Metadata (`ISPF_DB_*`) | Events | Variable history |
|-------|------------------------|---------|-------------------|
| **Single DB** (default) | PostgreSQL | `ISPF_EVENT_JOURNAL_STORE=jdbc` | `ISPF_VARIABLE_HISTORY_STORE=jdbc` |
| **Split telemetry** | PostgreSQL | `clickhouse` / `cassandra` | `clickhouse` / `cassandra` |

Single DB is the standard scenario: configuration, objects, `event_history`, and `variable_samples` in one PostgreSQL. ClickHouse/Cassandra — only under high time-series load.

Optionally: `ISPF_METADATA_DB_KIND` (`postgresql`, `h2`, `mssql`, …) — see [storage-portability-inventory](storage-portability-inventory.md).

**Changing metadata engine (greenfield, before v1.0):** new empty DB, Flyway baseline for the chosen dialect, configuration — import bundles. In-place PG→MSSQL migration is not supported.

Platform **Data Sources** (`root.platform.data-sources.*`): `internal` — schema in `ISPF_DB`; `external` — JDBC to external DB for reports and SQL bindings (not metadata).

### Messaging

| Broker | Enable | Usage |
|--------|-----------|---------------|
| MQTT | `ispf.mqtt.enabled=true` | Device drivers |
| NATS | `ispf.nats.enabled=true` | Workflow messageTask |

## Web Console

Static build:

```bash
cd apps/web-console && npm run build
```

`dist/` — serve via nginx/ingress with proxy to API.

Dev: `npm run dev`, proxy to backend.

## Production quick start (BL-127)

One command brings up the **lab / localhost** stack on a Linux host with Docker (PostgreSQL + Redis + `ispf-server` + nginx for UI). Not tied to VPS `ispf.example.invalid`.

**Not for internet-facing production without hardening:** ports are bound to `127.0.0.1`, demo fixtures are disabled, default DB passwords (`ispf/ispf`) must be changed before exposing to the network. Images use pinned tags from `deploy/air-gap-images.env`. Full prod checklist: [demostands.md § Production](demostands.md).

**Requirements:** Docker Engine + Compose v2, JDK 25 (for build), Node.js 20+ (for UI).

```bash
bash deploy/prod-quickstart.sh
```

The script:

1. Builds `ispf-server` JAR (`bootJar`, no tests).
2. Builds `apps/web-console` (`npm ci && npm run build`).
3. Copies JAR to `deploy/staging/ispf-server.jar`.
4. Starts `deploy/docker-compose.prod-stack.yml`.
5. Waits for readiness via `deploy/health-check.sh`.

| Endpoint | URL |
|----------|-----|
| API info | http://127.0.0.1:8080/api/v1/info |
| Actuator health | http://127.0.0.1:8080/actuator/health (not proxied through nginx) |
| Web UI (nginx) | http://127.0.0.1:8088/ |

Stop:

```bash
docker compose -f deploy/docker-compose.prod-stack.yml down
```

PostgreSQL volumes are preserved (`ispf_prod_pg`). Full cleanup: add `-v`.

**Files:**

| File | Purpose |
|------|------------|
| `deploy/docker-compose.prod-stack.yml` | postgres, redis, ispf-server (Temurin JRE + mounted JAR), nginx |
| `deploy/air-gap-images.env` | Pinned image tags (shared with BL-128 air-gap pack) |
| `deploy/nginx-local-prod.conf` | proxy `/api/`, `/ws/` → server; static SPA (no `/actuator/`) |
| `deploy/health-check.sh` | Poll `/actuator/health` + smoke `/api/v1/info` |

**Prod VPS:** for `ispf.example.invalid`, continue to use `deploy/vps-deploy-direct.ps1` (direct SCP + staging), not this quick start.

## Air-gap deployment (BL-128)

Hosts without outbound internet: offline bundle (JAR, UI zip, driver packs, Docker images) and runbook.

```bash
# Build host (connected)
bash deploy/air-gap-pack.sh --version 0.9.32

# Target host (isolated)
bash deploy/air-gap-apply.sh /path/to/ispf-airgap-0.9.32.tar.gz
```

Full checklist, licensing, and offline update: [air-gap-deployment](air-gap-deployment.md).

## Bundle signing (BL-100)

Commercial bundle manifests may contain an RSA-signed `license` block. Signing is **optional** by default; for production marketplace, enable mandatory verification:

| Variable / property | Default | Description |
|-----------------------|---------|----------|
| `ISPF_LICENSE_PUBLIC_KEY_PEM` / `ispf.license.public-key-pem` | empty | PEM RSA public key(s); multiple blocks for rotation |
| `ISPF_LICENSE_ENFORCE` / `ispf.license.enforce` | `false` | invalid license → HTTP 403 on deploy/import |
| `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES` / `ispf.license.require-signed-bundles` | `false` | manifest **without** `license` or with invalid signature → 403 |

Import/deploy behavior:

| Condition | `require-signed-bundles=false` | `require-signed-bundles=true` |
|---------|-------------------------------|------------------------------|
| No `license` | OK | HTTP 403 |
| Valid signed `license` | OK | OK |
| Invalid / tampered signature | WARN (if `enforce=false`) or 403 | HTTP 403 |

Details: [commercial-licensing](commercial-licensing.md) (deploy behavior, key rotation).

## Production topology (target)

```
                    ┌─────────────┐
   Clients ────────►│   Ingress   │  nginx round-robin (REST) + sticky WS
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ispf-server-1   ispf-server-2   ispf-server-N
           │               │               │
           └───────────────┼───────────────┘
                           ▼
              PostgreSQL + Redis + NATS JetStream
```

- Stateless API replicas share one PostgreSQL tree ([0028-horizontal-active-active-cluster](decisions/0028-horizontal-active-active-cluster.md)).
- Driver poll loops: exactly-one owner per device via `platform_driver_locks` (BL-136).
- Singleton schedulers: JDBC leader locks (`platform_leader_locks`).

## Multi-instance cluster (BL-134…139)

Detailed guide: **[cluster](cluster.md)** — topology, bindings, ADR-0029, tuning, and **[cluster startup order](cluster.md#cluster-startup-and-configuration)** (staged bootstrap, profiles, nginx, lab BL-210).

**Lab / Enterprise L (4 profiles: edge×2 + analytics + io):**

```bash
# On lab host after jar/UI upload:
bash ~/ispf/lab-cluster-bootstrap.sh
# Ingress: http://<lab>:8000/
```

Artifacts: [`deploy/lab-cluster-compose.yml`](../deploy/lab-cluster-compose.yml), [`deploy/lab-cluster-bootstrap.sh`](../deploy/lab-cluster-bootstrap.sh), [`deploy/local/nginx/cluster-lab.conf`](../deploy/local/nginx/cluster-lab.conf).

**Local CI stack (3 generic replicas):**

```bash
bash deploy/cluster-quickstart.sh
# UI + API via LB: http://127.0.0.1:8088/
```

Compose: [`deploy/docker-compose.cluster.yml`](../deploy/docker-compose.cluster.yml).  
Ingress: [`deploy/nginx-cluster.conf`](../deploy/nginx-cluster.conf).

### Required env (each replica)

| Variable | Example | Notes |
|----------|---------|-------|
| `ISPF_REPLICA_ID` | `replica-1` | Unique per node; exposed in `/api/v1/info` |
| `ISPF_DB_URL` | `jdbc:postgresql://postgres:5432/ispf` | Same DB for all replicas |
| `ISPF_CLUSTER_ENABLED` | `true` | Enables driver ownership + cluster health API |
| `ISPF_NATS_ENABLED` | `true` | Cross-replica WS/object-change fan-out |
| `ISPF_NATS_REPLICA_EVENTS` | `true` | Required for multi-replica UI sync |
| `ISPF_CLUSTER_LIVE_VARIABLE_SYNC` | `true` | NATS live-value RAM mirror ([0029-cluster-live-variable-replica-sync](decisions/0029-cluster-live-variable-replica-sync.md)) |
| `ISPF_CLUSTER_PATH_INTEREST` | `true` | Redis global WS interest (requires Redis) |
| `ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS` | `500` | NATS fan-out coalesce (separate from `ISPF_RUNTIME_TELEMETRY_COALESCE_MS`) |
| `ISPF_REDIS_ENABLED` | `true` | Recommended (correlator windows, ACL cache, cluster path interest) |

Optional tuning: `ISPF_CLUSTER_DRIVER_LOCK_TTL_SECONDS` (default 30), `ISPF_CLUSTER_DRIVER_LOCK_RENEW_MS` (default 10000).

### Ops runbook

**Add node**

1. Copy an existing `ispf-server-N` service block in compose; set unique `ISPF_REPLICA_ID`.
2. Add `server ispf-server-N:8080` to `upstream ispf_backend` and `ispf_ws_backend` in nginx config.
3. `docker compose -f deploy/docker-compose.cluster.yml up -d ispf-server-N nginx`
4. Verify: `GET /api/v1/platform/cluster/health` (admin) shows `replicaId` and held driver locks.

**Remove node (graceful)**

1. `docker stop ispf-server-N` — nginx marks upstream down after `max_fails`.
2. Driver locks on that node expire within TTL; another replica reclaims via `DriverOwnershipScheduler`.
3. Remove service from compose/nginx when drained.

**Failover verify**

1. `curl -sf http://127.0.0.1:8088/api/v1/info` — should succeed with any replica up.
2. Stop one replica: REST must not 502; WS clients on other replicas stay connected; NATS propagates live variable snapshots ([0029-cluster-live-variable-replica-sync](decisions/0029-cluster-live-variable-replica-sync.md)).
3. Admin → System → Metrics → cluster health card (`/api/v1/platform/cluster/health`).

**Ops checklist (BL-139)**

- [ ] `ISPF_CLUSTER_ENABLED=true` and unique `ISPF_REPLICA_ID` on every replica
- [ ] Shared PostgreSQL reachable from all nodes; Flyway migrations applied once
- [ ] NATS enabled with `ISPF_NATS_REPLICA_EVENTS=true` for cross-replica UI sync
- [ ] Redis enabled (recommended) for correlator windows / ACL cache
- [ ] Nginx upstream lists all healthy `ispf-server-*` backends; `/ws/` uses `ip_hash`
- [ ] `GET /api/v1/platform/cluster/health` (admin) — all nodes `UP`, driver locks visible
- [ ] Smoke: `bash deploy/cluster-smoke-test.sh` (round-robin, REST failover, driver reclaim)
- [ ] Config sync: `bash deploy/cluster-smoke-test.sh --config-sync` ([0030-cluster-config-structure-replica-sync](decisions/0030-cluster-config-structure-replica-sync.md))
- [ ] Scale gate (lab/CI): `python deploy/cluster-scale-load-test.py --scale-factor-floor 1.8`
- [ ] Kill one replica: REST via LB stays 200; driver locks migrate within TTL + failover scan

**Automated gates**

| Gate | Command / workflow |
| ---- | ------------------ |
| JDBC ownership | `./gradlew :packages:ispf-server:test --tests com.ispf.server.driver.ClusterFailoverIntegrationTest` |
| Compose smoke | `bash deploy/cluster-smoke-test.sh` |
| Config/structure sync smoke | `bash deploy/cluster-smoke-test.sh --config-sync` |
| Scale-out 1.8× | `python deploy/cluster-scale-load-test.py` |
| CI | [`.github/workflows/cluster-load-test.yml`](../.github/workflows/cluster-load-test.yml) |

### Prod VPS (single-node example)

The current public example uses a **single unified node**. Generalized deployment profiles: **[demostands](demostands.md)** (production, throughput, demo-idle, edge). Single-node ops template: [vps-demostand](vps-demostand.md).

| Script | When |
|--------|------|
| [`vps-single-rollout.sh`](../deploy/vps-single-rollout.sh) | Jar + UI on single-node; cluster → single migration |
| [`vps-apply-prod-idle-env.sh`](../deploy/vps-apply-prod-idle-env.sh) | Merge prod-idle overlay + **recreate** container (not `docker restart`) |
| [`vps-demostand-tune-drivers.sh`](../deploy/vps-demostand-tune-drivers.sh) | After recreate: SNMP `TELEMETRY_ONLY`, demo-sensor `FULL` |
| [`vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py) | Thread CPU diagnostic (SSH) |
| [`ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env) | Demo-idle overlay ([demostands](demostands.md)) |
| [`vps-deploy-direct.ps1`](../deploy/vps-deploy-direct.ps1) | Local build + SCP staging |

```powershell
.\deploy\vps-deploy-direct.ps1 -Version <ver> -SkipTests -SkipDriverPacks
```

On the host after upload:

```bash
bash /opt/ispf/bin/vps-single-rollout.sh /opt/ispf/staging/<ver>
bash /opt/ispf/bin/vps-demostand-tune-drivers.sh   # if demo-idle driver profile is needed
curl -sf http://127.0.0.1:8080/api/v1/info
```

**Do not use** [`apply-platform-update.sh`](../deploy/apply-platform-update.sh) on Docker single-node — the script is for **systemd** `:8080`.

#### Multi-replica cluster (optional)

| Script | When |
|--------|------|
| [`vps-deploy-direct.ps1 -Cluster`](../deploy/vps-deploy-direct.ps1) | Routine jar + UI rollout |
| [`vps-cluster-rollout.sh`](../deploy/vps-cluster-rollout.sh) | Restart replicas after staging upload |
| [`vps-cluster-bootstrap.sh`](../deploy/vps-cluster-bootstrap.sh) | First-time cluster install only |
| [`vps-cluster-factory-reset.sh`](../deploy/vps-cluster-factory-reset.sh) | Drop PG + reset Scylla/Redis |
| [`vps-cluster-verify.sh`](../deploy/vps-cluster-verify.sh) | Post-deploy health; `--config-sync` smoke |

```powershell
.\deploy\vps-deploy-direct.ps1 -Version <ver> -SkipTests -Cluster
ssh user@host 'bash /opt/ispf/bin/vps-cluster-verify.sh --config-sync'
```

**Rollback cluster → single:** [demostands](demostands.md), [vps-demostand](vps-demostand.md).

## ClickHouse variable history (prod playbook, BL-114)

Full ops guide: **[clickhouse-prod-playbook](clickhouse-prod-playbook.md)** — install, event journal, historian cutover, **dual-write** (BL-116), verify, rollback.

Default historian: PostgreSQL/Timescale (`ISPF_VARIABLE_HISTORY_STORE=jdbc`). Dual-write: `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true` + CH credentials (reads remain in PG).

## Monitoring

Actuator endpoints:

- `/actuator/health` — liveness/readiness
- `/actuator/prometheus` — metrics (admin role); ISPF gauges `ispf_event_history_records`, `ispf_alert_fires_total`, `ispf_object_change_queue_size`, …
- `/actuator/metrics` — Micrometer

**Metrics probe:** sync `/api/v1/platform/metrics` → `root.platform.devices.platform-metrics-probe`. Enable at **runtime** via Admin → System → Metrics → Load diagnostics (checkbox) or `PUT /api/v1/platform/diagnostics/metrics-probe` with `{ "enabled": true }`. Boot env `ISPF_PLATFORM_METRICS_PROBE_ENABLED` does not start the scheduler (see [observability](observability.md)). Create device: `python deploy/setup-platform-metrics-monitor.py`.

## Logging

```yaml
logging.level.com.ispf: INFO   # default
logging.level.com.ispf: DEBUG  # local/dev
```

## Local data

| Profile | Data path |
|---------|-------------|
| local | `./data/ispf-local.mv.db` |
| dev | PostgreSQL volume `ispf_pg_data` |

Stand cleanup: delete H2 file or `docker compose down -v` (PostgreSQL).

## Remote host (systemd + nginx)

Script `deploy/remote-setup-ispf.sh` — one-time install on Linux VPS (Ubuntu 24.04+):

1. Stops extra services on the host (configured for the specific server).
2. Installs Temurin JDK 25.
3. Places artifacts in `/opt/ispf`:
   - `ispf-server.jar` ← `/tmp/ispf-server.jar`
   - `web-console/` ← `/tmp/ispf-web-console-dist/`
4. Creates `ispf-server.service` (port **8080**). On **prod VPS** (`ispf.example.invalid`), datasource is set via `/opt/ispf/ispf-server.env` → **PostgreSQL** in Docker (`ispf-postgres`), despite `--spring.profiles.active=local` in the unit file. Directory `/opt/ispf/data/` — service files (auto-update, installation-id), **not** H2.
5. Configures nginx on **80** (`ai.example.invalid`): static UI + proxy `/api/`, `/ws/`, `/actuator/`.
6. Optional: if `/tmp/snmpd-ispf.conf` is present — installs snmpd for demo `snmp-localhost`.

Prepare and run on the server:

```bash
# Local: build
./gradlew :packages:ispf-server:bootJar
cd apps/web-console && npm run build
scp packages/ispf-server/build/libs/ispf-server-*.jar user@host:/tmp/ispf-server.jar
scp -r apps/web-console/dist user@host:/tmp/ispf-web-console-dist
scp deploy/remote-setup-ispf.sh deploy/snmpd-ispf.conf user@host:/tmp/
ssh user@host 'bash /tmp/remote-setup-ispf.sh'
```

Verify: `http://host/` (UI), `http://host/api/v1/info`.

Additional scripts:

| Script | Purpose |
|--------|------------|
| `deploy/remote-cleanup-apache-ispf-only.sh` | Remove Apache/ISPmanager, keep only ISPF on :80 |
| `deploy/update-snmp-mappings.sh` | Update OID mappings on a running server |
| `deploy/start-snmp-driver.sh` | Login + start SNMP driver |
| `deploy/apply-platform-update.sh` | Install jar + UI from staging and restart systemd (called by update API) |
| `deploy/remote-update-ispf.sh` | Manual update from `/tmp` artifacts |

### Auto-update from GitHub Releases

1. Publish a release: `git tag v0.1.1 && git push origin v0.1.1` — workflow `.github/workflows/release.yml` builds `ispf-server.jar` and `web-console.zip`.
2. On VPS, `ispf-server.service` has:
   - `ISPF_UPDATE_CHECK_ENABLED=true` — periodic check (default: once per hour)
   - `ISPF_UPDATE_APPLY_ENABLED=true` — "Update and restart" button in admin console
3. Admin sees a banner when GitHub has a newer version than the current jar (`/api/v1/info` → `version` from build-info).

Locally `apply-enabled=false` — notification only / link to release.

### SNMP demo driver on remote

After deploying the SNMP agent on the host (`127.0.0.1:161`, see `deploy/snmpd-ispf.conf`) and device `root.platform.devices.snmp-localhost`:

```bash
bash deploy/start-snmp-driver.sh
```

The script logs in (`admin`/`admin`), calls `POST /api/v1/drivers/runtime/start?devicePath=...`, and prints status. Default `API=http://127.0.0.1:8080` (JVM); via nginx: `API=http://127.0.0.1`.

## Upgrade to v0.8.0+

> **Runbook for upgrading from pre-0.8.0.** One-time breaking change [0010-binding-rules-only](decisions/0010-binding-rules-only.md): column `binding_expr` removed (`V41`), checksum `V1` changed — **easier to recreate the DB** than migrate legacy bindings. On prod 0.9.x, normal deploy via [vps-deploy-direct.ps1](../deploy/vps-deploy-direct.ps1) — no DB recreation.

### Prod VPS (`ispf.example.invalid`) — PostgreSQL in Docker

DB: container **`ispf-postgres`** (`timescale/timescaledb`), JDBC from `/opt/ispf/ispf-server.env` (`SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/ispf`).

```bash
# 1. Backup (optional)
docker exec ispf-postgres pg_dump -U ispf ispf > ispf-pre-0.8.27.sql

# 2. Stop server, recreate DB
systemctl stop ispf-server
docker exec ispf-postgres psql -U ispf -d postgres \
  -c 'DROP DATABASE IF EXISTS ispf;' \
  -c 'CREATE DATABASE ispf OWNER ispf;'

# 3. Deploy jar + UI (deploy/vps-deploy-direct.ps1 or apply-platform-update.sh)
#    If jar/UI already in staging — systemctl start is enough
systemctl start ispf-server

# 4. Flyway applies schema from V1 without binding_expr on clean DB
curl -sf ${ISPF_BASE_URL:-https://ispf.example.invalid}/api/v1/info
```

After recreation: binding rules — Web Console → Bindings; mini-TEC — `MiniTecPlatformBootstrap` on startup.

### PostgreSQL (generic / docker compose)

```bash
docker compose exec postgres psql -U ispf -d postgres \
  -c 'DROP DATABASE IF EXISTS ispf;' -c 'CREATE DATABASE ispf OWNER ispf;'
```

### H2 (local dev only)

Delete `./data/ispf-local.mv.db` or change `spring.datasource.url`. See [bindings](bindings.md).

## SCADA NFR (mini-TEC / production)

Targets from mini-TEC mimic requirements (§4) — **operational**, not bootstrap:

| Requirement | ISPF recommendation |
|------------|-------------------|
| 99.9% availability | `ispf-server` cluster + health checks; see [cluster](cluster.md) |
| ≤50 concurrent operators | Redis session/cache (`redis` in compose); nginx sticky sessions |
| 7-year telemetry archive | TimescaleDB retention policies + ClickHouse `store=clickhouse` for events |
| Backup | `pg_dump` / volume snapshots; runbook in [air-gap-deployment](air-gap-deployment.md) |
| Zero-downtime update | Blue/green via `apply-platform-update.sh` + staged jar/UI |
| Sessions / timeout | Keycloak realm: SSO idle timeout, max session; see [security](security.md) |
| Email/SMS alarms | `ISPF_NOTIFICATIONS_EMAIL_RELAY_URL` + webhook to SMS gateway; [automation](automation.md) |
| Offline operator HMI | PWA cache web-console static; HMI layout in `dashboard-v1.layout` |
| OPC-UA field level | Driver pack `opcua`; GPU variable mapping — lab note in [examples/mini-tec/README.md](readme.md) |

**Lab demo mini-TEC:** `ISPF_BOOTSTRAP_FIXTURES_ENABLED=true` (default local). **Prod VPS:** fixtures off — import via `POST /api/v1/applications/mini-tec/deploy` or factory-reset with `--fixtures`.
