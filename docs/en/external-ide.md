> **Language:** Canonical English. Russian edition: [ru/external-ide.md](../ru/external-ide.md).

# Solution development in an external AI IDE

> **Status:** Stable — Hosted UI packs (ADR-0054) + MCP (ADR-0006). Hub: [doc-status.md](doc-status.md).

**Platform value:** industry solutions are built **outside** `ispf-server` and `apps/web-console`. Partners use any AI IDE (Cursor, VS Code, …) with unlimited React components, then ship a **bundle** (logic) plus a **ui-pack** (SPA). The platform hosts both: BFF on the object tree, static UI at `/apps/<appId>/`.

This is the path used by Oil Control, FarmTwin, StoreTwin, and IT-infra monitoring — not a workaround.

Hub links: [application-principles](application-principles.md) (P2, P7, P9), [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md), [0054-hosted-ui-packs](decisions/0054-hosted-ui-packs.md), [0006-mcp-agent-tool-adapter](decisions/0006-mcp-agent-tool-adapter.md), [solution-developer-guide](solution-developer-guide.md), [marketplace](marketplace.md).

**Agent:** `search_context(query=hosted ui-pack Cursor, topic=external-ide)`.

---

## Why this is a platform capability

ISPF is middleware. The console widget kit (~42 types, 84×8 grid) is a **generic HMI** for lab, SNMP, and SCADA fallback. Product UIs need arbitrary React: maps, 3D, design systems, dense tables, branded shells.

| Layer | What the partner owns | What the platform owns |
|-------|------------------------|-------------------------|
| Pixels | Vite/React SPA, any components | Hosting at `/apps/<appId>/` (ui-pack) |
| Data / rules | Script functions, SQL in app schema, CEL, alerts, BPMN | Engines, BFF gateway, tree, historian, WS |
| Delivery | `bundle.json` + `ui-pack` zip | `validate → dry-run → deploy` / marketplace install |

**Forbidden in `main`:** industry React in `apps/web-console`, industry Java in `ispf-server`, hardcoded BFF routes, platform Flyway for app tables.

Two artifacts, one solution:

```text
React SPA (external IDE)          ISPF
  Vite base /apps/<appId>/          HostedUiPackFilter
  POST /api/v1/auth/login    ──►    session
  POST /api/v1/bff/invoke    ──►    hub script functions
  WS /ws/objects             ──►    live variables (optional)
```

Reference listings: `examples/oil-control` + `oil-control-ui`, `examples/farmtwin` + `farmtwin-ui`, `examples/storetwin` + `storetwin-ui`, `examples/marketplace-ui-pack-demo`.

---

## Choose UI path (do not mix)

| | **Hosted SPA (this page)** | **Platform widget kit** |
|---|----------------------------|-------------------------|
| When | Product operator UI, unlimited React | Lab, SNMP, SCADA fallback, AI Studio HMI |
| AUTHOR | External IDE on a **separate** SPA repo | Admin Console / in-server agent |
| SHIP | ui-pack zip + application bundle | `dashboards[]` + `operatorUi` |
| Operator | **Open app UI** → `/apps/<appId>/` | `?mode=operator&app=` dashboards |
| Source | Never inside `apps/web-console` | `DASHBOARD.layout` widgets |

Keep a thin Operator dashboard as fallback when the pack is missing (FarmTwin pattern). Do not duplicate business logic in widgets.

P7 layers stay the same: SPA authoring is **AUTHOR**; bundle + ui-pack is **SHIP**. See [application-principles P7](application-principles.md).

---

## 1. Connect the IDE to ISPF (MCP)

