# Agent guidance (ISPF)

## Run this checkout

Start both processes below with the `local` profile. The run is finished when `http://localhost:5173` shows the sign-in page. `bootRun` alone serves the API only, and `http://localhost:8080/` returns 404. The first start compiles the server and the small driver set (including `virtual`, `modbus`, `mqtt`, `http`, `jdbc`) and takes several minutes. `./gradlew test` builds the full driver matrix; it is not the way to see the console.

A source run has two processes. The Java API listens on 8080. The Vite dev server serves the console on 5173, applies console edits without a rebuild, and proxies `/api` and `/ws` to 8080. The portable zip, the container, and a release JAR built with `-PembedWebConsole=true` serve the console and the API from one process on 8080; `bootRun` does not. Describe the two ports to the user as the normal layout of a source run. Details: `docs/en/getting-started.md` § Why two processes (and two ports)?.

The wrapper uses `JAVA_HOME` when that variable is set, otherwise a JDK on `PATH`. That JDK must be 17 through 27. The server compiles and runs on JDK 25. The Foojay plugin in `settings.gradle.kts` downloads 25 when JDK 25 is not already installed. Python 3 must be on `PATH`: `bootRun` regenerates the AI context pack before the server starts. Node.js 20+ is required for the Web Console. The portable zip is a different path: that JAR needs JDK 25 on the machine that runs `start.bat` / `start.sh`, and it serves the console on port 8080.

### Terminal 1 — API

Windows PowerShell, from the repository root:

```
.\gradlew.bat :packages:ispf-server:bootRun --args="--spring.profiles.active=local"
```

Linux and macOS:

```
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"
```

The API is up when these respond with no token:

- `http://localhost:8080/api/v1/info` includes `version`
- `http://localhost:8080/actuator/health` is `{"status":"UP"}`

A log that asks for PostgreSQL on port 5432 means the `local` profile argument was missing.

### Terminal 2 — Web Console

```
cd apps/web-console
npm install
npm run dev
```

Open **http://localhost:5173** and sign in as `admin` / `admin`. Do not tell the user to open `http://localhost:8080/` for the console in this mode.

### API only

Terminal 1 alone is enough when the task is REST calls or scripts. The health checks above confirm the server is ready. Tell the user that this mode has no sign-in page: the console needs Terminal 2 (port 5173), the portable zip, or the container (port 8080). Human install paths: `docs/en/getting-started.md`.

When the API answers, `docs/http/local-session.ps1` checks auth mode `local`, checks that driver `virtual` is registered, and starts `root.platform.devices.demo-sensor-01`. It does not print the token. Failures: `docs/en/troubleshooting.md`. Humans learning the product continue at `docs/en/learn.md`.

Demo solutions are not built by hand. The `local` profile has the catalog on. In the console open **System**, tab **Solutions**. The page title is "Solution catalog". The **Marketplace** block on that page is the catalog (default host `https://marketplace.ispf.ai`, label "IoT Solutions Marketplace"). The ISPF process must reach it. With the `admin` token:

```
GET /api/v1/solutions/marketplaces/default-publisher/catalog?pricing=free
POST /api/v1/solutions/marketplaces/default-publisher/listings/<slug>/install
```

`operator` is 403 on that install. A companion screen lands at `/apps/<appId>/`. Direct `POST /api/v1/applications/.../deploy` still requires a signed license when that check is on. Details: `docs/en/marketplace.md`.

## Logic objects (hard rule — canon)

The object that holds application / orchestration / twin logic must **not** be `ObjectType.DEVICE`. DEVICE = drivers and telemetry only.

| Role | Blueprint kind | Notes |
|------|----------------|-------|
| Unique orchestrator (1 per solution/cluster) | **SINGLETON** | Prefer `root.platform.singleton-blueprints.{name}` + `ensure_singleton_instance` |
| Digital twin with per-twin logic (N) | **INSTANCE** | `instantiate_instance_type`; each instance holds twin logic |
| Telemetry / I/O | — | **DEVICE** children under hub or plant folder |

Cluster layout (hub parent + DEVICE children under `devices.*`) is an **implementation choice**; the hub’s **type** must not be DEVICE.

**Enforcement:** authoring default **WARNING**; ship / `--strict` / marketplace **ERROR** — [0060](docs/en/decisions/0060-solution-authoring-constraints.md). Details: `docs/en/application-principles.md` § Logic objects vs DEVICE.

## Field pain → ISPF constraints

Do **not** recreate known field footguns. Canonical list (validators + engine limits): `docs/en/anti-patterns.md`. Keep MQTT `eventJournalEnabled` off unless audit + retention (ADR-0061).

## Stability before features

Harden what shipped; do not widen the surface for scorecard claims. Non-claims and parked work: `docs/en/known-limitations.md`, `docs/en/parked-backlog.md`.

Historian: local ClickHouse disk spool (`ispf.variable-history.spool`) is **shipped** (off by default) — document/operate, do not re-implement. Still deferred until explicitly asked: cross-node historian buffer copy; ISPF-managed ClickHouse cluster. Leave sole-store + JDBC dual-write catch-up alone.

## Teaching users

Point humans to **`docs/en/learn.md`** (transparent entry): Path A first hour → Path B first solution → role tracks → certification. Do not dump the full doc catalog on newcomers.

## Hosted application UI (external IDE)

Product operator UI is a **React SPA** in a separate repo (Cursor/VS Code), shipped as a ui-pack and hosted at `/apps/<appId>/`. Do not add industry pages to `apps/web-console` or domain Java to `ispf-server`. Data: `POST /api/v1/bff/invoke` on a SINGLETON/INSTANCE hub. How-to: `docs/en/external-ide.md`.
