> **Язык:** русская версия (вычитка). Канонический английский: [en/web-console.md](../en/web-console.md).

# Web Console

> **Статус:** Stable — Explorer, System, AI Studio. Теги: [doc-status](doc-status.md).

React 19 + Vite 6 + TanStack Query. Каталог: `apps/web-console/`.

**URL:** all-in-one JAR — `http://localhost:8080`; Vite dev — `http://localhost:5173` (прокси `/api`, `/ws` → `:8080`). См. [getting-started](getting-started.md).

## Режимы работы

### Администратор (по умолчанию)

URL: `http://localhost:8080` (JAR) или `http://localhost:5173` (Vite dev)

| Область | Возможности |
|---------|-------------|
| Object tree | Поиск, раскрытие, CRUD, drag-and-drop порядка соседей |
| Explorer | Список дочерних объектов |
| Inspector | Свойства, переменные, события; alert rules и correlators |

![Admin Explorer — дерево объектов и свойства устройства](../assets/ispf-object-tree.png)

### Редакторы по типу объекта

Двойной клик в дереве:

| ObjectType | Компонент |
|------------|-----------|
| `DASHBOARD` | `DashboardBuilder` |
| `WORKFLOW` | `WorkflowBuilder` |
| `ALERT` | `AlertRuleInspector` |
| `CORRELATOR` | `CorrelatorInspector` |
| Остальные | `ObjectPropertiesEditor` |

### Operator HMI

URL: `http://localhost:8080?mode=operator` (JAR) или `http://localhost:5173?mode=operator` (Vite dev)

![Operator HMI — обзор мини-ТЭЦ с AI-ассистентом](../assets/ispf-operator-hmi.png)

- Полноэкранный дашборд (read-only)
- Sidebar: work queue + журнал событий
- Без редактирования layout и объектов

### Operator app (дашборды)

URL: `http://localhost:8080?mode=operator&app=platform` (JAR) или `http://localhost:5173?mode=operator&app=platform` (Vite dev)

Generic operator shell без отраслевого кода в `main`:

- Operator UI: `GET /api/v1/operator-apps/{appId}/ui` (встроенные apps, настройка в `root.platform.operator-apps`), затем `GET /api/v1/applications/{appId}/operator-ui` (bundle), legacy fallback `public/operator-apps/<appId>.ui.json`
- Навигация по вкладкам дашбордов; виджеты те же, что в Dashboard Builder (`DashboardBuilder` + `operatorMode`)
- BFF (`POST /api/v1/bff/invoke`) — только когда виджеты/функции приложения вызывают backend (не отдельный manifest shell)

### Legacy: operator manifest

`?mode=operator&app=demo` + `operatorManifest` — deprecated. Используйте `operatorUi` и объекты `DASHBOARD`.

## Навигация

Клиентский роутер не используется. Состояние в `App.tsx`:

- `mode`: `admin` | `operator`
- `app`: id operator application (опционально)
- `dashboard`: path объекта `DASHBOARD` (опционально)
- `screen`: id экрана в legacy manifest (deprecated)
- `selectedPath`, `editorPath`

## Роли

Вход через экран login; консоль использует **Bearer** после `POST /api/v1/auth/login`. Роль берётся из аутентифицированного пользователя.

`X-ISPF-Role` / селектор роли в заголовке **выключены по умолчанию** (`ispf.security.local-role-header-enabled=false`) — opt-in только для локальных экспериментов. См. [security](security.md), [getting-started](getting-started.md).

| Роль | UI |
|------|-----|
| `admin` | Полный доступ, automation, save |
| `operator` | Просмотр, функции, work queue |

## Структура исходников

