> **Language:** Canonical English. Russian edition: [ru/deployment.md](../ru/deployment.md).

# Server Deployment and Configuration

> **Status:** Stable — Docker Compose stack, environment variables, database storage modes, Kubernetes Helm chart, multi-replica cluster, and server upgrades. Status tags: [doc-status.md](doc-status.md).

## 1. Local Infrastructure with Docker Compose

Start the local development and lab infrastructure with a single command:

```bash
docker compose up -d
```

| Service | Docker Image | Ports | Default Credentials |
|---------|--------------|-------|---------------------|
| `postgres` | `timescale/timescaledb:latest-pg16` | `5432` | `ispf` / `ispf` |
| `redis` | `redis:7-alpine` | `6379` | — |
| `nats` | `nats:2.10-alpine` (`-js`) | `4222`, `8222` | — |
| `mosquitto` | `eclipse-mosquitto:2` | `1883` | config: `deploy/mosquitto/` |
| `keycloak` | `keycloak:26.0` (`dev` mode) | `8180` | `admin` / `admin` |

PostgreSQL data volume: `ispf_pg_data`. All services in `docker-compose.yml` start together without extra Compose profiles.

---

## 2. Spring Boot Server (`ispf-server`)

Run from source with `./gradlew :packages:ispf-server:bootRun` or run the fat JAR from `packages/ispf-server/build/libs/`.

### Core Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ISPF_DB_URL` | `jdbc:postgresql://localhost:5432/ispf` | Primary relational database JDBC URL |
| `ISPF_DB_USER` | `ispf` | Database username |
| `ISPF_DB_PASSWORD` | `ispf` | Database password |
| `ISPF_SERVER_PORT` | `8080` | HTTP listen port |
| `ISPF_OAUTH_ISSUER` | Keycloak realm URL | OIDC / JWT issuer URL |
| `ISPF_MQTT_ENABLED` | `false` | Enable built-in MQTT broker integration |
| `ISPF_MQTT_BROKER` | `tcp://localhost:1883` | MQTT broker URL |
| `ISPF_NATS_ENABLED` | `false` | Enable NATS integration (cluster sync and BPMN `messageTask`) |
| `ISPF_NATS_URL` | `nats://localhost:4222` | NATS server URL |
| `ISPF_BOOTSTRAP_FIXTURES_ENABLED` | `true` | Seed demo blueprints and devices (`mqtt-sensor-v1`, etc., see [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md)). Always set to `false` on production servers. |

### Spring Profiles

| Profile | Configuration File | Typical Use |
|---------|--------------------|-------------|
| `default` | `application.yml` | Production baseline: PostgreSQL + OIDC JWT |
| `local` | `application-local.yml` | Quick local evaluation: file-based H2 database, Bearer login via `POST /api/v1/auth/login` (`X-ISPF-Role` disabled by default) |
| `dev` | `application-dev.yml` | Full local development stack with PostgreSQL, MQTT, and NATS |
| `prod` | `application-prod.yml` | Hardened production: `ispf.security.strict-startup=true` by default |
| `test` | `application-test.yml` | Automated tests (in-memory H2) |

#### Shipped entrypoints and profiles

Compose **dependency** stack (`docker compose up` at repo root) does not start ISPF. Which profile the **server** loads:

| Entrypoint | Typical Spring profile | `strict-startup` |
|------------|------------------------|------------------|
| `docker run` GHCR image / `Dockerfile` | `local` | Off (guard skipped on `local`) |
| `deploy/docker-compose.vps-single.yml`, `vps-cluster.yml`, `edge-arm.yml` | `local` | Off (guard skipped on `local`) |
| Helm `deploy/helm/ispf` default values | `local` | Off (guard skipped on `local`) |
| `deploy/docker-compose.cluster.yml`, `analytics.yml`, `prod-stack.yml` | `dev` | Off (warn only) |
| `deploy/docker-compose.air-gap.yml` | `prod` | **On** |

`StartupSecurityGuard` skips `local` and `test` entirely (no warn, env flag ignored). Before any network exposure, leave `local` for `--spring.profiles.active=prod`, or set `ISPF_SECURITY_STRICT_STARTUP=true` on a **non-`local`/`test`** profile — [security](security.md).

### Database and Time-Series Storage Modes

