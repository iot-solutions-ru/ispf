> **Язык:** русская версия (вычитка). Канонический английский: [en/agent-knowledge.md](../en/agent-knowledge.md).

﻿# AGENT_KNOWLEDGE — справочник внутреннего агента ISPF

> **Статус:** Internal — Карта маршрутизации агента. Теги: [doc-status](../en/doc-status.md).

Документ для **tree-first агента**, AI Studio и MCP-клиентов. Описывает **все подходы к созданию приложений/решений** и предоставляет **карту документации** платформы.

**Как читать:** `search_context(query=..., topic=...)` → полный текст срезов в ContextPack. Этот файл — **маршрутизатор**: что выбрать и куда смотреть дальше.

См. также [application-principles](application-principles.md) (канонический свод P1–P10), [ai-development](ai-development.md), [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md), [0005-tree-first-ai-agent](decisions/0005-tree-first-ai-agent.md), [0051-poka-yoke-constraints-over-guards](decisions/0051-poka-yoke-constraints-over-guards.md).

---

## Target approach

Каноническая формулировка и развёрнутые правила: [application-principles](application-principles.md).

1. **Бизнес-логика — в конфигурации дерева объектов**, не на Java `ispf-server` и не в коде React платформы.
2. **Одна среда выполнения:** invoke, workflow, оповещения, дашборды, привязки — через **дерево объектов** и API платформы.
3. **Запись приложения** (таблица `applications`) — **реестр + изолированная SQL-схема**, не параллельный движок.
4. **Bundle Deploy** — доставка декларативной конфигурации в дерево; после развертывания всё адресируется деревом путей.
5. **Агент не пишет Java/React в `main`:** только проверенный JSON (пакет, макет, правила, модели) и вызовы инструментов платформы.

Запрещено: платформа Flyway для приложений-таблиц, hardcoded маршруты BFF, отраслевая Java на сервере.

---

## Подходы к созданию приложений (варианты AUTHOR / SHIP)

**Канон выбора — [application-principles P7](application-principles.md):** четыре слоя — **AUTHOR → SHAPE → SHIP → PROMOTE**, а не пять ровней «способов собрать приложение».

| Слой | Механизм | На этой странице |
|------|----------|------------------|
| AUTHOR | Admin UI или Agent | Строки **A**, **B**, **E** (черновик), **G** |
| SHAPE | Blueprint | [blueprints](blueprints.md); `models[]` в bundle |
| SHIP | Bundle | Строки **C**, **D**, **E** (import), **F**, **H** |
| PROMOTE | Change set | [collaboration](collaboration.md) § change-sets — не greenfield bootstrap |

A–H ниже — **детали tooling** под AUTHOR/SHIP. Сначала слой в P7, потом строка таблицы.

| # | Подход | Слой | Когда использовать | Доставка | Operator UI |
|---|--------|------|-------------------|----------|-------------|
| **A** | **Tree-first (agent tools / Explorer)** | AUTHOR | Демо, SNMP, lab, быстрый POC, интерактив с пользователем | `create_object`, `set_variable`, `configure_driver`, dashboard tools | `configure_operator_ui` |
| **B** | **Admin Console (ручная сборка)** | AUTHOR | Инженер без bundle, итеративный HMI | UI: Models, Dashboard Builder, Inspector | Панель Operator Apps |
| **C** | **Bundle deploy (manifest)** | SHIP | Production, CI/CD, повторяемый релиз | `POST .../applications/{id}/deploy` или `import_package` | `operatorUi` в манифесте |
| **D** | **Пошаговый REST API** | SHIP | Автоматизация без ZIP, поэтапная интеграция | register → migrate → functions → deploy sections | `PUT operator-apps/.../ui` |
| **E** | **AI Studio (generate → validate → import)** | AUTHOR→SHIP | Черновик манифеста из промпта | `validate_bundle` → `dry_run_deploy` → `import_package` | из сгенерированного `operatorUi` |
| **F** | **Reference example** | SHIP | Обучение, baseline MES/lab | `get_example_bundle` → adapt → import | из example-манифеста |
| **G** | **Platform HMI only** | AUTHOR | Только мониторинг, без app schema | dashboards + binding rules на дереве | встроенное `platform` operator app |
| **H** | **Commercial bundle** | SHIP | Лицензируемое решение | signed bundle + license gate | как у C |

### Дерево решений (кратко — зеркало P7)

