> **Язык:** русская версия (вычитка). Канонический английский: [en/agent-knowledge.md](../en/agent-knowledge.md).

﻿# AGENT_KNOWLEDGE — справочник внутреннего агента ISPF

Документ для **tree-first агента**, AI Studio и MCP-клиентов. Описывает **все подходы к созданию приложений/решений** и предоставляет **карту документации** платформы.

**Как читать:** `search_context(query=..., topic=...)` → полный текст срезов в ContextPack. Этот файл — **маршрутизатор**: что выбрать и куда смотреть дальше.

См. также [APPLICATION_PRINCIPLES.md](application-principles.md) (канонический свод P1–P10), [AI_DEVELOPMENT.md](ai-development.md), [0001](decisions/0001-app-platform-boundary.md), [0005](decisions/0005-tree-first-ai-agent.md).

---

## Target approach

Каноническая формулировка и развёрнутые правила: [APPLICATION_PRINCIPLES.md](application-principles.md).

1. **Бизнес-логика — в конфигурации дерева объектов**, не на Java `ispf-server` и не в коде React платформы.
2. **Одна среда выполнения:** invoke, workflow, оповещения, дашборды, привязки — через **дерево объектов** и API платформы.
3. **Запись приложения** (таблица `applications`) — **реестр + изолированная SQL-схема**, не параллельный движок.
4. **Bundle Deploy** — доставка декларативной конфигурации в дерево; после развертывания всё адресируется деревом путей.
5. **Агент не пишет Java/React в `main`:** только проверенный JSON (пакет, макет, правила, модели) и вызовы инструментов платформы.

Запрещено: платформа Flyway для приложений-таблиц, hardcoded маршруты BFF, отраслевая Java на сервере.

---

## Подходы к созданию приложений (выбор стратегии)

| # | Подход | Когда использовать | Доставка | Пользовательский интерфейс оператора |
|---|--------|-------------------|----------|-------------|
| **А** | **Сначала дерево (инструменты агента/Проводник)** | Демо, SNMP, лабораторная работа, быстрый POC, пошаговая настройка пользователем | `create_object`, `set_variable`, `configure_driver`, инструменты для панелей | `configure_operator_ui` |
| **Б** | **Консоль администратора (ручная сборка)** | Инженер без пакета, итеративный HMI | Пользовательский интерфейс: модели, конструктор дашбордов, инспектор | Панель приложений оператора |
| **С** | **Развертывание пакета (манифест)** | Производственное решение, CI/CD, повторяющийся релиз | `POST .../applications/{id}/deploy` или `import_package` | `operatorUi` в манифесте |
| **Д** | **Пошаговый REST API** | Автоматизация без ZIP, поэтапная интеграция | регистрация → миграция → функции → развертывание разделов | `PUT operator-apps/.../ui` |
| **Е** | **AI Studio (генерация → проверка → импорт)** | Черновик манифест из промпта | `validate_bundle` → `dry_run_deploy` → `import_package` | из сгенерированного `operatorUi` |
| ** Ч** | **Справочный пример** | Обучение, MES/шаблон лаборатории | `get_example_bundle` → адаптировать → импортировать | из примера манифеста |
| **Г** | **Только для HMI платформы** | Только мониторинг без схемы приложения | дашборды + правила привязки на дереве | встроенное `platform` приложение для оператора |
| **Ч** | **Коммерческий пакет** | Лицензируемое решение | подписанный пакет + лицензионные gates | как в C |

### Дерево решений (кратко)

```
Нужна изолированная SQL-schema приложения (orders, batches, …)?
  ├─ ДА → C/D/E/F/H (bundle или пошаговый API) + migrations[]
  └─ НЕТ → A/B/G (tree-only: devices, dashboards, rules, workflows на platform tree)

Нужен повторяемый релиз / CI?
  └─ C или E (bundle + validate gates)

Интерактивная сессия с пользователем в AI Studio?
  └─ A (tools) — предпочтительно; bundle import только после validate+dry_run

Полноценный MES/terminal с BFF-таблицами?
  └─ F (mes-reference) или C с functions[] + operatorUi + dashboards[]
```

---

## SIF — Структура приема спецификаций

Универсальный набор для **любого** задания (полное ТЗ, короткая подсказка, продолжение). Насосная станция — эталонное приспособление, не частный случай.

### Конвейер (сложные задания)

1. **Классифицировать** → `assignmentType`; **разложить неявные фразы** → `specBrief` (сущности, FR-* с `sourcePhrase`)
2. **Откройте для себя** → рецепты, профили, `list_objects`, `get_automation_schema`
3. **Объем** → `intent_scope` связывает FR со слоями; `assumptions[]` для выведенного
4. **Матрица пробелов** → FR → возможности (`full` / `out_of_scope`)
5. **Вопросы** → ≤3/ход; пользователь может выбрать несколько ответов в чате
6. **План** → `plan.sections[]` поэтапно (≤2/ход) → **СИНТЕЗ** обогащает секцию → утверждение, когда аналитический вентиль ОК