- **Flyway schema migrations** run automatically at startup (`ddl-auto: validate`), selecting the script directory via `RelationalDialect` ([ADR-0037](decisions/0037-relational-core-portability.md)).
- **Production PostgreSQL** uses the **TimescaleDB** extension (`variable_samples` and `event_history` hypertables with 90-day retention policies — see [ADR-0009](decisions/0009-timescaledb-retention.md) and [ADR-0015](decisions/0015-event-history-timescale.md)).

| Storage Mode | Metadata & Tree (`ISPF_DB_*`) | Event Journal | Variable History |
|--------------|-------------------------------|---------------|------------------|
| **Single Database (default)** | PostgreSQL | `ISPF_EVENT_JOURNAL_STORE=jdbc` | `ISPF_VARIABLE_HISTORY_STORE=jdbc` |
| **Split High-Rate Telemetry** | PostgreSQL | `clickhouse` or `cassandra` | `clickhouse` or `cassandra` |

For most sites, a **single PostgreSQL database** handles configuration, the object tree, `event_history`, and `variable_samples`. Offloading history and events to ClickHouse is recommended only under high sustained telemetry rates (see [ClickHouse production playbook](clickhouse-prod-playbook.md) and [Historian storage tiers](historian-tiers.md)).

**Application Data Sources (`root.platform.data-sources.*`):**
- `internal` — isolated schema inside the primary `ISPF_DB`;
- `external` — JDBC connection to an external database for SQL reports and `sqlBinding` queries.

---

## 3. Building the Web Console (`apps/web-console`)

Build static assets for nginx or ingress:

```bash
cd apps/web-console && npm run build
```

