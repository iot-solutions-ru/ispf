> **Language:** Canonical English. Russian edition: [ru/web-console.md](../ru/web-console.md).

# Web Console

React 19 + Vite 6 + TanStack Query. Catalog: `apps/web-console/`.

## Operating modes

### Administrator (default)

URL: `http://localhost:5173`

| Area | Features |
|------|----------|
| Object tree | Search, expand, CRUD, drag-and-drop sibling order |
| Explorer | List of child objects |
| Inspector | Properties, variables, events; alert rules and correlators |

### Object-type editors

Double-click in the tree:

| ObjectType | Component |
|------------|-----------|
| `DASHBOARD` | `DashboardBuilder` |
| `WORKFLOW` | `WorkflowBuilder` |
| `ALERT` | `AlertRuleInspector` |
| `CORRELATOR` | `CorrelatorInspector` |
| Other | `ObjectPropertiesEditor` |

### Operator HMI

URL: `http://localhost:5173?mode=operator`

- Full-screen dashboard (read-only)
- Sidebar: work queue + event journal
- No layout or object editing

### Operator app (dashboards)

URL: `http://localhost:5173?mode=operator&app=platform`

Generic operator shell with no industry-specific code in `main`:

- Operator UI: `GET /api/v1/operator-apps/{appId}/ui` (built-in apps, configured in `root.platform.operator-apps`), then `GET /api/v1/applications/{appId}/operator-ui` (bundle), legacy fallback `public/operator-apps/<appId>.ui.json`
- Dashboard tab navigation; widgets are the same as in Dashboard Builder (`DashboardBuilder` + `operatorMode`)
- BFF (`POST /api/v1/bff/invoke`) — only when app widgets/functions call the backend (not a separate manifest shell)

### Legacy: operator manifest

`?mode=operator&app=demo` + `operatorManifest` — deprecated. Use `operatorUi` and `DASHBOARD` objects.

## Navigation

No client-side router. State lives in `App.tsx`:

- `mode`: `admin` | `operator`
- `app`: operator application id (optional)
- `dashboard`: `DASHBOARD` object path (optional)
- `screen`: screen id in legacy manifest (deprecated)
- `selectedPath`, `editorPath`

## Roles

Header selector → `X-ISPF-Role: admin|operator` (`src/auth/role.ts`).

| Role | UI |
|------|-----|
| `admin` | Full access, automation, save |
| `operator` | View, functions, work queue |

## Source structure

```
src/
├── App.tsx                 # Shell, tabs, role selector
├── api.ts                  # REST client
├── api/bff.ts              # POST /bff/invoke (ispf-operator-v1)
├── types/                  # dashboard, workflow, bff, operatorUi, operatorManifest (legacy)
├── hooks/
│   ├── useObjectWebSocket.ts
│   ├── useBoundVariable.ts
│   ├── useTrendSeries.ts
│   ├── useOperatorUi.ts
│   └── useOperatorManifest.ts (legacy)
├── bpmn/
│   ├── ispf-moddle.json    # ISPF BPMN extensions
│   ├── constants.ts        # EMPTY_BPMN (with minimal bpmndi)
│   └── ensureDiagram.ts    # auto-layout when DI is missing
└── components/
    ├── ObjectTree.tsx
    ├── ExplorerView.tsx
    ├── ObjectPropertiesEditor.tsx
    ├── dashboard/          # Builder + 14 widgets
    ├── workflow/           # BPMN editor/viewer
    ├── automation/         # AlertRuleInspector, CorrelatorInspector, automationPath
    └── operator/           # OperatorView, OperatorDashboardApp, sidebar panels
```

`public/operator-apps/` — legacy fallback `{appId}.ui.json` (dev). Built-in app configuration: admin → `root.platform.operator-apps`.

## Dashboard builder

- 84-column fine grid, drag-and-drop, resize
- Widget add panel (all 14 types)
- `WidgetEditorPanel` — selected widget properties
- JSON preview layout
- Save via API

See [dashboards](dashboards.md).

## Workflow builder

- DRAFT / ACTIVE / STOPPED toggle
- Run button
- BPMN editor (bpmn-js) with ISPF moddle
- **Auto-layout:** if XML has no `bpmndi`, `ensureDiagram.ts` calls `bpmn-auto-layout` before display (see [workflows](workflows.md))
- Fallback XML viewer

See [workflows](workflows.md).

## Live data

1. **Polling** — `refetchInterval` = dashboard `refreshIntervalMs`
2. **WebSocket** — `useObjectWebSocket` invalidates cache on `VARIABLE_UPDATED`