UI: `executiveSummary`, таблица FR в `specBrief`, `gapMatrix`, список `planCompletenessGaps` до «Утвердить полный план».

**Fast path:** `monitoring_lab`, `explore_readonly`, `follow_up` — без 5-turn torture.

### Адаптеры домена

| Адаптер | Пособие | Шаблон |
|---------|----------|----------|
| `industrial_oil_gas` | `virtualPumpStation()` + abbrev lexicon | `scada-facility-overview` |
| `snmp_lab` | SNMP playbooks | `snmp-host-monitoring` |
| `mes_terminal` | `mesReferenceLifecycle()` | — |
| `_default` | `projectBlueprintGuide()` | `monitoring-overview` |

### Охрана и предполетная подготовка

- **Одобрение:** «Да, начинаю», «Утверждаю», первичное предложение → исполнение без перепланировки.
- **Preflight:** перед mutation — hint + `suggestedDiscovery` если parent/path не grounded
- **Регистр пути:** совпадение без учета регистра, подсказка «Использовать точный путь: …»
- **Готово:** блокировка при ОШИБКЕ в очереди, пустая мнемосхема, панель управления без виджетов, диаграмма без истории.
- **Judge:** pre-finish verdict `approve | rework | gap_required | user_moderation_required`

### Эталонные приборы (тесты)

- Pump station TZ → `industrial_facility`
- «SNMP localhost мониторинг» → `monitoring_lab` fast path
- «Разверни MES demo» → `application_bundle`

См. `AgentPlaybooks.specIntakeGuide()`, `SpecIntakeScenarioTest`.

---

## A. Tree-first (инструменты агента)

**Суть:** сборное решение **на живом дереве** без развертывания ZIP. Адаптация для «создания SNMP localhost и дашборда», виртуальной лаборатории, оповещений на хабе.

**Типичная последовательность:**

1. `list_object_models` → `create_object` (УСТРОЙСТВО/ПАНЕЛЬ ПАНЕЛИ/ПОЛЬЗОВАТЕЛЬСКИЙ/РАБОЧИЙ ПРОЦЕСС/…)
2. `configure_driver` + `driver_control start` (если УСТРОЙСТВО)
3. `configure_variable_history` (для диаграммы/спарклайна)
4. `create_variable` / правила привязки (`set_variable` + CEL) в CUSTOM Hub
5. `configure_alert` / `configure_correlator`
6. `create_object DASHBOARD` → `set_dashboard_layout template=...` или `add_dashboard_widget`
7. Правила платформы (ADR-0019): обязательные правила на DASHBOARD с `target.kind=context`, `onContextChange`
8. `configure_operator_ui` — панель управления по умолчанию + меню.
9. `list_variables` → `finish` с путями для пользовательского интерфейса

**Сборники в системной подсказке:** SNMP, виртуальный кластер, Modbus, MES, отчеты, виджеты, мнемосхема SCADA — см. `AgentPlaybooks.*`.

### Чертеж проекта (8 слоёв)

Полноценный проект на tree-first = **8 слоёв** (см. `get_automation_schema topic=projectBlueprint`):

| # | Слой | Путь | Инструменты |
|---|------|------|-------|
| 1 | Hub (ABSOLUTE) | `root.platform.instances.{project}` | `ensure_absolute_instance`, binding rules, `refAt` |
| 2 | Устройства | `root.platform.devices.{project}/*` | `instantiate_instance_type`, `apply_relative_model`, `create_virtual_device` |
| 3 | Dashboard | `root.platform.dashboards.{project}-*` | `set_dashboard_layout`, `add_dashboard_widget` |
| 4 | SCADA | `root.platform.mimics.{project}-*` | `save_mimic_diagram`, `get_mimic_diagram` |
| 5 | Alerts | `root.platform.alert-rules.{project}-*` | `configure_alert` |
| 6 | Correlators | `root.platform.correlators.{project}-*` | `configure_correlator` |
| 7 | Workflows | `root.platform.workflows.{project}-*` | `save_workflow_bpmn` |
| 8 | Reports | `root.platform.reports.{project}-*` | `configure_report` |

**Три вида моделей:** `get_automation_schema topic=instanceTypes` — RELATIVE (примесь существующего объекта), INSTANCE (новый объект), ABSOLUTE (одноэлементный концентратор).

**Каталог рецептов (1410):** `search_platform_recipes`, `get_automation_schema topic=recipes|projects|recipe/{id}`. **500** готовых отраслевых проектов (`project-{industry}-{archetype}`). Полный индекс: [AGENT_RECIPES.md](agent-recipes.md).

