> **Language:** Canonical English. Russian edition: [ru/getting-started.md](../ru/getting-started.md).

# Getting started

## Requirements

| Component | Version |
|-----------|---------|
| JDK | 21+ |
| Gradle | Wrapper in the repository |
| Node.js | 20+ |
| Docker Desktop | For infrastructure (PostgreSQL, NATS, MQTT, Keycloak) |

## 1. Infrastructure

```bash
docker compose up -d
```

Services started:

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL (TimescaleDB) | 5432 | Primary database `ispf` |
| Redis | 6379 | Cache (reserved) |
| NATS JetStream | 4222, 8222 | Messaging |
| Mosquitto | 1883 | MQTT broker |
| Keycloak | 8180 | OAuth2 (`dev` profile) |

## 2. Driver packs (required)

Protocol drivers are **not bundled** in `ispf-server.jar`. Build packs before the first run:

```bash
./gradlew syncAllDriverPacks
```

Default directory: `./data/drivers` (or `ISPF_DRIVER_PACKS_DIR`).  
`bootRun` and Gradle tests automatically use `build/driver-packs` after `syncAllDriverPacks`.

Copy packs to the server:

```bash
cp -r build/driver-packs/* /opt/ispf/data/drivers/
```

Details: [licensed-driver-packs.md](licensed-driver-packs.md).

## 3. Start the API server

### Local development without OAuth (recommended)

H2 file DB, RBAC via `X-ISPF-Role` header:

```bash
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"
```

Data is stored in `./data/ispf-local.mv.db`.

### Development with full stack

PostgreSQL + Keycloak + MQTT + NATS:

```bash
docker compose up -d
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=dev"
```

### Verify

```bash
curl http://localhost:8080/api/v1/info
curl http://localhost:8080/actuator/health
```

## 4. Web Console

```bash
cd apps/web-console
npm install
npm run dev
```

Console: http://localhost:5173

Vite proxies `/api` and `/ws` to `http://localhost:8080`.

### Operator mode (HMI)

http://localhost:5173?mode=operator

Opens a dashboard in view mode plus a sidebar (work queue, event journal).

### Role selection (`local` profile)

Use the **Role** selector in the console header: `admin` or `operator`.  
Sends the `X-ISPF-Role` header.

## 5. First steps in the UI

1. Open the object tree on the left — the `root.platform` branch.
2. Expand `devices` → `demo-sensor-01` — temperature, threshold, and alarm variables.
3. Double-click `dashboards.demo-sensor` — opens **Dashboard Builder**.
4. Double-click `workflows.demo-alarm-handler` — **Workflow Builder** with BPMN.
5. Expand `alert-rules` and `correlators` — automation rules in the tree; use the inspector on the right to edit.

## 6. Start a device driver

For `demo-sensor-01`, the virtual driver starts automatically on first `start`:

```bash
curl -X POST "http://localhost:8080/api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01" \
  -H "X-ISPF-Role: admin"
```

Temperature is simulated as a sine wave; when the threshold is exceeded, the `alarmActive` binding fires and an alert rule may trigger.

## 7. Tests

```bash
./gradlew test
```

Server integration tests use the `test` profile (H2 in-memory, RBAC disabled).

## Spring profiles

| Profile | DB | Auth | MQTT/NATS |
|---------|-----|------|-----------|
| *(default)* | PostgreSQL | JWT Keycloak | off |
| `local` | H2 file | `X-ISPF-Role` | off |
| `dev` | PostgreSQL | JWT Keycloak localhost:8180 | on |
| `test` | H2 memory | off | off |

More: [deployment.md](deployment.md), [security.md](security.md).

## Next steps

- [object-model.md](object-model.md) — paths, variables, and bindings
- [api.md](api.md) — full REST reference
- [dashboards.md](dashboards.md) — building HMI screens
