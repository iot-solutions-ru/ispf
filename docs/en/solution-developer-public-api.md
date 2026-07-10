> **Language:** Canonical English. Russian edition: [ru/solution-developer-public-api.md](../ru/solution-developer-public-api.md).

# Solution Developer Public API

Stable boundary between **platform** (ISPF core) and **solution** (your bundle). Detailed workflow — [solution-developer-guide](solution-developer-guide.md). Architectural boundaries — [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md).

---

## Allowed (stable contract)

| Area | API / artifact | Note |
|------|----------------|------|
| App registration | `POST /api/v1/applications` | `appId`, `schemaName`, `tablePrefix` |
| App SQL schema | `POST .../data/migrate`, `.../data/seed` | Not platform Flyway |
| Bundle deploy | `POST .../deploy`, `POST /api/v1/platform/packages/import` | Single manifest JSON |
| Script functions | `POST .../functions/deploy`, tree path `{appId}.functions.*` | JSON script steps |
| Invoke | `POST .../functions/invoke`, `POST /api/v1/bff/invoke` | Operator wire profile |
| Object tree | `GET/POST/PATCH /api/v1/objects/**` | CRUD, variables, functions |
| Blueprints | `POST /api/v1/blueprints`, apply to device | Blueprint |
| Dashboards / workflows | Object tree + REST | Layout JSON, BPMN XML |
| Automation | Alert rules, correlators in tree | CEL, correlator patterns |
| Operator UI | `operatorUi` / `dashboards[]` in bundle | `GET .../operator-ui` |
| Reports | Tree-first `root.platform.reports.*` | SQL + optional YARG |
| WebSocket | `/ws/objects` — `subscribe`, `subscribe_events` | Token query param; see [messaging](messaging.md) |
| Event catalog | `events[]` in bundle, `GET .../events` | Roles for WS subscribe (FW-31) |
| Bundle dependencies | `requires[]` in bundle | minVersion of another appId (FW-12) |
| Drivers | SPI `DeviceDriver` in separate JAR | [drivers](drivers.md) |
| AI tools (platform admin) | `POST /api/v1/ai/tools/*`, Studio | [ai-development](ai-development.md) — does not change stable bundle contract |

## Forbidden

| Action | Why |
|--------|-----|
| Java in `packages/ispf-server/` | [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md) |
| Platform Flyway for app tables | App schema only via migrate API |
| Domain-specific BFF routes | Only `/api/v1/bff/invoke` |
| Fork platform server for one customer | Bundle deploy + config |

## Bundle manifest (semver)

Manifest field `version` is **semver** (`MAJOR.MINOR.PATCH`). Comparison for `requires[].minVersion` and `license.minPlatformVersion` uses numeric semver (platform `GET /api/v1/info`).

| Change | Level | Example |
|--------|-------|---------|
| Remove/rename function, event, object | **MAJOR** | removed `mes_listOrders` |
| Change input/output schema of existing function | **MAJOR** | new required input field |
| Add optional events, objects, dashboards | **MINOR** | new `events[]` id |
| Description fixes, seed data, non-breaking script | **PATCH** | operatorUi text |

| Field | Stability |
|-------|-----------|
| `version` | Semver bundle; deploy history |
| `schemaName`, `migrations`, `functions` | Stable |
| `objects`, `dashboards`, `workflows`, `models` | Stable (Phase 5 tree-first) |
| `alertRules`, `correlators`, `operatorUi` | Stable |
| `events[]` | Stable — id, roles, optional payloadSchema |
| `requires[]` | Stable — appId, minVersion |
| `license` | Optional; commercial — [commercial-licensing](commercial-licensing.md) |

## Platform version

- `GET /api/v1/info` → `version` — for `minPlatformVersion` in license.
- Compatibility: platform ≥ `minPlatformVersion` in commercial license.

## Commercial bundle

`license` section in manifest — see [0003-commercial-bundle-licensing](decisions/0003-commercial-bundle-licensing.md). Apache reference bundles (`warehouse`, `lab-training`, `mes-reference`) — no `license`.

## Related documents

- [applications](applications.md) — full deploy API
- [messaging](messaging.md) — async vs sync, NATS subjects, WS events
- [ai-development](ai-development.md) — AI layer (FW-40…43), admin tools
- [api](api.md) — REST reference
- [plugins](plugins.md) — `main` boundaries