External IDEs must call **ISPF** tools, not a third-party SCADA MCP. Profile `mcp` exposes the same ACL-aware registry as AI Studio ([ai-development](ai-development.md#mcp-adapter-0006)).

1. Enable MCP on the server (`spring.profiles.include=mcp`, `ispf.mcp.enabled=true`).
2. Login: `POST /api/v1/auth/login` → Bearer token (admin for mutate).
3. Cursor / MCP host:

```json
{
  "mcpServers": {
    "ispf": {
      "url": "https://<ispf-host>/api/v1/ai/mcp",
      "headers": { "Authorization": "Bearer <admin-token>" }
    }
  }
}
```

Local: `http://localhost:8080/api/v1/ai/mcp`.

4. Confirm tools include `list_objects`, `validate_bundle`, `import_package`, `get_example_bundle`. ContextPack slices: `contextpack://bundle-manifest`, `contextpack://example-summaries`.

Use MCP to mutate the **tree and bundle**. Use the IDE filesystem for the **SPA**. Do not ask the agent to patch `apps/web-console` for industry screens.

---

## 2. Repository layout

Keep SPA sources out of the platform JAR:

```text
my-app-web/                      ← Cursor workspace for UI
  src/
  vite.config.ts                 ← base: '/apps/my-app/'
  openapi.yaml                   ← SPA ↔ BFF contract
  package.json                   ← pack:ui

ispf/  or sibling checkout       ← bundle + pack zip only
  examples/my-app/bundle.json
  examples/my-app-ui/ui-pack.json
  examples/my-app-ui/my-app-ui-x.y.z.zip
```

SPA git history does not belong in `ispf-server`. Marketplace copies under `examples/marketplace-catalog/` are listings + zip, not Vite source.

---

## 3. HTTP contract (SPA ↔ platform)

### Auth

```http
POST /api/v1/auth/login
{ "username": "admin", "password": "admin" }
```

Store `token`; send `Authorization: Bearer`. On 401, return to login. Same-origin when hosted; Vite proxy in dev.

### BFF — the only business API

Do **not** add `/api/v1/<app>/…` controllers. Every screen calls:

```http
POST /api/v1/bff/invoke
Authorization: Bearer <token>
Content-Type: application/json

{
  "objectPath": "root.platform.singleton-blueprints.my-app-hub",
  "functionName": "my_listItems",
  "input": {
    "schema": { "name": "in", "fields": [] },
    "rows": [{}]
  },
  "wireProfile": "ispf-operator-v1"
}
```

Success: `error_code === "OK"`. List functions often unwrap to `result` as a row array. Full wire: [applications](applications.md#bff-req-pf-06).

Pin `objectPath` and `functionName` in an OpenAPI file (see `examples/oil-control/api/openapi.yaml`). Prefix functions (`oc_`, `ft_`, `st_`, `my_`).

**Hub type:** SINGLETON orchestrator (prefer `root.platform.singleton-blueprints.{app}-hub`) or INSTANCE twin — **never** `ObjectType.DEVICE`. Existing dogfood apps may use path `root.platform.devices.{app}.hub`; the path is an implementation choice, the type must not be DEVICE.

### Live telemetry (optional)

`WS /ws/objects` with `Sec-WebSocket-Protocol: ispf-bearer, <token>`. Subscribe to device paths; do not poll protocols from the browser. See [messaging](messaging.md).

### Vite

```ts
export default defineConfig({
  base: '/apps/my-app/',
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
```

React Router `basename` must match `base`. Dev URL: `http://localhost:5173/apps/my-app/`. Optional `VITE_DATA_SOURCE=mock` for offline UI work (StoreTwin).

Local bridge before the pack is installed: bundle `operatorUi.externalSpaUrl` = `http://127.0.0.1:5173` (see `examples/m11-monitor-ui`).

---

## 4. Map screens before pixels

For each operator screen:

| SPA route | Label | BFF functions |
|-----------|-------|----------------|
| `/` | Overview | `my_getKpi`, `my_listItems` |
| `/alarms` | Alarms | `my_listAlarms`, `my_ackAlarm` |

Persist the same map in the bundle (`operatorUi.spaNav`). Routes in React **must** match `to`.

```json
"operatorUi": {
  "title": "My App",
  "spaNav": [
    { "to": "/", "label": "Overview", "bff": ["my_getKpi", "my_listItems"] }
  ],
  "externalSpaUrl": "/apps/my-app/",
  "uiPack": { "packId": "my-app-ui", "version": "1.0.0", "entry": "index.html" }
}
```

`spaNav` is metadata for Open app UI and agents — it does not host the SPA by itself.

A new screen = new route + `spaNav` row + hub function(s). Not a new server URL.

---

## 5. Bundle (logic SHIP)

Canonical lifecycle: [solution-developer-guide](solution-developer-guide.md).

1. Register `appId`, isolated `schemaName`, `tablePrefix`.
2. `migrations[]` — app SQL only (not platform Flyway).
3. Hub object + `functions[]` (script steps `selectMany` / `insert` / `return`).
4. Optional DEVICE children, alerts, workflows, fallback dashboards.
5. `operatorUi` as above.
6. Gates in **one run:** `validate_bundle` → `dry_run_deploy` → `import_package` or `POST /api/v1/applications/{appId}/deploy`.

Prefer `get_example_bundle` when a reference covers ≥70% of the spec.

---

## 6. UI pack (pixels SHIP)

`ui-pack.json` at zip **root** with `index.html` and `assets/` (not wrapped in `dist/`):

```json
{
  "appId": "my-app",
  "packId": "my-app-ui",
  "version": "1.0.0",
  "entry": "index.html",
  "basePath": "/apps/my-app/",
  "title": "My App",
  "minIspfVersion": "0.9.30"
}
```

`appId` is the install directory (`ISPF_UI_PACKS_DIR/<appId>/`). Listing slug may differ (`oil-control-ui` vs appId `oil-control`).

Build: `vite build` then zip the dist root. Dogfood scripts: `npm run pack:ui` in Oil Control / StoreTwin SPA repos.

Install:

```http
POST /api/v1/marketplace/ui-packs/{id}/install
```

Or drop-in unpack to `ISPF_UI_PACKS_DIR/my-app/`. Application listing field `uiPackSlug` installs BFF + SPA together ([marketplace](marketplace.md)).

Smoke: `GET /apps/my-app/` must **not** return the Admin Console title. Split nginx **must** `proxy_pass` `location ^~ /apps/` to the JVM — [ADR-0054](decisions/0054-hosted-ui-packs.md).

---

## 7. IDE prompt (copy)

Paste into the SPA workspace so the model does not treat ISPF as “a React monolith to extend”:

```text
This is an ISPF hosted UI pack (ADR-0054), not the admin console.

Stack: Vite + React, base and Router basename '/apps/<appId>/'.
Data: only POST /api/v1/auth/login and POST /api/v1/bff/invoke on the hub.
Do not add Java to ispf-server or pages to apps/web-console.
A new screen = route + spaNav entry + BFF function with the app prefix.
Contract: openapi.yaml (enum functionName). Optional VITE_DATA_SOURCE=mock.
Build: pack:ui → zip with ui-pack.json at zip root.
Hub logic object is SINGLETON or INSTANCE, never DEVICE.
```

For the platform checkout, a second agent session with ISPF MCP owns migrations, functions, and `operatorUi`.

---

## 8. Acceptance

- [ ] Login on `/apps/<appId>/` succeeds; BFF `error_code=OK` on every `spaNav` route
- [ ] Writes (ack, create) persist in the app schema and refresh the UI
- [ ] Operator `/?mode=operator&app=<appId>` → **Open app UI** opens the pack (not the console shell)
- [ ] `curl /apps/<appId>/` is the SPA, not `<title>ISPF Admin Console</title>`
- [ ] No industry files under `apps/web-console` or `packages/ispf-server`

---

## Anti-patterns

| Anti-pattern | Correct |
|--------------|---------|
| Industry pages in `apps/web-console` | Separate Vite app + ui-pack |
| `@RestController` / Flyway for the domain | `functions[]` + `migrations[]` + `/bff/invoke` |
| `base: '/'` in production | `base: '/apps/<appId>/'` |
| Business rules only in React | SQL/CEL/alerts on the platform; SPA renders |
| Logic hub typed as DEVICE | SINGLETON or INSTANCE |
| AggreGate or other MCP instead of `/api/v1/ai/mcp` | ISPF MCP with admin Bearer |
| Ten `add_dashboard_widget` calls as the product UI | Widget kit is fallback; SPA is the product |

---

## Related documents

| Document | Purpose |
|----------|---------|
| [0054-hosted-ui-packs](decisions/0054-hosted-ui-packs.md) | Serve `/apps/<appId>/`, nginx, PWA denylist |
| [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md) | No industry Java/React in `main` |
| [0006-mcp-agent-tool-adapter](decisions/0006-mcp-agent-tool-adapter.md) | External IDE tool bridge |
| [ai-development](ai-development.md) | MCP enablement, Cursor JSON example |
| [applications](applications.md) | Bundle, BFF wire, migrations |
| [marketplace](marketplace.md) | `artifactKind: ui-pack`, `uiPackSlug` |
| [operator-apps](operator-apps.md) | Operator shell; Open app UI |
| [widgets](widgets.md) / [dashboards](dashboards.md) | Fallback HMI kit |
| [agent-knowledge](agent-knowledge.md) | Approach **I** (hosted UI pack) |