**Финишный охранник:** агент не может `finish` без `list_variables` на DEVICE, `configure_alert` при намерении мониторинга, `get_mimic_diagram elementCount>0` при SCADA.

**Не использовать:** `set_variable name=widgets` на приборной панели; макет только в переменной `layout`.
**SCADA:** `save_mimic_diagram` / `add_mimic_elements` — не `set_variable name=diagram`.

### Полный каталог инструментов агента (администратор, ~85 инструментов)

| Область | Инструменты |
|---------|-------|
| Discovery | `search_context`, `list_drivers`, `get_driver_help`, `list_examples`, `get_example_bundle`, `get_widget_catalog`, `list_applications` |
| Object tree | `list_objects`, `get_object`, `create_object`, `delete_object`, `search_objects`, `search_by_haystack_tags`, `list_object_models` |
| Variables | `list_variables`, `describe_variables`, `set_variable`, `create_variable`, `configure_variable_history` |
| Drivers | `configure_driver`, `driver_control` |
| Bindings | `create_binding_rule`, `list_binding_rules`, `configure_platform_context_rule` |
| Dashboards | `get_dashboard_layout`, `set_dashboard_layout`, `add_dashboard_widget` |
| SCADA | `list_mimic_symbols`, `save_mimic_diagram`, `add_mimic_elements`, `get_mimic_diagram` |
| Reports | `list_reports`, `get_report_schema`, `run_report`, `configure_report` |
| Automation | `configure_alert`, `configure_correlator`, `list_automation`, `get_automation_schema` |
| Workflows | `get_workflow`, `save_workflow_bpmn`, `run_workflow`, `update_workflow_status`, `list_workflow_instances`, `signal_workflow_instance`, `cancel_workflow_instance` |
| Приложения | `register_application`, `application_data_*`, `deploy_app_binding`, `deploy_app_function`, `validate_bundle`, `dry_run_deploy`, `import_package`, `export_application_bundle`, `rollback_application_deploy`, `pull_application_from_tree` |
| Functions/events | `list_functions`, `get_function`, `invoke_bff`, `invoke_tree_function`, `fire_event`, `list_events`, `list_event_catalog`, `get_event_schema` |
| Древовидные функции | `get_function_template`, `deploy_tree_function` (скрипт **или Java**), `invoke_tree_function`; приложение BFF: `deploy_app_function` (скрипт) |
| Operator HMI | `configure_operator_ui` |
| Platform | `list_platform_schedules`, `configure_platform_schedule`, `resolve_timezone`, `export_haystack` |
| Models | `list_relative_models`, `list_instance_types`, `list_absolute_models`, `get_object_model`, `apply_relative_model`, `instantiate_instance_type`, `ensure_absolute_instance`, `create_virtual_device` |
| Recipes | `search_platform_recipes`, `get_automation_schema topic=recipes|projects|recipe/{id}|projectBlueprint|instanceTypes` |

Документы: [DASHBOARDS.md](dashboards.md), [DRIVERS.md](drivers.md), [BINDINGS.md](bindings.md), [PLATFORM_LOGIC.md](platform-logic.md), [AUTOMATION.md](automation.md).

---

## B. Консоль администратора (ручная сборка)

**Суть:** инженер собирает решение в веб-консоли без агента и без пакета.

| Задача | Пользовательский интерфейс |
|--------|-----|
| Модель устройства | Проводник → Модели → Редактор моделей |
| Устройство | Проводник → Устройства → + Объект, Инспектор → Драйвер |
| HMI | Dashboard Builder (Редактор → виджеты/Правила) |
| Привязки / CEL | Инспектор → Привязки |
| БПМН | Workflow Builder (+ отмена/активный экземпляр) |
| Operator shell | `root.platform.operator-apps` → Operator Apps Panel |
| Развернуть приложение (пакет) | `APPLICATION` → Инспектор → **Развертывание** → Пакет + история + откат |
| Жизненный цикл приложения (REST D) | `APPLICATION` → Развертывание → **Жизненный цикл приложения** (миграция/заполнение/статус, привязки, отчеты, развертывание функций) |
| Platform schedules (DB) | **System → App schedules** (`GET/POST /api/v1/schedules`) |
| Семантический экспорт | **Система → Семантический экспорт** (Haystack JSON, Brick JSON-LD/Turtle) |
| Федерация связывает | Инспектор → Федерация; пиры → FederationPeersPanel |
| Timezone (device) | Inspector DEVICE → resolved TZ badge (`GET /platform/timezone/resolve`) |
| User timezone | Header → TimezoneSwitcher (`PATCH /auth/me/timezone`) |