Proxy (`vite.config.ts`): `/api`, `/ws` → `:8080`.

## Build

```bash
cd apps/web-console
npm install
npm run dev      # dev server
npm run build    # production dist/
npm run i18n:check   # locale keys vs en (canonical)
npm run i18n:translate   # regenerate ru/de/zh from en (tools/i18n/generate-locales.py)
```

## Localization (Phase 19)

- **Canonical locale:** English (`apps/web-console/src/locales/en/*.json`)
- **UI locales:** `en`, `ru`, `de`, `zh` — dropdown in admin/operator header and login card
- **Persistence:** `localStorage` (`ispf.ui.locale`), URL `?lang=`, fallback `en`
- **Adding strings:** key in `en/{namespace}.json` → `useTranslation` + `t('key')` → `npm run i18n:translate` → `npm run i18n:check`

See [0013-web-console-i18n](decisions/0013-web-console-i18n.md).

## Federation

**Federation** section (`root.platform.federation`, admin only) — tabs:

| Tab | Content |
|-----|---------|
| **Peers** | Peer table, new node form, **Sync catalog** (conflict preview → SKIP/BIND) |
| **Tokens** | Issue local federation token for another ISPF |
| **Tunnel** | Inbound registrations, outbound agents, secrets key (if capability `federation-tunnel`) |
| **Probe** | Proxy read probe for selected node |

On device/dashboard objects — **Federation bind** panel (`FederationBindPanel`): remote path check, bind/rebind, inline unbind confirmation. For catalog mirror — “Host locally” callout.

Components: `FederationPeersPanel`, `FederationCatalogSyncDialog`, `components/federation/*`, `FederationBindPanel`.

## System (admin)

**System** section (`SystemView`) — tabs:

| Tab | Content |
|-----|---------|
| **Metrics** | Platform metrics, health cards (Redis, NATS, YARG, MCP), **Platform backup** |
| **Runtime settings** | `GET/PATCH /api/v1/platform/runtime-settings` |
| **Events / Functions / Bindings** | Invoke/audit logs |
| **Change sets** | Platform change management |
| **App schedules** | `GET/POST /api/v1/schedules` — JDBC `platform_schedules` (not to be confused with object-tree `SCHEDULE` → `ScheduleEditor`) |
| **Semantic export** | Haystack JSON + Brick JSON-LD/Turtle (`GET /platform/haystack/export`, `GET /platform/brick/export`) |

**Application deploy (Inspector → APPLICATION → Deploy):**

| Panel | API |
|-------|-----|
| ApplicationBundlePanel | export, validate, deploy, pull-from-tree |
| ApplicationDeployPanel | deploy history, rollback, event catalog, function versions, bundle-objects lifecycle |
| ApplicationLifecyclePanel | `data/migrate`, `data/seed`, `data/status`, bindings deploy/refresh, reports deploy, functions deploy |

**WorkflowBuilder:** cancel/signal active BPMN instance (`POST /workflows/instances/{id}/cancel|signal`).

**Platform backup** (`PlatformBackupPanel`, BL-47): export JSON subtree `root.platform`; import with dry-run preview. API: `GET /api/v1/platform/backup/export`, `POST /api/v1/platform/backup/import?dryRun=true|false`. Federation proxy nodes are skipped on import.

## AI Studio

**AI Studio** tab (admin): **Agent** | **Bundle** | **Settings** modes (`.tabs`).

- **Agent** — chat sidebar; state in `AgentChatProvider` (not unmounted when switching tabs or console sections)
- **Settings** — LLM provider, model list (`GET /ai/models`), Context Pack, session root path, tool list, clear local chat cache
- **Bundle** — prompt, generate/validate/dry-run/publish, deploy history

Background execution: agent HTTP request continues when navigating to Explorer; indicator in header and dot on AI Studio tab. After closing the window, an incomplete request is restored from `localStorage` (server session polling).

See [ai-development](ai-development.md).

## UI dependencies

| Package | Purpose |
|---------|---------|
| @tanstack/react-query | Server state |
| recharts | chart, sparkline |
| bpmn-js | BPMN editor/viewer |
| bpmn-auto-layout | Generate DI for BPMN without graphical markup |

## Customization

- Styles: `src/styles.css` (CSS variables `--bg`, `--border`, …)
- New widget: type in `types/dashboard.ts`, view in `widgets/`, dispatch in `renderDashboardWidget.tsx` / `DashboardWidgetContent.tsx`, editor in `WidgetEditorPanel.tsx`
- **40+ widget types** (see `WIDGET_TYPES` in `types/dashboard.ts`)
