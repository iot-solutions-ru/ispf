> **Language:** Canonical English. Russian edition: [ru/getting-started.md](../ru/getting-started.md).

# Getting started

Two tracks:

1. **[Try ISPF](#try-ispf-15-minutes)** — run the platform and open the demo (newcomers).  
2. **[Contribute](#contribute-local-dev--qa)** — fast local QA / pre-push (contributors).

---

## Try ISPF (≈15 minutes)

### Requirements

| Component | Version |
|-----------|---------|
| JDK | 21+ |
| Gradle | Wrapper in the repository |
| Node.js | 20+ |
| Docker Desktop | Optional — only for PostgreSQL / Keycloak / MQTT full stack |

### 1. Start API + console

```bash
# Terminal 1 — local profile (H2, no OAuth; syncs a small set of dev driver packs)
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"

# Terminal 2 — Web Console
cd apps/web-console && npm install && npm run dev
```

| URL | Purpose |
| --- | ------- |
| http://localhost:5173 | Admin console |
| http://localhost:5173?mode=operator | Operator HMI |
| http://localhost:8080/api/v1/info | Version / capabilities |
| http://localhost:8080/actuator/health | Health |

`bootRun` uses **`syncDevDriverPacks`** by default (≈8 packs). Full catalog: `-Dispf.driver.packs=all`.

### 2. First steps in the UI

1. Open the object tree — branch `root.platform`.  
2. Expand `devices` → `demo-sensor-01` — temperature, threshold, alarm variables.  
3. Double-click `dashboards.demo-sensor` — **Dashboard Builder**.  
4. Expand `alert-rules` → `temperature-threshold-exceeded` — CEL alert.  
5. Double-click `workflows.demo-alarm-handler` — **BPMN** demo.  
6. Switch role to **operator** or open `?mode=operator`.

Language: use the console language selector (**English** recommended for screenshots / OSS).

### 3. Start the demo sensor driver

```bash
curl -X POST "http://localhost:8080/api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01" \
  -H "X-ISPF-Role: admin"
```

Temperature is a sine wave; when the threshold is exceeded, `alarmActive` and the alert rule may fire.

### Role selection (`local` profile)

Header **Role** selector: `admin` or `operator` (sends `X-ISPF-Role`).

### Next after the demo

- [Product overview](product.md) · [Object model](object-model.md) · [Dashboards](dashboards.md) · [Automation](automation.md)  
- [Solution developer guide](solution-developer-guide.md) — build a real bundle  
- [Architecture](architecture.md) · [API](api.md)

---

## Optional: full local stack

```bash
docker compose up -d
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=dev"
```

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL (TimescaleDB) | 5432 | Primary database `ispf` |
| Redis | 6379 | Cache (reserved) |
| NATS JetStream | 4222, 8222 | Messaging |
| Mosquitto | 1883 | MQTT broker |
| Keycloak | 8180 | OAuth2 (`dev` profile) |

### Spring profiles

| Profile | DB | Auth | MQTT/NATS |
|---------|-----|------|-----------|
| *(default)* | PostgreSQL | JWT Keycloak | off |
| `local` | H2 file | `X-ISPF-Role` | off |
| `dev` | PostgreSQL | JWT Keycloak localhost:8180 | on |
| `test` | H2 memory | off | off |

More: [deployment](deployment.md), [security](security.md).

### Driver packs

Protocol drivers are **not** inside `ispf-server.jar`. Local `bootRun` / server tests sync **dev packs** automatically.

```bash
./gradlew syncDevDriverPacks          # default — fast local
./gradlew syncAllDriverPacks          # all packs — driver work / prod parity
```

Runtime dir: `./data/drivers` (or `ISPF_DRIVER_PACKS_DIR`). Gradle output: `build/driver-packs`.  
Details: [licensed-driver-packs](licensed-driver-packs.md).

---

## Contribute: local dev & QA

**Do not** start with `./gradlew test` or `syncAllDriverPacks` unless you are changing drivers or running full regression. Those paths build **all ~58 driver packs** and can run **1000+** tests — often **hours** on a cold machine ([issue #65](https://github.com/Michaael/IoT-Solutions-Platform/issues/65)).

### Pre-push check (matches CI pr-fast)

```bash
# Linux/macOS
./tools/ci/pr-fast.sh

# Windows
.\tools\ci\pr-fast.ps1
```

Gradle backend slice:

```bash
./gradlew testPrFast \
  -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev
```

Web console: `cd apps/web-console && npm test && npm run i18n:check && npm run build`.

### Targeted feedback (&lt; 2 min)

```bash
./gradlew :packages:ispf-core:test --tests com.ispf.core.model.DataRecordTest

./gradlew :packages:ispf-server:test --tests com.ispf.server.alert.AlertRuleLatchTest \
  -Dispf.test.skipLoad=true
```

### When you need the full pipeline

| Goal | Command |
|------|---------|
| All driver packs | `./gradlew syncAllDriverPacks` or `-Dispf.driver.packs=all` |
| PR-fast backend | `./gradlew testPrFast -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev` |
| Nightly backend | `./tools/ci/nightly.sh` or `./gradlew testNightlyBackend …` |
| Full server tests | `./gradlew :packages:ispf-server:test` (no `skipLoad`) |
| Everything | `./gradlew build` (slow — avoid daily) |

**Test tiers (issue #65):** PR-fast skips `@Tag("load")` and `@Tag("federation")`; nightly runs them (`tools/ci/nightly.sh`, [ci-nightly.yml](../../.github/workflows/ci-nightly.yml)). Subproject tests run in parallel locally; serialize with `-Dispf.test.serializeSubprojects=true`. CI may cache `build/driver-packs` (`ISPF_DRIVER_PACKS_PREBUILT=true` on cache hit).

Optional: [gradle.properties.example](../../gradle.properties.example) for more Gradle workers.

See also: [testing](testing.md).
