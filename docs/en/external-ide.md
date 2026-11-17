> **Language:** Canonical English. Russian edition: [ru/external-ide.md](../ru/external-ide.md).

# Building a custom React operator UI (external IDE & UI packs)

> **Status:** Stable — Hosted UI packs ([ADR-0054](decisions/0054-hosted-ui-packs.md)) and MCP IDE integration ([ADR-0006](decisions/0006-mcp-agent-tool-adapter.md)). Status tags: [doc-status.md](doc-status.md).

ISPF's built-in Dashboard Builder and SCADA Mimic Editor cover standard operator displays without writing frontend code. When a product requires a completely custom web interface — interactive maps, 3D equipment views, custom design systems, or dense MES scheduling boards — you develop a standalone **React + Vite single-page application (SPA)** in your own IDE (Cursor, VS Code, WebStorm) and install it on ISPF as a **`ui-pack`**.

This is the standard architecture behind ISPF's reference applications (`Oil Control`, `FarmTwin`, `StoreTwin`, `WarehouseTwin`, and `IT Infrastructure Monitoring`).

**Demo:** [`examples/oil-control-ui`](../../examples/oil-control-ui/) with [`examples/oil-control`](../../examples/oil-control/); minimal pack shape: [`examples/marketplace-ui-pack-demo`](../../examples/marketplace-ui-pack-demo/).

Related guides: [Solution developer guide](solution-developer-guide.md) · [Application architecture rules](application-principles.md) · [Applications API](applications.md) · [Marketplace](marketplace.md).

---

## How responsibilities are split

Your custom UI lives in its own repository — **never** inside `apps/web-console` or `packages/ispf-server`:

| Part of the solution | Built and owned in your project | Provided by ISPF |
|----------------------|---------------------------------|------------------|
| **Frontend screens** | Standalone Vite/React SPA (`ui-pack` zip) | Static hosting under `/apps/<appId>/` (`HostedUiPackFilter`) |
| **Business logic & data** | Hub functions (`script` / SQL), CEL rules, alerts, BPMN (`bundle.json`) | `POST /api/v1/bff/invoke`, isolated SQL schema, historian, WebSocket |
| **Deployment** | `bundle.json` + `<app>-ui.zip` | Bundle validation, atomic deploy, and Marketplace installation |

```text
Custom React SPA (/apps/<appId>/)       ISPF Server
  POST /api/v1/auth/login        ──►    Authentication & session token
  POST /api/v1/bff/invoke        ──►    Functions on SINGLETON / INSTANCE hub
  WS /ws/objects                 ──►    Live variable & alarm updates (optional)
```

### When to choose a custom `ui-pack` vs built-in dashboards

| Criterion | Custom React SPA (`ui-pack`) | Built-in Dashboards & SCADA mimics |
|-----------|------------------------------|------------------------------------|
| **Best for** | Branded product UI, maps, 3D, complex forms and workflows | P&ID schematics, trend screens, SNMP/lab monitoring, quick HMI |
| **Where you build it** | Separate frontend repository in your IDE | Directly in the browser (Dashboard / Mimic Builder) |
| **How operators open it** | `/apps/<appId>/` (or **Open app UI** in Operator mode) | `?mode=operator&app=<appId>` |

You can ship both: a custom `ui-pack` as the primary operator interface and a lightweight built-in dashboard as a fallback if the UI pack is not installed yet.

---

## 1. Repository structure

Keep frontend source code separate from your ISPF configuration folder:

```text
my-app-web/                         # Frontend repository (React + Vite)
  src/
  vite.config.ts                    # base: '/apps/my-app/'
  openapi.yaml                      # Contract of hub functions called by the UI
  package.json                      # "pack:ui" script producing zip archive

my-app-solution/                    # ISPF solution folder (packed via ispf-cli)
  solution.json
  sql/
  functions/
  bundle.json                       # Output of `ispf pack`
  ui-pack/
    ui-pack.json
    my-app-ui-1.0.0.zip
```

---

## 2. Optional: connect Cursor or VS Code to ISPF via MCP

If you use an AI-assisted IDE (such as Cursor or VS Code), connect it to your local or dev ISPF server's **MCP endpoint** so it can inspect live tree paths, schemas, and validate bundles directly ([ai-development](ai-development.md#mcp-adapter-0006)):

1. Enable the `mcp` profile on ISPF (`spring.profiles.include=mcp` or `ispf.mcp.enabled=true`).
2. Obtain an admin Bearer token via `POST /api/v1/auth/login`.
3. Add the server to your IDE's MCP configuration:

```json
{
  "mcpServers": {
    "ispf": {
      "url": "http://localhost:8080/api/v1/ai/mcp",
      "headers": {
        "Authorization": "Bearer <admin-token>"
      }
    }
  }
}
```

---

## 3. HTTP & WebSocket contract between React and ISPF

### Authentication

First-party UI packs sign in via:

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "username": "operator", "password": "operator" }
```

Store the returned `token` and pass `Authorization: Bearer <token>` on subsequent requests (returning to the login screen on HTTP `401`).
*(Note: third-party partner packs configured with restricted `uiPackTrust` (`partner` / `signedPartner`) do not call `/api/v1/auth/login` directly and instead receive a BFF-scoped token carrying `uiPackAppId` — see [ADR-0073](decisions/0073-hosted-ui-pack-trust.md).)*

### Calling backend functions (`POST /api/v1/bff/invoke`)

Every screen reads data and submits commands through `/api/v1/bff/invoke` targeting your application's **`SINGLETON`** or **`INSTANCE`** hub object (never a `DEVICE` node):

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

A successful call returns `error_code === "OK"` with payload data in `result`. Full wire specification: [Applications API § BFF](applications.md#bff-req-pf-06). Prefix your function names (for example, `my_getKpi`, `my_ackAlarm`) so they are easy to trace in logs.

### Subscribing to live tag updates (optional)

For real-time values without polling, open `WS /ws/objects` with header `Sec-WebSocket-Protocol: ispf-bearer, <token>` and subscribe to the relevant object paths. See [Messaging](messaging.md).

### Vite dev server configuration

Set `base` (and your React Router `basename`) to `'/apps/<appId>/'` and proxy `/api` and `/ws` to ISPF during local development:

```ts
import { defineConfig } from 'vite';

export default defineConfig({
  base: '/apps/my-app/',
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
```

During local frontend work, open `http://localhost:5173/apps/my-app/`.

---

## 4. Declaring routes in `bundle.json` (`operatorUi`)

List your SPA's routes and the hub functions they use inside `operatorUi` in `bundle.json`:

```json
"operatorUi": {
  "title": "My Application",
  "spaNav": [
    { "to": "/", "label": "Overview", "bff": ["my_getKpi", "my_listItems"] },
    { "to": "/alarms", "label": "Alarms", "bff": ["my_listAlarms", "my_ackAlarm"] }
  ],
  "externalSpaUrl": "/apps/my-app/",
  "uiPack": {
    "packId": "my-app-ui",
    "version": "1.0.0",
    "entry": "index.html"
  }
}
```

Adding a new screen to your product simply means adding a React route, a `spaNav` entry, and the corresponding hub function(s) — no changes to ISPF server endpoints.

---

## 5. Packaging and installing the `ui-pack`

Place `ui-pack.json` at the **root** of the zip archive alongside `index.html` and `assets/` (zip the contents of `dist/`, not the `dist/` folder itself):

```json
{
  "appId": "my-app",
  "packId": "my-app-ui",
  "version": "1.0.0",
  "entry": "index.html",
  "basePath": "/apps/my-app/",
  "title": "My Application",
  "minIspfVersion": "0.9.30"
}
```

Install the UI pack either via the Marketplace API:

```http
POST /api/v1/marketplace/ui-packs/{id}/install
```

or by unpacking it into `ISPF_UI_PACKS_DIR/my-app/` on the server.

> **Reverse proxy note (split nginx):** When nginx sits in front of `ispf-server`, ensure `location ^~ /apps/` is proxied (`proxy_pass`) to the ISPF backend just like `/api/` — otherwise nginx's SPA fallback will serve the Admin Console HTML instead of your UI pack ([ADR-0054](decisions/0054-hosted-ui-packs.md)).

---

## 6. Verification checklist

- [ ] Opening `/apps/<appId>/` serves your custom SPA (not `<title>ISPF Admin Console</title>`)
- [ ] Every route in `spaNav` loads data cleanly with `error_code === "OK"` from `POST /api/v1/bff/invoke`
- [ ] User actions (creating records, acknowledging alarms) persist in the isolated SQL schema and update the screen
- [ ] Clicking **Open app UI** in `/?mode=operator&app=<appId>` opens `/apps/<appId>/`
- [ ] All backend logic sits on a `SINGLETON` or `INSTANCE` hub (not `DEVICE`), and zero project files were added to `apps/web-console` or `packages/ispf-server`