```
src/
├── App.tsx                 # Shell, tabs, login
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

`public/operator-apps/` — legacy fallback `{appId}.ui.json` (dev). Конфигурация встроенных app: admin → `root.platform.operator-apps`.

## Dashboard builder

![Конструктор дашбордов — палитра виджетов и холст](../assets/ispf-dashboard-builder.png)

- Fine grid **84 колонки**, drag-and-drop, resize
- Панель добавления виджетов (все 14 типов)
- `WidgetEditorPanel` — свойства выбранного виджета
- JSON preview layout
- Сохранение через API

См. [dashboards](dashboards.md).

## Workflow builder

![BPMN-редактор workflow — MES work-order dispatch](../assets/ispf-bpmn-workflow.png)

- Переключатель DRAFT / ACTIVE / STOPPED
- Кнопка Run
- BPMN-редактор (bpmn-js) с ISPF moddle
- **Auto-layout:** если в XML нет `bpmndi`, `ensureDiagram.ts` вызывает `bpmn-auto-layout` перед показом (см. [workflows](workflows.md))
- Fallback XML viewer

См. [workflows](workflows.md).

## Live-данные

1. **Polling** — `refetchInterval` = `refreshIntervalMs` дашборда
2. **WebSocket** — `useObjectWebSocket` инвалидирует кэш при `VARIABLE_UPDATED`

Proxy (`vite.config.ts`): `/api`, `/ws` → `:8080`.

## Сборка

```bash
cd apps/web-console
npm install
npm run dev      # dev server
npm run build    # production dist/
npm run i18n:check   # locale keys vs en (canonical)
npm run i18n:translate   # regenerate ru/de/zh from en (tools/i18n/generate-locales.py)
```

## Локализация (Phase 19)

- **Canonical locale:** English (`apps/web-console/src/locales/en/*.json`)
- **UI locales:** `en`, `ru`, `de`, `zh` — dropdown в шапке admin/operator и на карточке login
- **Persistence:** `localStorage` (`ispf.ui.locale`), URL `?lang=`, fallback `en`
- **Добавление строк:** ключ в `en/{namespace}.json` → `useTranslation` + `t('key')` → `npm run i18n:translate` → `npm run i18n:check`

См. [0013-web-console-i18n](decisions/0013-web-console-i18n.md).

## Федерация

Раздел **Federation** (`root.platform.federation`, только admin) — вкладки:

| Вкладка | Содержимое |
|---------|------------|
| **Peers** | Таблица пиров, форма нового узла, **Sync catalog** (preview конфликтов → SKIP/BIND) |
| **Tokens** | Выпуск локального federation token для другого ISPF |
| **Tunnel** | Inbound registrations, outbound agents, secrets key (если capability `federation-tunnel`) |
| **Probe** | Proxy read probe для выбранного узла |

На объектах device/dashboard — панель **Federation bind** (`FederationBindPanel`): проверка remote path, bind/rebind, inline-подтверждение unbind. Для catalog mirror — callout «Host locally».

Компоненты: `FederationPeersPanel`, `FederationCatalogSyncDialog`, `components/federation/*`, `FederationBindPanel`.

## System (admin)

Раздел **System** (`SystemView`) — вкладки:

| Вкладка | Содержимое |
|---------|------------|
| **Metrics** | Метрики платформы, health cards (Redis, NATS, YARG, MCP), **Platform backup** |
| **Runtime settings** | `GET/PATCH /api/v1/platform/runtime-settings` |
| **Events / Functions / Bindings** | Invoke/audit logs |
| **Change sets** | Управление изменениями платформы |
| **App schedules** | `GET/POST /api/v1/schedules` — JDBC `platform_schedules` (не путать с object-tree `SCHEDULE` → `ScheduleEditor`) |
| **Semantic export** | Haystack JSON + Brick JSON-LD/Turtle (`GET /platform/haystack/export`, `GET /platform/brick/export`) |

**Application deploy (Inspector → APPLICATION → Deploy):**

| Панель | API |
|--------|-----|
| ApplicationBundlePanel | export, validate, deploy, pull-from-tree |
| ApplicationDeployPanel | deploy history, rollback, event catalog, function versions, bundle-objects lifecycle |
| ApplicationLifecyclePanel | `data/migrate`, `data/seed`, `data/status`, bindings deploy/refresh, reports deploy, functions deploy |

**WorkflowBuilder:** cancel/signal активного BPMN instance (`POST /workflows/instances/{id}/cancel|signal`).

**Platform backup** (`PlatformBackupPanel`, BL-47): экспорт JSON-поддерева `root.platform`; импорт с dry-run preview. API: `GET /api/v1/platform/backup/export`, `POST /api/v1/platform/backup/import?dryRun=true|false`. Federation proxy nodes при импорте пропускаются.

## AI Studio

Вкладка **AI Studio** (admin): режимы **Agent** | **Bundle** | **Settings** (`.tabs`).

![AI Studio — чат tree-first агента](../assets/ispf-ai-studio.png)

- **Agent** — chat sidebar; состояние в `AgentChatProvider` (не размонтируется при переключении вкладок или разделов консоли)
- **Settings** — LLM provider, список моделей (`GET /ai/models`), Context Pack, session root path, список tools, очистка локального chat cache
- **Bundle** — prompt, generate/validate/dry-run/publish, deploy history

Фоновое выполнение: HTTP-запрос агента продолжается при переходе в Explorer; индикатор в шапке и точка на вкладке AI Studio. После закрытия окна незавершённый запрос восстанавливается из `localStorage` (polling server session).

См. [ai-development](ai-development.md).

## UI-зависимости

| Package | Назначение |
|---------|------------|
| @tanstack/react-query | Server state |
| recharts | chart, sparkline |
| bpmn-js | BPMN editor/viewer |
| bpmn-auto-layout | Генерация DI для BPMN без графической разметки |

## Кастомизация

- Стили: `src/styles.css` (CSS variables `--bg`, `--border`, …)
- Новый виджет: тип в `types/dashboard.ts`, view в `widgets/`, dispatch в `renderDashboardWidget.tsx` / `DashboardWidgetContent.tsx`, editor в `WidgetEditorPanel.tsx`
- **40+ типов виджетов** (см. `WIDGET_TYPES` в `types/dashboard.ts`)