Документы: [WEB_CONSOLE.md](web-console.md), [OPERATOR_GUIDE.md](operator-guide.md).

---

## Веб-консоль ↔ API платформы (версия **0.9.60**, июнь 2026 г.)

**Четность ~100%** для администратора/оператора наблюдается. Детальный реестр: [ROADMAP.md § Часть I](roadmap.md).

### Application platform (`/api/v1/applications/{appId}/…`)

| API | Пользовательский интерфейс |
|-----|-----|
| `POST /applications` | CreateObjectDialog (+ APPLICATION) |
| `POST …/deploy`, rollback, history | ApplicationBundlePanel, ApplicationDeployPanel |
| `GET …/export`, validate, pull-from-tree | ApplicationBundlePanel |
| `POST …/data/migrate`, `…/data/seed`, `GET …/data/status` | ApplicationLifecyclePanel |
| `GET/POST …/bindings/*` | ApplicationLifecyclePanel |
| `GET/POST …/reports/deploy`, запуск/экспорт | ApplicationLifecyclePanel + манифест оператора (устаревший путь) |
| `POST …/functions/deploy`, версии, откат | ApplicationLifecyclePanel + ApplicationDeployPanel |
| `GET …/events` | ApplicationDeployPanel (event catalog) |
| `GET …/operator-ui`, `…/hmi-ui` | useOperatorAppsRegistry (fallback) |

### Системный администратор

| API | Пользовательский интерфейс |
|-----|-----|
| `GET/POST /api/v1/schedules` | System → App schedules |
| `GET …/platform/haystack/export`, `…/brick/export` | System → Semantic export |
| `GET/POST …/platform/backup/*` | System → Metrics → Platform backup |
| Наборы изменений, настройки времени выполнения, журналы | Вкладки SystemView |
| `GET /ai/models`, `/ai/provider` | AI Studio → Settings |

### Время выполнения/автоматизация

| API | Пользовательский интерфейс |
|-----|-----|
| `POST /workflows/instances/{id}/cancel\|signal` | WorkflowBuilder → Instance panel |
| `POST /federation/proxy/…/functions/invoke` | InvokeFunctionDialog (federated bind) |
| Federated variable write | Inspector Save → `PUT /objects/…/variables` (server proxy) |
| `POST /bff/invoke` | Operator manifest screens |
| Поиск по тегам Haystack | HaystackBindDialog (дашборд), HaystackMetadataPanel (устройство) |

### Намеренно без пользовательского интерфейса администратора (API/MCP/ops)

| API | Кто использует |
|-----|----------------|
| `POST /api/v1/ai/mcp` | Внешние MCP-клиенты (Cursor, SDK) |
| `GET /api/v1/platform/installation-id` | Диагностика / скрипты |
| Пошаговый REST D без пакета | Агент, CI, Curl — дублирует жизненный цикл приложения пользовательского интерфейса |

**Prod:** https://ispf.iot-solutions.ru — `0.9.60`, `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` (демо fixtures только после `vps-factory-reset.sh --fixtures`).

---

## C. Bundle Deploy (декларативный манифест)

**Суть:** один манифест JSON/ZIP описывает всё решение. **Target approach** для производства.

**Секции manifest** (см. ContextPack `bundleManifest.fields`):

| Секция | Назначение |
|--------|------------|
| `objects[]` | Узлы дерева (reconcile при redeploy) |
| `models[]` | Blueprints |
| `dashboards[]` | layout JSON на DASHBOARD paths |
| `workflows[]` | BPMN + triggers |
| `migrations[]` | SQL в app schema (не platform Flyway) |
| `functions[]` | Script functions → tree `{appId}.functions.*` |
| `bindings[]` | SQL bindings app schema → variables |
| `reports[]` | SQL reports |
| `schedules[]` | Periodic invoke_function |
| `alertRules[]` / `correlators[]` | Automation nodes |
| `events[]` | Event catalog |
| `operatorUi` | Operator HMI menu (preferred) |
| `operatorManifest` | **Legacy** — deprecated |

**API:**

- `POST /api/v1/applications/{appId}/deploy` — monolithic JSON
- `POST /api/v1/platform/packages/import` — ZIP (agent: `import_package`)
- Gates: `validate_bundle` → `dry_run_deploy` → import

**После deploy:** invoke через `POST /bff/invoke` или `objects/by-path/functions/invoke` по tree path; SQL через `sqlBinding('appId','var')`.

Документы: [APPLICATIONS.md](applications.md), [SOLUTION_DEVELOPER_PUBLIC_API.md](solution-developer-public-api.md), [SOLUTION_DEVELOPER_GUIDE.md](solution-developer-guide.md).

---

## Д. Пошаговый REST API

**Суть:** те же возможности, что и Bundle, но **части** — для скриптов и поэтапной поддержки.

