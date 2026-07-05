# Public API для разработчика решений

Стабильная граница между **platform** (ISPF core) и **solution** (ваш bundle). Подробный workflow — [SOLUTION_DEVELOPER_GUIDE.md](SOLUTION_DEVELOPER_GUIDE.md). Архитектурные границы — [0001](decisions/0001-app-platform-boundary.md).

---

## Разрешено (stable contract)

| Область | API / артефакт | Примечание |
|---------|----------------|------------|
| Регистрация app | `POST /api/v1/applications` | `appId`, `schemaName`, `tablePrefix` |
| SQL schema app | `POST .../data/migrate`, `.../data/seed` | Не Flyway platform |
| Bundle deploy | `POST .../deploy`, `POST /api/v1/platform/packages/import` | Один manifest JSON |
| Script functions | `POST .../functions/deploy`, tree path `{appId}.functions.*` | JSON script steps |
| Invoke | `POST .../functions/invoke`, `POST /api/v1/bff/invoke` | Operator wire profile |
| Object tree | `GET/POST/PATCH /api/v1/objects/**` | CRUD, variables, functions |
| Models | `POST /api/v1/blueprints`, apply to device | Blueprint |
| Dashboards / workflows | Object tree + REST | Layout JSON, BPMN XML |
| Automation | Alert rules, correlators в дереве | CEL, correlator patterns |
| Operator UI | `operatorUi` / `dashboards[]` в bundle | `GET .../operator-ui` |
| Reports | Tree-first `root.platform.reports.*` | SQL + optional YARG |
| WebSocket | `/ws/objects` — `subscribe`, `subscribe_events` | Token query param; см. [MESSAGING.md](MESSAGING.md) |
| Event catalog | `events[]` в bundle, `GET .../events` | Роли для WS subscribe (FW-31) |
| Bundle dependencies | `requires[]` в bundle | minVersion другого appId (FW-12) |
| Drivers | SPI `DeviceDriver` в отдельном JAR | [DRIVERS.md](DRIVERS.md) |
| AI tools (platform admin) | `POST /api/v1/ai/tools/*`, Studio | [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) — не меняет stable bundle contract |

## Запрещено

| Действие | Почему |
|----------|--------|
| Java в `packages/ispf-server/` | [0001](decisions/0001-app-platform-boundary.md) |
| Flyway миграции app tables в platform | App schema только через migrate API |
| Отраслевые BFF routes | Только `/api/v1/bff/invoke` |
| Fork platform server для одного заказчика | Bundle deploy + config |

## Bundle manifest (semver)

Поле `version` в manifest — **semver** (`MAJOR.MINOR.PATCH`). Сравнение при `requires[].minVersion` и `license.minPlatformVersion` — numeric semver (platform `GET /api/v1/info`).

| Изменение | Уровень | Пример |
|-----------|---------|--------|
| Удаление/переименование function, event, object | **MAJOR** | удалён `mes_listOrders` |
| Изменение input/output schema существующей function | **MAJOR** | новое обязательное поле input |
| Добавление optional events, objects, dashboards | **MINOR** | новый `events[]` id |
| Исправление описаний, seed data, non-breaking script | **PATCH** | текст operatorUi |

| Поле | Стабильность |
|------|--------------|
| `version` | Semver bundle; история deploy |
| `schemaName`, `migrations`, `functions` | Stable |
| `objects`, `dashboards`, `workflows`, `models` | Stable (Phase 5 tree-first) |
| `alertRules`, `correlators`, `operatorUi` | Stable |
| `events[]` | Stable — id, roles, optional payloadSchema |
| `requires[]` | Stable — appId, minVersion |
| `license` | Optional; commercial — [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) |

## Версия platform

- `GET /api/v1/info` → `version` — для `minPlatformVersion` в license.
- Совместимость: platform ≥ `minPlatformVersion` в commercial license.

## Commercial bundle

Секция `license` в manifest — см. [0003](decisions/0003-commercial-bundle-licensing.md). Apache reference bundles (`warehouse`, `lab-training`, `mes-reference`) — без `license`.

## Связанные документы

- [APPLICATIONS.md](APPLICATIONS.md) — полный deploy API
- [MESSAGING.md](MESSAGING.md) — async vs sync, NATS subjects, WS events
- [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) — AI Layer (FW-40…43), admin tools
- [API.md](API.md) — REST справочник
- [PLUGINS.md](PLUGINS.md) — границы `main`