Serve `dist/` behind nginx/ingress with `/api/`, `/ws/`, and `/apps/` proxied to `ispf-server`. For UI development, run `npm run dev` (port `5173`) next to `bootRun` (API on port `8080`); the reason for two processes is in [Getting started](getting-started.md#why-two-processes-and-two-ports). The release JAR is built with `-PembedWebConsole=true` and contains the console, so the portable zip and the container serve the console and the API on port `8080`.

---

## 4. Local Pre-Production Stack Quick Start (`prod-quickstart.sh`)

On a Linux host with Docker Engine + Compose v2, JDK 25, and Node.js 20+, one script builds the server JAR and Web Console from source and starts a pre-production container stack (PostgreSQL + Redis + `ispf-server` + nginx):

```bash
bash deploy/prod-quickstart.sh
```

> **Note:** this script binds ports to `127.0.0.1` and disables demo fixtures, but uses default database credentials (`ispf/ispf`). Before exposing a host to the network, change all database passwords and configure TLS. See **[Deployment profiles (`demostands.md`)](demostands.md)** for full production sizing and security guidance.

| Endpoint | URL |
|----------|-----|
| Server API info | `http://127.0.0.1:8080/api/v1/info` |
| Actuator health | `http://127.0.0.1:8080/actuator/health` (internal only; not proxied by nginx) |
| Web Console (nginx) | `http://127.0.0.1:8088/` |

Stop the stack (preserving the `ispf_prod_pg` volume; pass `-v` to wipe data):

```bash
docker compose -f deploy/docker-compose.prod-stack.yml down
```

---

## 5. Kubernetes Helm Chart (`deploy/helm/ispf`)

The Helm chart in [`deploy/helm/ispf`](../../deploy/helm/ispf/) requires Helm 3.14+:

```bash
# Lint and template validation (no cluster required)
bash deploy/helm/ispf/validate.sh

# Install into Kubernetes
helm upgrade --install ispf deploy/helm/ispf \
  --namespace ispf --create-namespace \
  --set secrets.dbPassword='change-me' \
  --set ispf.bootstrap.fixturesEnabled=false

# Verify rollout
kubectl -n ispf rollout status deploy/ispf --timeout=180s
kubectl -n ispf port-forward svc/ispf 8080:80 &
curl -sf http://127.0.0.1:8080/actuator/health
curl -sf http://127.0.0.1:8080/api/v1/info
```

For ARM64 edge gateways on Kubernetes, set `edge.enabled=true` and `edge.arm64=true` (for Docker Compose on ARM64 gateways, see [`deploy/edge/arm64/README.md`](../../deploy/edge/arm64/README.md)).

---

## 6. Offline / Air-Gapped Installation (`air-gap`)

For isolated plant networks and DMZs without internet access, build a self-contained offline archive on a connected machine and apply it on the target host:

```bash
# On connected build host
bash deploy/air-gap-pack.sh --version 0.9.32

# On isolated target host
bash deploy/air-gap-apply.sh /path/to/ispf-airgap-0.9.32.tar.gz
```

Full runbook: **[Air-gapped deployment (`air-gap-deployment.md`)](air-gap-deployment.md)**.

---

## 7. Bundle Signature Verification (`bundle.json`)

Commercial bundles can carry an RSA-signed `license` block. To require valid signatures on production servers:

| Environment Variable / Property | Default | Description |
|---------------------------------|---------|-------------|
| `ISPF_LICENSE_PUBLIC_KEY_PEM` / `ispf.license.public-key-pem` | empty | RSA public key(s) in PEM format (multiple blocks supported for key rotation) |
| `ISPF_LICENSE_ENFORCE` / `ispf.license.enforce` | `false` | Reject invalid signatures with HTTP `403` |
| `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES` / `ispf.license.require-signed-bundles` | `false` | Reject unsigned manifests (or invalid signatures) with HTTP `403` |

Details: **[Commercial bundle licensing (`commercial-licensing.md`)](commercial-licensing.md)**.

---

## 8. Multi-Replica High-Availability Cluster

```text
                    ┌─────────────┐
   Clients ────────►│    nginx    │  round-robin REST + sticky WebSocket
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ispf-server-1   ispf-server-2   ispf-server-N
           │               │               │
           └───────────────┼───────────────┘
                           ▼
              PostgreSQL + Redis + NATS JetStream
```

- Stateless `ispf-server` replicas share one PostgreSQL object tree ([ADR-0028](decisions/0028-horizontal-active-active-cluster.md)).
- Device polling is partitioned across replicas via `platform_driver_locks`, and singleton schedulers use `platform_leader_locks`.
- Full architecture, replica profiles (`edge-api`, `io`, `hmi-read`, `compute`, `analytics`), and staged startup order: **[ISPF Cluster (`cluster.md`)](cluster.md)**.

### Local 3-Replica Cluster Quick Start

```bash
bash deploy/cluster-quickstart.sh
# Web Console + API behind nginx load balancer: http://127.0.0.1:8088/
```

### Required Environment Variables per Cluster Replica

| Variable | Example | Purpose |
|----------|---------|---------|
| `ISPF_REPLICA_ID` | `replica-1` | Unique node identifier (returned in `/api/v1/info`) |
| `ISPF_DB_URL` | `jdbc:postgresql://postgres:5432/ispf` | Shared PostgreSQL database |
| `ISPF_CLUSTER_ENABLED` | `true` | Enables driver lock partitioning and cluster health APIs |
| `ISPF_NATS_ENABLED` | `true` | Enables cross-replica event fan-out |
| `ISPF_NATS_REPLICA_EVENTS` | `true` | Synchronizes tree changes and WebSocket notifications across replicas |
| `ISPF_NATS_JETSTREAM_ENABLED` | `true` | Durable structure sync stream ([ADR-0069](decisions/0069-cluster-consistency.md)) |
| `ISPF_CLUSTER_LIVE_VARIABLE_SYNC` | `true` | Mirrors live telemetry values in RAM across replicas over NATS ([ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md)) |
| `ISPF_CLUSTER_PATH_INTEREST` | `true` | Tracks active WebSocket subscriptions cluster-wide in Redis |
| `ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS` | `500` | Coalescing window for NATS live-value fan-out |
| `ISPF_REDIS_ENABLED` | `true` | Required for cluster path interest, live mirror, correlator windows, and ACL cache |

---

## 9. Standalone All-in-One JAR and Linux `systemd` Setup

### All-in-One JAR
Release builds (`-PembedWebConsole=true`) embed the Web Console directly inside `ispf-server.jar`. On any machine with JDK 25+ (Windows, Linux, macOS):

```bash
java -jar ispf-server.jar --spring.profiles.active=local
```

Open `http://localhost:8080` for both the Web Console and REST API.

### Linux `systemd` + `nginx` Installation
The script `deploy/remote-setup-ispf.sh` provisions an Ubuntu 24.04+ server with Temurin JDK 25, installs `/opt/ispf/ispf-server.jar` and `/opt/ispf/web-console/`, registers `ispf-server.service` on port `8080`, and configures `nginx` on port `80`.
