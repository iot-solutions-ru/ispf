> **Язык:** русская версия. Английский оригинал: [en/web-console.md](../en/web-console.md).

# Веб-консоль (Web Console)

> **Статус:** Stable — консоль администратора, пульт оператора, системная диагностика и ИИ-студия. Метки зрелости: [doc-status.md](doc-status.md).

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

Оболочка оператора общая для всех приложений (отраслевой код в `ispf-server` не добавляется):

- UI приложения: сначала `GET /api/v1/operator-apps/{appId}/ui` (встроенные apps из `root.platform.operator-apps`), затем `GET /api/v1/applications/{appId}/operator-ui` (из `bundle.json`); в dev при отсутствии обоих возможен резервный файл `public/operator-apps/<appId>.ui.json`
- Навигация по вкладкам дашбордов; те же виджеты, что в конструкторе (`DashboardBuilder` + `operatorMode`)
- BFF (`POST /api/v1/bff/invoke`) вызывается только когда виджет или функция приложения обращается к backend

### Устаревший operator manifest

Параметры `?mode=operator&app=demo` вместе с `operatorManifest` больше не используются. Настраивайте `operatorUi` и объекты `DASHBOARD`.

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
    ├── dashboard/          # Конструктор дашбордов и более 40 типов виджетов
    ├── workflow/           # Встроенный редактор и просмотрщик BPMN
    ├── automation/         # AlertRuleInspector, CorrelatorInspector, automationPath
    └── operator/           # OperatorView, OperatorDashboardApp, sidebar panels
```

`public/operator-apps/` — резервный каталог dev-режима `{appId}.ui.json`. Настройка приложений оператора выполняется в `root.platform.operator-apps`.

## Конструктор дашбордов (Dashboard Builder)

![Конструктор дашбордов — палитра виджетов и холст](../assets/ispf-dashboard-builder.png)

- Сетка **84 колонки × шаг высоты 8 пикселей**, перетаскивание и изменение размеров карточек
- Палитра добавления виджетов (свыше 40 типов)
- `WidgetEditorPanel` — настройка свойств выбранного виджета
- Просмотр и экспорт JSON макета
- Сохранение через REST API

См. [Дашборды](dashboards.md) и [Справочник виджетов](widgets.md).

## Редактор процессов (Workflow Builder)

![Визуальный редактор BPMN в веб-консоли](../assets/ispf-bpmn-workflow.png)

- Переключатель статуса `DRAFT` / `ACTIVE` / `STOPPED` и кнопка ручного запуска
- Собственный векторный редактор диаграмм BPMN (`IspfBpmnEditor`, [ADR-0052](decisions/0052-ispf-workflow-diagram-editor.md))
- Автоматическая раскладка блоков для схем без координат (`bpmndi`) и режим просмотра XML

См. [Процессы BPMN](workflows.md).

## Live-данные

1. **Polling** — `refetchInterval` = `refreshIntervalMs` дашборда
2. **WebSocket** — `useObjectWebSocket` инвалидирует кэш при `VARIABLE_UPDATED`
3. **Большие папки** — развёрнутая папка дерева с более чем 64 детьми не подписывается на живые обновления. У строки метка «Без live»; чтобы обновить список, сверните и снова разверните папку.

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

![System → Metrics overview](../assets/ispf-system-metrics.png)

Раздел **System** (`SystemView`) — вкладки:

| Вкладка | Содержимое |
|---------|------------|
| **Metrics** | Метрики платформы, health cards (Redis, NATS, YARG, MCP), **Platform backup** |
| **Runtime settings** | `GET/PATCH /api/v1/platform/runtime-settings`; **Перезапуск сервера** → `POST .../runtime-settings/restart` |
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

**Platform backup** (`PlatformBackupPanel`, BL-47): экспорт JSON-поддерева `root.platform`; импорт с dry-run preview. API: `GET /api/v1/platform/backup/export`, `POST /api/v1/platform/backup/import?dryRun=true|false`. Federation proxy nodes при импорте пропускаются. В папке каталога есть **Импорт из JSON**: экспорт поддерева ровно с одним узлом создаёт новый объект в открытой папке. Путь в файле не используется, имя вводится при импорте. Импорт в каталог mixin, типов экземпляров или singleton регистрирует blueprint. Цель остаётся `CUSTOM` или `DEVICE`; другой тип исходного узла становится `CUSTOM`. API: `POST /api/v1/platform/backup/import-node?parentPath=&name=`.

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
