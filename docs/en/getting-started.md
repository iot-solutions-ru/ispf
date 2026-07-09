> **Language:** Canonical English. Russian edition: [ru/getting-started.md](../ru/getting-started.md).

# Getting started

## Requirements

| Component | Version |
|-----------|---------|
| JDK | 21+ |
| Gradle | Wrapper in the repository |
| Node.js | 20+ |
| Docker Desktop | For infrastructure (PostgreSQL, NATS, MQTT, Keycloak) |

## Fast local dev & QA

**Do not** start with `./gradlew test` or `syncAllDriverPacks` unless you are changing drivers or running full regression. Those paths build **all ~58 driver packs** and serialize **1000+** tests — often **hours** on a cold machine ([issue #65](https://github.com/Michaael/IoT-Solutions-Platform/issues/65)).

### Recommended first run (< 30 min warm cache)

```bash
# API (local profile, dev driver packs only — virtual/mqtt/modbus/http/…)
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"

# UI (hot reload, no production build)
cd apps/web-console && npm install && npm run dev
```

`bootRun` and `:packages:ispf-server:test` use **`syncDevDriverPacks`** by default (8 packs). Full catalog: `-Dispf.driver.packs=all` → `syncAllDriverPacks`.

### Pre-push check (matches CI pr-fast)

```bash
# Linux/macOS
./tools/ci/pr-fast.sh

# Windows
.\tools\ci\pr-fast.ps1
```

Equivalent Gradle backend slice:

```bash
./gradlew :packages:ispf-core:test :packages:ispf-expression:test \
  :packages:ispf-plugin-blueprint:test :packages:ispf-plugin-workflow:test \
  :packages:ispf-server:test \
  -Dispf.test.skipLoad=true -Dispf.driver.packs=dev
```

Web console slice: `cd apps/web-console && npm test && npm run i18n:check && npm run build`.

### Targeted feedback (< 2 min)

Core/platform change only — **no driver packs**:

```bash
./gradlew :packages:ispf-core:test --tests com.ispf.core.model.DataRecordTest
```

Server integration test:

```bash
./gradlew :packages:ispf-server:test --tests com.ispf.server.alert.AlertRuleLatchTest \
  -Dispf.test.skipLoad=true
```

### When you need the full pipeline

| Goal | Command |
|------|---------|
| All driver packs (prod parity, driver work) | `./gradlew syncAllDriverPacks` or `-Dispf.driver.packs=all` |
| Full backend regression (CI nightly) | `./gradlew :packages:ispf-server:test` (no `skipLoad`) |
| Everything | `./gradlew build` (slow — avoid for daily iteration) |

Optional: copy [gradle.properties.example](../../gradle.properties.example) for more Gradle workers locally.

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

## 2. Driver packs

Protocol drivers are **not bundled** in `ispf-server.jar`. For local dev, **`syncDevDriverPacks` runs automatically** before `bootRun` and server tests (virtual, mqtt, modbus, http, cwmp, flexible, gps-tracker, application).

Manual sync when needed:

```bash
./gradlew syncDevDriverPacks          # default — fast local QA
./gradlew syncAllDriverPacks          # all packs — driver development / prod deploy
```

Default runtime directory: `./data/drivers` (or `ISPF_DRIVER_PACKS_DIR`).  
Gradle uses `build/driver-packs` after sync.

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

**Avoid** `./gradlew test` at the repo root for day-to-day work — it runs every subproject and pulls the full server test suite.

```bash
./tools/ci/pr-fast.sh    # recommended pre-push (see § Fast local dev & QA)
```

Server integration tests use the `test` profile (H2 in-memory, RBAC disabled). Load/scale gates: omit `-Dispf.test.skipLoad=true` (nightly CI).

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