```
Нужна изолированная app SQL и/или повторяемый релиз?
  ├─ ДА → SHIP: C/D/E/F/H (+ migrations[], если SQL)
  └─ НЕТ → AUTHOR: A/B/G (tree-only; SHAPE через blueprints для типизированных объектов)

Структура типизированного объекта (variables/events/functions)?
  └─ SHAPE → blueprint apply / models[] — не копировать руками каждый раз

Промоут / ревью уже созданных ops?
  └─ PROMOTE → change set preview → apply (не greenfield)

Интерактив AI Studio без SQL/CI?
  └─ AUTHOR A предпочтителен; если потом ship bundle — gates до import
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

1. Выбрать вид объекта логики: **SINGLETON**-оркестратор (один) или **INSTANCE**-twin (много) — **никогда** `ObjectType.DEVICE`
2. Создать хаб: `ensure_singleton_instance` / create под `singleton-blueprints`, **или** `instantiate_instance_type` для twin (может быть родителем под `devices.{plant}`)
3. DEVICE-дети для I/O: `create_object` / `create_virtual_device` под хабом или `devices.{project}`
4. `configure_driver` + `driver_control start` (если DEVICE)
5. `configure_variable_history` (для диаграммы/спарклайна)
6. `create_variable` / binding rules на **non-DEVICE** хабе (`create_binding_rule`)
7. `configure_alert` / `configure_correlator`
8. `create_object DASHBOARD` → `set_dashboard_layout template=...` или `add_dashboard_widget`
9. Правила платформы (ADR-0019): binding rules на DASHBOARD с `target.kind=context`, `onContextChange`
10. `configure_operator_ui` — dashboard по умолчанию + меню
11. `list_variables` → `finish` с путями UI

**Жёсткое правило:** объект логики/хаба не типизировать как DEVICE. Путь под `devices` с DEVICE-детьми допустим; DEVICE = только I/O.

**Сборники в системной подсказке:** SNMP, виртуальный кластер, Modbus, MES, отчеты, виджеты, мнемосхема SCADA — см. `AgentPlaybooks.*`.

### Чертеж проекта (8 слоёв)

Полноценный проект на tree-first = **8 слоёв** (см. `get_automation_schema topic=projectBlueprint`):

| # | Слой | Путь | Инструменты |
|---|------|------|-------|
| 1 | Хаб (SINGLETON-оркестратор **или** INSTANCE-twin) | Предпочтительно `singleton-blueprints.{project}`; twin через Instance Type (можно под `devices`) | `ensure_singleton_instance` / `instantiate_instance_type`, binding rules |
| 2 | Устройства (I/O) | DEVICE-дети под хабом или `devices.{project}/*` | `instantiate_instance_type`, `apply_mixin_blueprint`, `create_virtual_device` |
| 3 | Dashboard | `root.platform.dashboards.{project}-*` | `set_dashboard_layout`, `add_dashboard_widget` |
| 4 | SCADA | `root.platform.mimics.{project}-*` | `save_mimic_diagram`, `get_mimic_diagram` |
| 5 | Alerts | `root.platform.alert-rules.{project}-*` | `configure_alert` |
| 6 | Correlators | `root.platform.correlators.{project}-*` | `configure_correlator` |
| 7 | Workflows | `root.platform.workflows.{project}-*` | `save_workflow_bpmn` |
| 8 | Reports | `root.platform.reports.{project}-*` | `configure_report` |

**Три вида blueprints:** `get_automation_schema topic=instanceTypes` → MIXIN (примесь), INSTANCE (повторяемый twin / типизированный объект с логикой), SINGLETON (уникальный оркестратор). Объект логики ≠ DEVICE.

**Каталог рецептов (1410):** `search_platform_recipes`, `get_automation_schema topic=recipes|projects|recipe/{id}`. **500** готовых отраслевых проектов (`project-{industry}-{archetype}`). Полный индекс: [agent-recipes](agent-recipes.md).

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

Документы: [dashboards](dashboards.md), [drivers](drivers.md), [bindings](bindings.md), [platform-logic](platform-logic.md), [automation](automation.md).

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

Документы: [web-console](web-console.md), [operator-guide](operator-guide.md).

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

**Prod:** ${ISPF_BASE_URL:-https://ispf.example.invalid} — `0.9.60`, `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` (демо fixtures только после `vps-factory-reset.sh --fixtures`).

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

Документы: [applications](applications.md), [solution-developer-public-api](solution-developer-public-api.md), [solution-developer-guide](solution-developer-guide.md).

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

Документ: [applications](applications.md), [api](api.md).

---

## E. AI Studio (генерация → проверка → импорт)

**Суть:** LLM переменный манифест JSON; Ворота платформы проверяют до записей в БД.

1. AI Studio → «Bundle package» или цепочка инструментов агента
2. `validate_bundle` / `dry_run_deploy`
3. `import_package` (агент) или интерфейс «Опубликовать»

**Policy:** `generationPolicy` в ContextPack — allowed/forbidden artifacts.

Документ: [ai-development](ai-development.md), [0004-ai-artifact-generation-gates](decisions/0004-ai-artifact-generation-gates.md).

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

Прохождения: [reference-mes-walkthrough](reference-mes-walkthrough.md), [lab-training](lab-training.md), [reference-mini-tec-walkthrough](reference-mini-tec-walkthrough.md).

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

Документ: [commercial-licensing](commercial-licensing.md), [0003-commercial-bundle-licensing](decisions/0003-commercial-bundle-licensing.md).

---

## Где выражать логику (не дублировать)

| Задача | Механизм | Документ |
|--------|----------|----------|
| Вычисление переменных | CEL / привязки платформы / правила привязки | [bindings](bindings.md) |
| Пользовательский интерфейс дашборда (показать/скрыть, режим) | Правило платформы → `@dashboardContext` | [platform-logic](platform-logic.md), [dashboards](dashboards.md) |
| Порог → событие | узел ALERT + CEL | [automation](automation.md) |
| Шаблоны событий → рабочий процесс | Коррелятор | [automation](automation.md) |
| Процесс с задачами оператора | РАБОЧИЙ ПРОЦЕСС BPMN | [workflows](workflows.md) |
| CRUD по схеме приложения SQL | Функция сценария (шаги) | [applications](applications.md), [object-functions](object-functions.md) |
| SQL → опрос переменных | sqlBinding/привязки[] | [applications](applications.md) |
| Телеметрия устройства | Драйвер + сопоставления точек | [drivers](drivers.md) |
| Таблица HMI | Виджет дашборда `object-table` + `selectionKey` | [dashboards](dashboards.md), [widgets](widgets.md) |
| Мнемосхема / P&ID | Объект `MIMIC` + виджет `scada-mimic` | [scada](scada.md) |
| Legacy mini-DSL на виджете | **Устарело** → Правила платформы | [platform-logic](platform-logic.md) § наследие |

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
| [product](product.md) | продукт | Обзор продукта, сюжеты |
| [application-principles](application-principles.md) | принципы применения | Свод P1–P10, Target approach, антипаттерны |
| [solution-developer-guide](solution-developer-guide.md) | решения, приложения | Жизненный цикл решения, 6 шагов |
| [solution-developer-public-api](solution-developer-public-api.md) | публичный API, пакет | Манифест стабильного контракта |
| [applications](applications.md) | приложения, BFF, бандлы | REQ-PF: развертывание, функции, SQL, расписания |
| [glossary](glossary.md) | глоссарий | Термины |

### Дерево объектов и логика

| Документ | тема | Содержание |
|----------|-------|------------|
| [architecture](architecture.md) | архитектура | Принципы, следствия |
| [object-model](object-model.md) | объектная модель, особенности | Дерево, виды, API |
| [blueprints](blueprints.md) | модели | Чертежи, templateId |
| [object-functions](object-functions.md) | функции | обработчики сценариев/Java, вызов |
| [bindings](bindings.md) | крепления чел | CEL, крепления для платформы |
| [platform-logic](platform-logic.md) | логика платформы, правила | Правило платформы, контекст дашборда |
| [variable-history](variable-history.md) | историк | Временной ряд |

### HMI и оператор

| Документ | тема | Содержание |
|----------|-------|------------|
| [dashboards](dashboards.md) | дашборды | Макет, клавиша выбора, вкладка «Правила» |
| [scada](scada.md) | scada, mimic | Мнемосхемы, MIMIC, привязки, редактор (выравнивание, изменение размера, привязка) |
| [scada-mimic](scada-mimic.md) | SCADA mimic | диаграммаJson v2, mock REST API |
| [widgets](widgets.md) | виджеты | Каталог виджетов, поля JSON |
| [spreadsheet-widget](spreadsheet-widget.md) | электронная таблица | ISPF(), конфигурация листа |
| [operator-guide](operator-guide.md) | оператор | HMI, очередь работ |
| [web-console](web-console.md) | веб-консоль | Интерфейс администратора |

### Автоматизация и интеграция

| Документ | тема | Содержание |
|----------|-------|------------|
| [automation](automation.md) | автоматизация, рабочие процессы | Оповещения, корреляторы, события |
| [workflows](workflows.md) | рабочие процессы | Расширения BPMN, ISPF |
| [messaging](messaging.md) | обмен сообщениями, возможности | НАТС, WS, события |
| [federation](federation.md) | федерация | Удаленные коллеги |
| [drivers](drivers.md) | драйверы | 58 драйверов, конфиг |
| [reports](reports.md) | отчеты | SQL-отчеты, экспорт |

### ИИ, развертывание, операции

| Документ | тема | Содержание |
|----------|-------|------------|
| [ai-development](ai-development.md) | ай, агент | Агент, ContextPack, MCP |
| **AGENT_KNOWLEDGE.md** (этот файл) | агент-знание | Подходы к приложениям, индекс |
| [api](api.md) | API | Конечные точки REST |
| [deployment](deployment.md) | развертывание | Докер, среда |
| [security](security.md) | безопасность | RBAC, Keycloak |
| [testing](testing.md) | тестирование | Тесты |
| [load-testing](load-testing.md) | нагрузочный тест | Базовые показатели пропускной способности |
| [observability](observability.md) | наблюдаемость | Prometheus, API диагностики, датчик метрик |
| [cluster](cluster.md) | кластер | Мультиреплика, живая синхронизация ADR-0029, разветвление диагностики |

### Операции и среда выполнения платформы (для агента при загрузке/сортировке продуктов)

| Документ | Когда |
|----------|-------|
| [observability](observability.md) | Скачок ЦП, отставание в очереди, Prometheus/OTLP, **Диагностика нагрузки** Пользовательский интерфейс |
| [cluster](cluster.md) | Несколько реплик, рассинхронизация ОЗУ, блокировки драйверов, `cluster/diagnostics` |
| [load-testing](load-testing.md) | Базовые показатели, тестовое устройство, сценарии нагрузочного тестирования |

**Загрузка диагностики (админ):** `GET /api/v1/platform/cluster/diagnostics` — все реплики; развернуть → темы, драйверы (`pressureScore`), задания. Проба метрики: `PUT /api/v1/platform/diagnostics/metrics-probe` — синхронизация с `root.platform.devices.platform-metrics-probe` (включать только на время теста).

### Справочные руководства

| Документ | тема |
|----------|-------|
| [lab-training](lab-training.md) | лаборатория, обучение |
| [reference-mes-walkthrough](reference-mes-walkthrough.md) | меня |
| [reference-mes-defect-walkthrough](reference-mes-defect-walkthrough.md) | мес, дефект |
| [reference-mes-ogp-events-walkthrough](reference-mes-ogp-events-walkthrough.md) | меня, огп |
| [reference-mini-tec-walkthrough](reference-mini-tec-walkthrough.md) | мини-тек |

### ADR (архитектурные решения)

| АДР | Тема |
|-----|------|
| [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md) | Платформа против решения |
| [0004-ai-artifact-generation-gates](decisions/0004-ai-artifact-generation-gates.md) | AI проверяет gates |
| [0005-tree-first-ai-agent](decisions/0005-tree-first-ai-agent.md) | Tree-first агент |
| [0006-mcp-agent-tool-adapter](decisions/0006-mcp-agent-tool-adapter.md) | МКП |
| [0010-binding-rules-only](decisions/0010-binding-rules-only.md) | Обязательные правила |
| [0019-platform-rule-unification](decisions/0019-platform-rule-unification.md) | Правило платформы/дашборд |
| [0020-time-and-timezones](decisions/0020-time-and-timezones.md) | Время и часовые пояса (хранилище UTC, TZ пользователя/устройства) |
| [решения/README.md](decisions/readme.md) | Полный список АДР |

### Эволюция платформы (для контекста, не для генерации)

| Документ | Назначение |
|----------|------------|
| [platform-evolution](platform-evolution.md) | История создания |
| [roadmap](roadmap.md) | Дорожная карта |
| [roadmap](roadmap.md) | REQ-PF/FW статус, BL, спринты |

---

## search_context — рекомендуемые темы

| тема | Когда |
|-------|-------|
| `application-principles` | Target approach, P1–P10, стек творения P7 |
| `poka-yoke` | ADR-0051: constraints вместо гвардов; схемы до native FC |
| `agent-knowledge` | Варианты AUTHOR/SHIP A–H под P7, карта docs |
| `applications` | Bundle, BFF, migrations, functions |
| `public-api` | Контракт manifest |
| `solution` | Жизненный цикл solution developer |
| `dashboards` | Widgets, layout, platform rules |
| `scada` | Mimic diagrams, MIMIC objects, scada-mimic widget |
| `platform-logic` | Context, visibility, CEL rules |
| `bindings` | CEL, counterRate, PlatformRef (`read`/`call`/`fire`) |
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
2. Non-DEVICE хаб: SINGLETON-оркестратор или INSTANCE-twin + `create_variable` / `create_binding_rule` (alert может смотреть телеметрию DEVICE)
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

### «Не ломай платформа» (см. [application-principles](application-principles.md) P2, P10)

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