```text
1. POST /applications          — register appId, schemaName, tablePrefix
2. POST .../data/migrate       — SQL migrations
3. POST .../data/seed          — demo seed profiles
4. GET  .../data/status        — applied migration version
5. POST .../functions/deploy   — script functions
6. POST .../bindings/deploy    — SQL → variable sync (+ refresh)
7. POST .../reports/deploy      — reports
8. POST .../deploy             — full bundle (или только недостающие секции)
9. PUT  /operator-apps/{id}/ui — operator menu
10. GET/POST /api/v1/schedules — platform_schedules (отдельно от tree SCHEDULE objects)
```

**Веб-консоль (0.9.62):** шаги 2–7 доступны в Инспекторе → ПРИЛОЖЕНИЕ → Развертывание → **Жизненный цикл приложения**; агент: `register_application`, `application_data_migrate`, `deploy_app_binding`, `deploy_app_function`, …

Документ: [APPLICATIONS.md](applications.md), [API.md](api.md).

---

## E. AI Studio (генерация → проверка → импорт)

**Суть:** LLM переменный манифест JSON; Ворота платформы проверяют до записей в БД.

1. AI Studio → «Bundle package» или цепочка инструментов агента
2. `validate_bundle` / `dry_run_deploy`
3. `import_package` (агент) или интерфейс «Опубликовать»

**Policy:** `generationPolicy` в ContextPack — allowed/forbidden artifacts.

Документ: [AI_DEVELOPMENT.md](ai-development.md), [0004](decisions/0004-ai-artifact-generation-gates.md).

---

## F. Справочные примеры

| Пример | идентификатор приложения | Назначение |
|---------|-------|------------|
| `examples/mes-reference/` | mes-reference | MES orders, BFF functions, workflows |
| `examples/lab-training/` | lab-training | Virtual lab, PF-15 exercises |
| `examples/demo-app/` | demo-app | Minimal bundle |
| `examples/warehouse-app/` | warehouse-app | Warehouse pattern |
| `examples/mini-tec/` | — | Bootstrap demo (fixtures) |
| Бутстрап `tank-farm-demo` | нефтебаза-демо | Анонимная SCADA mimic нефтебазы (fixtures) |
| Бутстрап `pipeline-scada` | трубопровод-Scada | Экранные формы РД-029 (15 мнемосхем, fixtures) |

Agent tools: `list_examples`, `get_example_bundle(appId)`.

Прохождения: [REFERENCE_MES_WALKTHROUGH.md](reference-mes-walkthrough.md), [LAB_TRAINING.md](lab-training.md), [REFERENCE_MINI_TEC_WALKTHROUGH.md](reference-mini-tec-walkthrough.md).

### Демо Bootstrap SCADA (фиксации, подход G)

При `ispf.bootstrap.fixtures-enabled=true` сервере создаются готовые мнемосхемы и HMI. **Не используйте реальные названия компаний** в демо-текстах и ​​путях.

| Демо | идентификатор приложения | Устройства | Мимик | Панель управления |
|------|-------|------------|-------|-----------|
| Резервуарный парк | `tank-farm-demo` | `root.platform.devices.tank-farm-demo.*` | `root.platform.mimics.tank-farm-demo` | `root.platform.dashboards.tank-farm-hmi` |
| СДКУ РД-029 | `pipeline-scada` | `root.platform.devices.pipeline-scada.*` | `root.platform.mimics.pipeline-rp` (+ 14 форм `pipeline-*`) | `root.platform.dashboards.pipeline-scada-hmi` |
| Mini-TEC SLD | — | mini-tec devices | `root.platform.mimics.mini-tec-single-line` | `root.platform.dashboards.mini-tec-single-line` |

**Код и реэкспорт:**

| Демо | TypeScript | Экспорт |
|------|------------|--------|
| tank-farm | `apps/web-console/src/scada/templates/buildTankFarmMimic.ts` | `npx tsx src/scada/templates/exportTankFarmMimic.ts` |
| pipeline-scada | `apps/web-console/src/scada/templates/pipeline-scada/` | `npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts` |

Java bootstrap: `TankFarmPlatformBootstrap`, `PipelineScadaPlatformBootstrap`. Playbook: `AgentPlaybooks.scadaMimicGuide()`.

Operator URL: `?mode=operator&app=tank-farm-demo&dashboard=root.platform.dashboards.tank-farm-hmi`.

**Примечание:** путь `root.platform.mimics.tank-farm-demo` при одновременном бутстрапе `pipeline-scada` может быть перезаписан диаграммой РП (устаревший псевдоним). Для полного СДКУ используйте `pipeline-rp` + `pipeline-scada-hmi`.

---

## G. Платформа HMI без схемы приложения

