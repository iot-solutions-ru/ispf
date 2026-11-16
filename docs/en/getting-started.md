> **Language:** Canonical English. Russian edition: [ru/getting-started.md](../ru/getting-started.md).

# Getting started

> **Status:** Stable — local quick start and contributor QA workflow. Status tags: [doc-status.md](doc-status.md).

This guide has two parts:

1. **[Try ISPF (≈15 minutes)](#try-ispf-15-minutes)** — start ISPF on your machine, log in, and explore the built-in sensor, alarm, and operator screen.
2. **[Contribute: local dev & QA](#contribute-local-dev--qa)** — build and run fast local checks if you are modifying platform code.

New to ISPF concepts? Read **[How ISPF works](learn.md)** alongside this guide.

---

## Try ISPF (≈15 minutes)

**First success is a login screen**, not only a healthy API. Pick the path that matches your machine:

| Goal | Path | Open after start |
|------|------|------------------|
| Fastest first look (no sources) | [Option A](#option-a--portable-release-archive-fastest-no-database-install) or [Option B](#option-b--container-docker) | **http://localhost:8080** — sign in `admin` / `admin` |
| This repository is already checked out | [Option C](#option-c--run-from-source) — **API and Web Console** | **http://localhost:5173** — sign in `admin` / `admin` |
| REST / scripts only, no browser UI | Option C, [Advanced: API only](#advanced-api-only) | Health checks on port `8080` — no login page |

Agents that follow `AGENTS.md` use Option C with both terminals so the console is available.

### Option A — Portable release archive (fastest, no database install)

1. Install **JDK 25** and download **`ispf-*-portable.zip`** from [GitHub Releases](https://github.com/iot-solutions-ru/ispf/releases).
2. Extract the archive and start the server:

```bash
# Windows: double-click start.bat (or run in PowerShell/cmd)
start.bat

# Linux / macOS:
./start.sh
```

PostgreSQL is not required for local evaluation: the portable build uses an **embedded H2 file database** (`data/ispf-local.mv.db` created next to the JAR on first start).

The zip includes the **evaluation driver set** in `drivers/` (`virtual`, `modbus`, `mqtt`, `http`, `jdbc`, and the same small set `bootRun` uses). `start.bat` / `start.sh` set `ISPF_DRIVER_PACKS_DIR` to that folder when you have not set it yourself. That is enough to start `demo-sensor-01`. OPC UA, SNMP, and the rest of the 160+ protocol packs are not in the zip — build them with `./gradlew syncAllDriverPacks` or ship them in an air-gap bundle.

To reset the database, stop ISPF and delete `data/` only. Leave `drivers/` in place.

3. Open **http://localhost:8080** in your browser and sign in with **`admin` / `admin`**. Continue from [Step 2 — Sign in](#2-sign-in-local-profile) below (using port `8080`).

### Option B — Container (Docker)

```bash
docker pull ghcr.io/iot-solutions-ru/ispf-server:latest
docker run --rm -p 8080:8080 -v ispf-data:/opt/ispf/data ghcr.io/iot-solutions-ru/ispf-server:latest
```

Open **http://localhost:8080** and sign in with **`admin` / `admin`**.

The image defaults to the **`local`** Spring profile — `StartupSecurityGuard` is skipped there (even if `ISPF_SECURITY_STRICT_STARTUP=true`). Beyond a laptop lab, leave `local` for a `prod` deployment (PostgreSQL/OIDC) and change credentials — [deployment](deployment.md#shipped-entrypoints-and-profiles), [security](security.md).

The image contains the same evaluation driver set at `/opt/ispf/drivers`. The volume above is only the H2 data directory. Mounting `-v ispf-data:/opt/ispf/data` does not replace the drivers. A full protocol catalog is not in the image.

#### ISPF, Mosquitto, and a Modbus slave

`deploy/docker-compose.evaluate.yml` starts the published image, a broker, and the lab Modbus TCP fixture. It does not start PostgreSQL or Keycloak. The root `docker compose up` does the opposite and does not start ISPF. Do not run both if port 1883 is already taken.

```bash
docker compose -f deploy/docker-compose.evaluate.yml up -d
```

Open **http://127.0.0.1:8080** and sign in with **`admin` / `admin`** (the image uses the `local` profile). Published ports are bound to `127.0.0.1`.

From another container on this network the slave is `modbus:502`. An `mqtt` device uses `brokerUrl` `tcp://mosquitto:1883` (anonymous, no TLS, not a plant network). From the laptop those are `127.0.0.1:1883` and `127.0.0.1:5502`. Holding registers 0, 1, and 2 start as `0x1111`, `0x2222`, `0x3333`. A `modbus-tcp` device uses `host` `modbus`, `port` `502`, point `1:HOLDING:0`. Point format: [modbus-tcp](drivers.md#modbus-tcp-ispf-driver-modbus).

`demo-sensor-01` is still the `virtual` driver. This compose does not start its poll.

### Option C — Run from source

Use this when the repository is already on disk (contributors, agents, local checkout). You need **two processes**: the API and the Web Console. Stopping after `bootRun` alone leaves `http://localhost:8080/` as a 404 — that is not a complete first install.

#### Prerequisites

| Tool | Required version | Notes |
|------|------------------|-------|
| JDK | **17–27 to launch, 25 to run** | The wrapper uses `JAVA_HOME` when set, otherwise `PATH`. Compilation and the server use 25; the Foojay plugin in `settings.gradle.kts` downloads 25 when it is not already installed. The portable zip still needs 25 on the machine that runs `start.bat` / `start.sh`. |
| Gradle | Included (`./gradlew` / `gradlew.bat`) | No separate installation needed |
| Python | **3** | On `PATH`. `bootRun` regenerates the AI context pack before the server starts. |
| Node.js | **20+** | **Required** for the Web Console (`apps/web-console`) |
| Docker | Optional | Needed only if you want PostgreSQL, Keycloak, Mosquitto, and NATS locally |

#### 1. Start the API and the Web Console

**Terminal 1 — API** (`local` profile), from the repository root.

Windows PowerShell:

```
.\gradlew.bat :packages:ispf-server:bootRun --args="--spring.profiles.active=local"
```

Linux and macOS:

```bash
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"
```

The API is up when `http://localhost:8080/api/v1/info` includes `version` and `http://localhost:8080/actuator/health` is `{"status":"UP"}`. No token. The first start takes several minutes. A log that asks for PostgreSQL on port 5432 means the `local` profile argument was missing.

**Terminal 2 — Web Console** (required for the login page). Node.js 20+. The Vite app proxies `/api` and `/ws` to port 8080.

```
cd apps/web-console
npm install
npm run dev
```

**Open http://localhost:5173** and continue at [Step 2 — Sign in](#2-sign-in-local-profile) with **`admin` / `admin`**.

Do not expect a login form at `http://localhost:8080/` in this mode — that port is API-only until you use the portable zip or the container. Those serve the same console on port 8080.

| URL | What opens |
|-----|------------|
| `http://localhost:5173` | Admin Console (Vite — Option C) |
| `http://localhost:5173?mode=operator` | Operator HMI (Vite — Option C) |
| `http://localhost:8080` | Admin Console (portable JAR or Docker only) |
| `http://localhost:8080?mode=operator` | Operator HMI (portable JAR / Docker only) |
| `http://localhost:8080/api/v1/info` | Server version and enabled capabilities |
| `http://localhost:8080/actuator/health` | Health status |

By default, `bootRun` synchronizes a small set of core development drivers (`syncDevDriverPacks`, including `virtual`, `mqtt`, `modbus`, `http`, `jdbc`). To build all 160+ protocol driver packs, pass `-Dispf.driver.packs=all`.

#### Advanced: API only

Skip Terminal 2 when you only need REST or scripts against the running server. Health checks on `/api/v1/info` and `/actuator/health` are enough. There is no browser login page in that setup — use Option A/B or both terminals above when you want the console.

### 2. Sign in (`local` profile)

On an empty database, ISPF creates three local accounts for testing:
- **`admin` / `admin`** — full access to the object tree, builders, and settings
- **`developer` / `developer`** — solution authoring access
- **`operator` / `operator`** — opens Operator HMI directly (read-only screens, commands, and work queue)

To call the REST API from a terminal, obtain a Bearer token first:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/objects
```

> Note: the legacy `X-ISPF-Role` debug header is disabled by default (`ispf.security.local-role-header-enabled=false`). Always authenticate via `/api/v1/auth/login`. Details: [Security](security.md).

### 3. Install a demo from the marketplace

The `local` profile has the catalog turned on. In the console open **System**, then the **Solutions** tab. The page title is "Solution catalog". The **Marketplace** block on that page lists "IoT Solutions Marketplace" (`https://marketplace.ispf.ai`, `ISPF_MARKETPLACE_DEFAULT_URL`). A free card installs with one button: the server downloads the bundle and deploys it. An already installed card says so and offers reinstall. The process that is running ISPF must be able to reach that host.

`admin` and `developer` can install. `operator` receives 403.

The same call from the shell, with the token from the step above:

```bash
curl -sS -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/solutions/marketplaces/default-publisher/catalog?pricing=free"

curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/solutions/marketplaces/default-publisher/listings/<slug>/install"
```

A listing that names a companion screen also installs that screen at `/apps/<appId>/`. If only the screen fails, the application is still installed and the install result says so. Direct `POST /api/v1/applications/.../deploy` is a different path and still requires a signed license when that check is on. Details: [Marketplace](marketplace.md).

### 4. Walk through the built-in demo in the browser

![Admin Explorer — object tree after first login](../assets/ispf-object-tree.png)

1. In the left **Explorer** tree, expand `root` → `platform`.
2. Expand `devices` → click **`demo-sensor-01`**. In the right **Inspector** panel you will see its variables: `temperature`, `threshold`, `alarmActive`, and `temperaturePercent`.
3. Expand `dashboards` → double-click **`demo-sensor`** to open it in **Dashboard Builder**.
4. Expand `alert-rules` → click **`temperature-threshold-exceeded`** to inspect the CEL threshold formula watching `demo-sensor-01`.
5. Expand `workflows` → double-click **`demo-alarm-handler`** to view the sample **BPMN** alarm-handling workflow.
6. Switch to **Operator mode** (`http://localhost:8080?mode=operator` or `http://localhost:5173?mode=operator`) to see the clean operator view without configuration menus.

You can switch the interface language between **Russian** and **English** in the top header bar.

### 5. Start the simulated sensor driver

Start polling on `demo-sensor-01` from the device Inspector, or from the shell.

Windows PowerShell. The script checks mode `local` and driver `virtual`, starts the demo device, and does not print the token:

```
.\docs\http\local-session.ps1
```

Linux and macOS (`$TOKEN` is the login from the step above; `jq` reads the `token` field):

```bash
curl -X POST "http://localhost:8080/api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01" \
  -H "Authorization: Bearer $TOKEN"
```

`demo-sensor-01` generates a sine-wave temperature signal. Whenever the temperature crosses `threshold`, `alarmActive` turns `true` and the alert rule fires an event into the journal.

### Where to go next

- **[Troubleshooting](troubleshooting.md)** — the console will not open, sign-in rejects `admin` / `admin`, or `demo-sensor-01` stays still.
- **[How ISPF works](learn.md)** — follow **Path B** to build your first own station/device project.
- **[Solution developer guide](solution-developer-guide.md)** — package SQL tables, functions, and dashboards into a repeatable `bundle.json` (`ispf pack` / `ispf validate` / `ispf deploy`). `ispf login` and `ispf doctor` are in [tools/ispf-cli/README.md](../../tools/ispf-cli/README.md).
- **[Common design mistakes](anti-patterns.md)** — seven practical rules to keep screens fast and configurations clean.
- **[Custom React UI](external-ide.md)** — build a custom React SPA (`ui-pack`) in Cursor or VS Code.

---

## Optional: full local infrastructure stack (`dev` profile)

If you want PostgreSQL/TimescaleDB, MQTT (Mosquitto), NATS JetStream, and Keycloak OAuth2 locally:

```bash
docker compose up -d
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=dev"
```

| Service | Port | Role |
|---------|------|------|
| PostgreSQL (TimescaleDB) | `5432` | Primary relational & hot time-series database (`ispf`) |
| Redis | `6379` | Cache |
| NATS JetStream | `4222`, `8222` | Internal/workflow messaging |
| Eclipse Mosquitto | `1883` | Local MQTT broker for device testing |
| Keycloak | `8180` | OAuth2 / OIDC identity provider (`dev` profile) |

### Summary of Spring profiles

| Profile | Database | Authentication | MQTT / NATS |
|---------|----------|----------------|-------------|
| *(default)* | PostgreSQL | Keycloak OAuth2 JWT | Disabled unless configured via env vars |
| `local` | Embedded H2 file (`./data/ispf-local`) | Built-in Bearer login (`POST /api/v1/auth/login`) | Disabled |
| `dev` | PostgreSQL (`localhost:5432`) | Keycloak JWT (`localhost:8180`) | Enabled |
| `test` | In-memory H2 | Disabled for unit/slice tests | Disabled |

Full server configuration reference: [Deployment](deployment.md) and [Security](security.md).

### How driver packs are loaded

Protocol drivers are packaged as separate plugins outside `ispf-server.jar` and loaded at startup from `ISPF_DRIVER_PACKS_DIR` (default `./data/drivers`).

The portable zip and the GHCR image set that variable to a `drivers/` directory shipped beside the server (`./drivers` or `/opt/ispf/drivers`) and fill it with the evaluation set (`syncDevDriverPacks`). A source checkout does not: `bootRun` builds the same set into `build/driver-packs` and points the process there.

```bash
./gradlew syncDevDriverPacks          # fast default subset for everyday development
./gradlew syncAllDriverPacks          # builds all 160+ driver packs into build/driver-packs
```

Details: [Device drivers](drivers.md) and [Driver packs](licensed-driver-packs.md).

---

## Contribute: local dev & QA

> **Important:** Do **not** run `./gradlew test` or `syncAllDriverPacks` for routine changes unless you are modifying drivers. Building all 160+ driver packs and running 1,000+ tests from cold state can take **hours** ([issue #65](https://github.com/iot-solutions-ru/ispf/issues/65)). Use the targeted commands below instead.

### Fast pre-push check (matches CI `pr-fast`)

```bash
# Linux / macOS
./tools/ci/pr-fast.sh

# Windows PowerShell
.\tools\ci\pr-fast.ps1
```

Or run the backend and frontend checks directly:

```bash
# Backend core checks (skips heavy load and multi-node federation suites)
./gradlew testPrFast \
  -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev

# Web Console lint, tests, i18n check, and production build
cd apps/web-console && npm test && npm run i18n:check && npm run build
```

### Running a single test class (< 2 minutes)

```bash
./gradlew :packages:ispf-core:test --tests com.ispf.core.model.DataRecordTest

./gradlew :packages:ispf-server:test --tests com.ispf.server.alert.AlertRuleLatchTest \
  -Dispf.test.skipLoad=true
```

### Reference table of test suites

| What you changed | Command to run |
|------------------|----------------|
| Core / server logic | `./gradlew testPrFast -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev` |
| Dev driver packs | `./gradlew testDevDriverPacks syncDevDriverPacks` |
| All protocol driver packs | `./gradlew syncAllDriverPacks` |
| Load & federation suites (nightly equivalent) | `./tools/ci/nightly.sh` |
| Full `ispf-server` suite including load tests | `./gradlew :packages:ispf-server:test` |

See [Testing](testing.md) and [gradle.properties.example](../../gradle.properties.example) for parallel worker tuning.
