> **Язык:** русская версия (вычитка). Канонический английский: [en/web-console.md](../en/web-console.md).

# Веб-консоль

React 19 + Vite 6 + TanStack Query. Каталог: `apps/web-console/`.

## Режимы работы

### Администратор (по умолчанию)

URL: `http://localhost:5173`

| Область | Функции |
|---------|---------|
| Древесные объекты | Поиск, раскрытие, CRUD, перетаскивание порядка соседей |
| Исследователь | Список дочерних объектов |
| инспектор | Свойства, переменные, события; правила оповещений и корреляторы |

### Редакторы по типу объекта

Двойной клик в деревне:

| ТипОбъекта | Компонент |
|------------|-----------|
| `DASHBOARD` | `DashboardBuilder` |
| `WORKFLOW` | `WorkflowBuilder` |
| `ALERT` | `AlertRuleInspector` |
| `CORRELATOR` | `CorrelatorInspector` |
| Остальные | `ObjectPropertiesEditor` |

### HMI оператора

URL: `http://localhost:5173?mode=operator`

- Полноэкранный дашборд (только чтение)
- Боковая панель: рабочая очередь + журнал событий.
- Без редактирования макета и объектов

### Приложение Оператора (дашборды)

URL: `http://localhost:5173?mode=operator&app=platform`

Generic operator shell без отраслевого кода в `main`:

- Пользовательский интерфейс оператора: `GET /api/v1/operator-apps/{appId}/ui` (встроенные приложения, настройка в `root.platform.operator-apps`), затем `GET /api/v1/applications/{appId}/operator-ui` (пакет), устаревший резервный вариант `public/operator-apps/<appId>.ui.json`
- Навигация по вкладкам дашбордов; виджеты — то же что в Dashboard Builder (`DashboardBuilder` + `operatorMode`)
- BFF (`POST /api/v1/bff/invoke`) — только если виджеты/функции приложения вызывают серверную часть (не отдельную оболочку манифеста)

### Устаревшая версия: манифест оператора

`?mode=operator&app=demo` + `operatorManifest` — deprecated. Используйте `operatorUi` и объекты `DASHBOARD`.

## Навигация

Клиентский роутер не используется. Состояние в `App.tsx`:

- `mode`: `admin` | `operator`
- `app`: operator application id (опционально)
- `dashboard`: path объекта `DASHBOARD` (опционально)
- `screen`: id экрана в legacy manifest (deprecated)
- `selectedPath`, `editorPath`

## Роли

Селектор в шапке → `X-ISPF-Role: admin|operator` (`src/auth/role.ts`).

| Роль | Пользовательский интерфейс |
|------|-----|
| `admin` | Полный доступ, автоматизация, сохранение |
| `operator` | Просмотр, функции, work queue |

## Структура исходников

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
│   ├── constants.ts        # EMPTY_BPMN (с минимальным bpmndi)
│   └── ensureDiagram.ts    # auto-layout при отсутствии DI
└── components/
    ├── ObjectTree.tsx
    ├── ExplorerView.tsx
    ├── ObjectPropertiesEditor.tsx
    ├── dashboard/          # Builder + 14 widgets
    ├── workflow/           # BPMN editor/viewer
    ├── automation/         # AlertRuleInspector, CorrelatorInspector, automationPath
    └── operator/           # OperatorView, OperatorDashboardApp, sidebar panels