**Суть:** мониторинг **без** отдельного appId/SQL — только дерево платформы.

- Устройства + драйверы + правила привязки + дашборды
- Demo rules on `snmp-host-monitoring` (Platform rules, `@dashboardContext`)
- Operator: `?mode=operator&app=platform`

Не нужны: регистрация приложения, миграции, функции пакета — если нет прикладных таблиц.

---

## H. Коммерческий пакет

Signed manifest + `license` block. Deploy через тот же import с проверкой RSA.

Документ: [COMMERCIAL_LICENSING.md](commercial-licensing.md), [0003](decisions/0003-commercial-bundle-licensing.md).

---

## Где выражать логику (не дублировать)

| Задача | Механизм | Документ |
|--------|----------|----------|
| Вычисление переменных | CEL / привязки платформы / правила привязки | [BINDINGS.md](bindings.md) |
| Пользовательский интерфейс дашборда (показать/скрыть, режим) | Правило платформы → `@dashboardContext` | [PLATFORM_LOGIC.md](platform-logic.md), [DASHBOARDS.md](dashboards.md) |
| Порог → событие | узел ALERT + CEL | [АВТОМАТИЗАЦИЯ.md](automation.md) |
| Шаблоны событий → рабочий процесс | Коррелятор | [АВТОМАТИЗАЦИЯ.md](automation.md) |
| Процесс с задачами оператора | РАБОЧИЙ ПРОЦЕСС BPMN | [WORKFLOWS.md](workflows.md) |
| CRUD по схеме приложения SQL | Функция сценария (шаги) | [APPLICATIONS.md](applications.md), [OBJECT_FUNCTIONS.md](object-functions.md) |
| SQL → опрос переменных | sqlBinding/привязки[] | [APPLICATIONS.md](applications.md) |
| Телеметрия устройства | Драйвер + сопоставления точек | [DRIVERS.md](drivers.md) |
| Таблица HMI | Виджет дашборда `object-table` + `selectionKey` | [DASHBOARDS.md](dashboards.md), [WIDGETS.md](widgets.md) |
| Мнемосхема / P&ID | Объект `MIMIC` + виджет `scada-mimic` | [SCADA.md](scada.md) |
| Legacy mini-DSL на виджете | **Устарело** → Правила платформы | [PLATFORM_LOGIC.md](platform-logic.md) § наследие |

---

## Пользовательский интерфейс оператора — источник (приоритет)

1. `GET /operator-apps/{appId}/ui` —таблица + дерево `root.platform.operator-apps`
2. `operatorUi` в пакетном развертывании
3. Автоген из `dashboards[]` в комплекте
4. Legacy `public/operator-apps/{appId}.ui.json` (только для разработчиков)

URL: `?mode=operator&app={appId}&dashboard={path}`.

---

## Карта документации (полный индекс)

Используй `search_context` с `topic` или ключевыми словами из таблицы.

### Продукты и технологические решения

| Документ | тема / ключевые слова | Содержание |
|----------|------------------|------------|
| [PRODUCT.md](product.md) | продукт | Обзор продукта, сюжеты |
| [APPLICATION_PRINCIPLES.md](application-principles.md) | принципы применения | Свод P1–P10, Target approach, антипаттерны |
| [SOLUTION_DEVELOPER_GUIDE.md](solution-developer-guide.md) | решения, приложения | Жизненный цикл решения, 6 шагов |
| [SOLUTION_DEVELOPER_PUBLIC_API.md](solution-developer-public-api.md) | публичный API, пакет | Манифест стабильного контракта |
| [APPLICATIONS.md](applications.md) | приложения, BFF, бандлы | REQ-PF: развертывание, функции, SQL, расписания |
| [ГЛОССАРИЙ.md](glossary.md) | глоссарий | Термины |

### Дерево объектов и логика

| Документ | тема | Содержание |
|----------|-------|------------|
| [АРХИТЕКТУРА.md](architecture.md) | архитектура | Принципы, следствия |
| [OBJECT_MODEL.md](object-model.md) | объектная модель, особенности | Дерево, виды, API |
| [BLUEPRINTS.md](blueprints.md) | модели | Чертежи, templateId |
| [OBJECT_FUNCTIONS.md](object-functions.md) | функции | обработчики сценариев/Java, вызов |
| [BINDINGS.md](bindings.md) | крепления чел | CEL, крепления для платформы |
| [PLATFORM_LOGIC.md](platform-logic.md) | логика платформы, правила | Правило платформы, контекст дашборда |
| [VARIABLE_HISTORY.md](variable-history.md) | историк | Временной ряд |

### HMI и оператор