```

`public/operator-apps/` — legacy fallback `{appId}.ui.json` (dev). Настройка встроенных app — админка → `root.platform.operator-apps`.

## Конструктор дашбордов

- Сетка 12 колонок, перетаскивание, изменение размера
- Панель добавления виджетов (все 14 типов)
- `WidgetEditorPanel` — свойства выбранного виджета
- Макет предварительного просмотра JSON
- Сохранение через API

См. [dashboards](dashboards.md).

## Построитель рабочих процессов

- Переключение ПРОХОД/АКТИВ/ОСТАНОВЛЕНО
- Кнопка «Выполнить»
- Редактор BPMN (bpmn-js) с модулем ISPF
- **Автоматическая компоновка:** если XML без `bpmndi`, `ensureDiagram.ts` вызывает `bpmn-auto-layout` перед использованием (см. [workflows](workflows.md))
- Резервный просмотрщик XML

См. [workflows](workflows.md).

## Live-данные

1. **Опрос** — `refetchInterval` = `refreshIntervalMs` дашборда
2. **WebSocket** — `useObjectWebSocket` отключает кеш при `VARIABLE_UPDATED`

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

## Локализация (этап 19)

- **Canonical locale:** English (`apps/web-console/src/locales/en/*.json`)
- **локали пользовательского интерфейса:** `en`, `ru`, `de`, `zh` — раскрывающийся список в заголовке администратора/оператора и карточке входа.
- **Persistence:** `localStorage` (`ispf.ui.locale`), URL `?lang=`, fallback `en`
- **Adding strings:** key in `en/{namespace}.json` → `useTranslation` + `t('key')` → `npm run i18n:translate` → `npm run i18n:check`

См. [0013-web-console-i18n](decisions/0013-web-console-i18n.md).

## Федерация

Раздел **Федерация** (`root.platform.federation`, только admin) — вкладки:

| Вкладка | Содержимое |
|---------|------------|
| **Узлы** | Таблица пиров, форма нового узла, **Каталог синхронизации** (предварительный просмотр → SKIP/BIND) |
| **Токены** | Выпуск локального токена федерации для другого ISPF |
| **Туннель** | Входящие регистрации, исходящие агенты, секретный ключ (если возможность `federation-tunnel`) |
| **Проверка** | Прокси-зонд чтения по выбранному узлу |

На объекте устройства/дашборда — панель **Привязка к федерации** (`FederationBindPanel`): проверка удаленного пути, привязка/перепривязка, встроенное подтверждение от привязок. Для зеркала каталога — пометка «Разместить локально».

Компоненты: `FederationPeersPanel`, `FederationCatalogSyncDialog`, `components/federation/*`, `FederationBindPanel`.

## Система (админ)

Раздел **Система** (`SystemView`) — вкладки:

| Вкладка | Содержимое |
|---------|------------|
| **Показатели** | Метрики платформы, карты работоспособности (Redis, NATS, YARG, MCP), **Резервное копирование платформы** |
| **Runtime settings** | `GET/PATCH /api/v1/platform/runtime-settings` |
| **События/Функции/Привязки** | Журналы вызов/аудит |
| **Изменить наборы** | Управление изменениями платформы |
| **App schedules** | `GET/POST /api/v1/schedules` — JDBC `platform_schedules` (не путать с object-tree `SCHEDULE` → `ScheduleEditor`) |
| **Semantic export** | Haystack JSON + Brick JSON-LD/Turtle (`GET /platform/haystack/export`, `GET /platform/brick/export`) |

**Развертывание приложения (Инспектор → ПРИЛОЖЕНИЕ → Развертывание):**

| Панель | API |
|--------|-----|
| ApplicationBundlePanel | экспорт, проверка, развертывание, извлечение из дерева |
| Панель развертывания приложения | история развертывания, откат, каталог событий, версии функций, жизненный цикл объектов пакета |
| ApplicationLifecyclePanel | `data/migrate`, `data/seed`, `data/status`, развертывание/обновление привязок, развертывание отчетов, развертывание функций |

**WorkflowBuilder:** cancel/signal активного BPMN instance (`POST /workflows/instances/{id}/cancel|signal`).

**Резервная копия платформы** (`PlatformBackupPanel`, BL-47): экспорт поддерева JSON `root.platform`; импорт с пробным просмотром. API: `GET /api/v1/platform/backup/export`, `POST /api/v1/platform/backup/import?dryRun=true|false`. Прокси-узлы Федерации при импорте продаются.

## Студия искусственного интеллекта

Вкладка **AI Studio** (админ): режимы **Агент** | **Пакет** | ** Настройки** (`.tabs`).

- **Агент** — боковая панель чатов; состояние в `AgentChatProvider` (не размонтируется при переключении вкладок или разделов консоли)
- ** Настройки** — провайдер LLM, список моделей (`GET /ai/models`), Context Pack, корневой путь сессий, список инструментов, очистка локального кэша чатов
- **Пакет Bundle** — подсказка, генерация/проверка/пробный запуск/публикация, история развертывания.

Фоновое выполнение: HTTP-запрос агента продолжается при переходе в Обозреватель; индикатор в шапке и точка на вкладке AI Studio. После закрытия окна незавершённый запрос восстанавливается из `localStorage` (сессия опроса на сервере).

См. [ai-development](ai-development.md).

## Зависимости пользовательского интерфейса

| Пакет | Назначение |
|-------|------------|
| @tanstack/реакция-запрос | Состояние сервера |
| перезарядка | диаграмма, спарклайн |
| bpmn-js | Редактор/просмотрщик BPMN |
| bpmn-авто-макет | Генерация DI для BPMN без графической разметки |

## Кастомизация

- Стили: `src/styles.css` (CSS variables `--bg`, `--border`, …)
- Новый виджет: ввод в `types/dashboard.ts`, просмотр в `widgets/`, отправка в `renderDashboardWidget.tsx` / `DashboardWidgetContent.tsx`, редактор в `WidgetEditorPanel.tsx`
- **40+ типов** виджетов (см. `WIDGET_TYPES` в `types/dashboard.ts`)