| Документ | тема | Содержание |
|----------|-------|------------|
| [DASHBOARDS.md](dashboards.md) | дашборды | Макет, клавиша выбора, вкладка «Правила» |
| [SCADA.md](scada.md) | scada, mimic | Мнемосхемы, MIMIC, привязки, редактор (выравнивание, изменение размера, привязка) |
| [SCADA_MIMIC.md](scada-mimic.md) | SCADA mimic | диаграммаJson v2, mock REST API |
| [WIDGETS.md](widgets.md) | виджеты | Каталог виджетов, поля JSON |
| [SPREADSHEET_WIDGET.md](spreadsheet-widget.md) | электронная таблица | ISPF(), конфигурация листа |
| [OPERATOR_GUIDE.md](operator-guide.md) | оператор | HMI, очередь работ |
| [WEB_CONSOLE.md](web-console.md) | веб-консоль | Интерфейс администратора |

### Автоматизация и интеграция

| Документ | тема | Содержание |
|----------|-------|------------|
| [АВТОМАТИЗАЦИЯ.md](automation.md) | автоматизация, рабочие процессы | Оповещения, корреляторы, события |
| [WORKFLOWS.md](workflows.md) | рабочие процессы | Расширения BPMN, ISPF |
| [MESSAGING.md](messaging.md) | обмен сообщениями, возможности | НАТС, WS, события |
| [ФЕДЕРАЦИЯ.md](federation.md) | федерация | Удаленные коллеги |
| [DRIVERS.md](drivers.md) | драйверы | 58 драйверов, конфиг |
| [ОТЧЕТЫ.md](reports.md) | отчеты | SQL-отчеты, экспорт |

### ИИ, развертывание, операции

| Документ | тема | Содержание |
|----------|-------|------------|
| [AI_DEVELOPMENT.md](ai-development.md) | ай, агент | Агент, ContextPack, MCP |
| **AGENT_KNOWLEDGE.md** (этот файл) | агент-знание | Подходы к приложениям, индекс |
| [API.md](api.md) | API | Конечные точки REST |
| [DEPLOYMENT.md](deployment.md) | развертывание | Докер, среда |
| [SECURITY.md](security.md) | безопасность | RBAC, Keycloak |
| [TESTING.md](testing.md) | тестирование | Тесты |
| [LOAD_TESTING.md](load-testing.md) | нагрузочный тест | Базовые показатели пропускной способности |
| [OBSERVABILITY.md](observability.md) | наблюдаемость | Prometheus, API диагностики, датчик метрик |
| [CLUSTER.md](cluster.md) | кластер | Мультиреплика, живая синхронизация ADR-0029, разветвление диагностики |

### Операции и среда выполнения платформы (для агента при загрузке/сортировке продуктов)

| Документ | Когда |
|----------|-------|
| [OBSERVABILITY.md](observability.md) | Скачок ЦП, отставание в очереди, Prometheus/OTLP, **Диагностика нагрузки** Пользовательский интерфейс |
| [CLUSTER.md](cluster.md) | Несколько реплик, рассинхронизация ОЗУ, блокировки драйверов, `cluster/diagnostics` |
| [LOAD_TESTING.md](load-testing.md) | Базовые показатели, тестовое устройство, сценарии нагрузочного тестирования |

**Загрузка диагностики (админ):** `GET /api/v1/platform/cluster/diagnostics` — все реплики; развернуть → темы, драйверы (`pressureScore`), задания. Проба метрики: `PUT /api/v1/platform/diagnostics/metrics-probe` — синхронизация с `root.platform.devices.platform-metrics-probe` (включать только на время теста).

### Справочные руководства

| Документ | тема |
|----------|-------|
| [LAB_TRAINING.md](lab-training.md) | лаборатория, обучение |
| [REFERENCE_MES_WALKTHROUGH.md](reference-mes-walkthrough.md) | меня |
| [REFERENCE_MES_DEFECT_WALKTHROUGH.md](reference-mes-defect-walkthrough.md) | мес, дефект |
| [REFERENCE_MES_OGP_EVENTS_WALKTHROUGH.md](reference-mes-ogp-events-walkthrough.md) | меня, огп |
| [REFERENCE_MINI_TEC_WALKTHROUGH.md](reference-mini-tec-walkthrough.md) | мини-тек |

### ADR (архитектурные решения)

| АДР | Тема |
|-----|------|
| [0001](decisions/0001-app-platform-boundary.md) | Платформа против решения |
| [0004](decisions/0004-ai-artifact-generation-gates.md) | AI проверяет gates |
| [0005](decisions/0005-tree-first-ai-agent.md) | Tree-first агент |
| [0006](decisions/0006-mcp-agent-tool-adapter.md) | МКП |
| [0010](decisions/0010-binding-rules-only.md) | Обязательные правила |
| [0019](decisions/0019-platform-rule-unification.md) | Правило платформы/дашборд |
| [0020](decisions/0020-time-and-timezones.md) | Время и часовые пояса (хранилище UTC, TZ пользователя/устройства) |
| [решения/README.md](decisions/readme.md) | Полный список АДР |

### Эволюция платформы (для контекста, не для генерации)

| Документ | Назначение |
|----------|------------|
| [PLATFORM_EVOLUTION.md](platform-evolution.md) | История создания |
| [ROADMAP.md](roadmap.md) | Дорожная карта |
| [ROADMAP.md](roadmap.md) | REQ-PF/FW статус, BL, спринты |

---

## search_context — рекомендуемые темы

| тема | Когда |
|-------|-------|
| `application-principles` | Target approach, P1–P10, «как создать приложение правильно» |
| `agent-knowledge` | Выбор подхода A–H, карта docs |
| `applications` | Bundle, BFF, migrations, functions |
| `public-api` | Контракт manifest |
| `solution` | Жизненный цикл solution developer |
| `dashboards` | Widgets, layout, platform rules |
| `scada` | Mimic diagrams, MIMIC objects, scada-mimic widget |
| `platform-logic` | Context, visibility, CEL rules |
| `bindings` | CEL, counterRate, refAt |
| `drivers` | SNMP, Modbus, virtual, MQTT |
| `workflows` | BPMN, instance cancel/signal |
| `automation` | Alerts, correlators |
| `semantic` | Haystack/Brick export, device metadata |
| `timezones` | User TZ, device TZ resolve (ADR-0020) |
| `web-console` | Admin UI, System tabs, Application lifecycle |
| `observability` | Metrics, diagnostics, probe device, Prometheus |
| `cluster` | Multi-replica, live sync, cluster health/diagnostics |
| `object-model` | Tree types, variables |
| `ai` | Agent tools, ContextPack |
| `all` | Широкий поиск по docChunks |

---

## Чеклисты для агента

### «Создание приложения/решения» (с SQL)

1. Уточнить appId, нужен ли пользовательский интерфейс оператора
2. `search_context topic=application-principles` + `topic=agent-knowledge`; `get_example_bundle` если похоже на MES/лаборатория
3. зарегистрироваться (или включить в пакете) → миграции → функции → объекты/дашборды.
4. `validate_bundle` → `dry_run_deploy` → `import_package`
5. `configure_operator_ui`, если нет в манифесте
6. `finish` с `?mode=operator&app=...` и путями приборной панели

### «Создай мониторинг / SNMP / дашборд» (без схемы приложения)

1. Схема «сначала дерево» (SNMP/виртуальный кластер)
2. Драйвер + шаблон приборной панели
3. Правила платформы при необходимости (режим детализации, видимость виджета)
4. `configure_operator_ui` для приложения платформы

### «Добавь автоматизация»

1. `get_automation_schema`
2. Hub CUSTOM + `create_variable` или оповещение на DEVICE
3. `configure_alert` / `configure_correlator`
4. Необязательно: РАБОЧИЙ ПРОЦЕСС + `operatorAppId`

### «Создай / обнови мнемосхему» (scada.md)

1. `get_automation_schema topic=scada` или `list_mimic_symbols`
2. `create_object` тип=MIMIC под `root.platform.mimics`
3. **`save_mimic_diagram`** с непустым `elements[]` (бак, клапан, этикетка, труба…) — обязательно
4. `get_mimic_diagram` — проверка `elementCount > 0`
5. `list_variables` на устройствах → привязки в символах
6. ПАНЕЛЬ + `add_dashboard_widget type=scada-mimic mimicPath=...`
7. **Анонимизация:** без отдельных компаний, ФИО, гео-меток в демо.

### «Не ломай платформа» (см. [APPLICATION_PRINCIPLES.md](application-principles.md) P2, P10)

- Не изобретать пути REST — только инструменты
- Импорт пакета только после проверки + Dry_run OK в том же запуске
- Нет платформы Flyway для столов приложений.
- Prefer `operatorUi` over legacy `operatorManifest`

---

## ContextPack и ресурсы MCP

- Build: `python tools/ai-pack/build.py` → `ai/context-pack.json`
- MCP: `contextpack://doc-chunks`, `contextpack://feature-index`, `contextpack://example-summaries`
- Briefing: `PlatformBriefingService` — drivers, examples, live tree snapshot

---

*Обновлять при добавлении новых подходов (ADR, REQ-PF), расширении UI веб-консоли↔API и при пересборке docChunks (`python tools/ai-pack/build.py`).*

**Последнее обновление знаний:** 30 июня 2026 г. — четность UI↔API ~100 % (жизненный цикл приложения, расписания платформы, семантический экспорт, управление экземплярами рабочего процесса, вызов прокси-сервера федерации, разрешение TZ устройства); прод **0.9.60**.
