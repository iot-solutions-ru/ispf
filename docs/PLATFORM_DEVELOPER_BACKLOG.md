# Спецификация для разработчика платформы ISPF

**Цель:** generic-слой в **iot-solutions-platform-framework**, чтобы прикладные решения разворачивались **только через deploy API**, без:

- отраслевого Java-кода в `ispf-server`;
- Flyway-миграций таблиц приложений в platform repo;
- дополнительных industry plugin-JAR в дереве `main`.

**Решение проекта:** ADR-0008, ADR-0009 (app/platform boundary, dogfooding через reference apps вне `main`).

**Аудитория:** разработчики ISPF (framework).  
**Версия платформы (baseline):** `main`, миграция `V10__application_platform.sql`, лицензия Apache 2.0, docs [APPLICATIONS.md](APPLICATIONS.md), [PLUGINS.md](PLUGINS.md).

---

## 0. Принцип обобщения

Развитие platform идёт через **dogfooding** с обязательным gate (ADR-0009):

```text
Потребность app-команды  →  REQ-PF (generic)  →  PR в platform main  →  bundle deploy  →  smoke
```

### Gate обобщения (обязателен для каждого PR в platform)

| # | Вопрос | Если «нет» |
|:---:|:---|:---|
| 1 | API работает для **любого** `appId`, без отраслевых имён в Java? | Оставить в app bundle |
| 2 | App-команда использует только **deploy REST**, без fork server? | Доработать API |
| 3 | Есть **второй** сценарий на том же API? | Переформулировать абстракцию |

### Запрещено vs правильно

| Запрещено в `main` | Правильное обобщение |
|:---|:---|
| Industry `*Handler` в server | `ApplicationFunctionHandler` + script deploy |
| Отраслевые BFF routes | `/bff/invoke` + wire profile |
| App tables в platform Flyway | `applications/{id}/data/migrate` |
| `@Scheduled` import jobs | `platform_schedules` |
| Hardcoded domain script steps | `selectMany` + `exec` + app SQL |

### Суть platform (не «модуль MES»)

| Столп ISPF | REQ-PF | Потребитель |
|:---|:---|:---|
| Callable logic on objects | PF-01 script runtime | app functions |
| App-owned data | PF-02 data layer | app schema |
| Package deploy | PF-03 bundle | app manifest |
| Process + operator tasks | PF-04, PF-10 workflow | app BPMN |
| Cron / integration tick | PF-05 scheduler | app schedules |
| HMI wire | PF-06 BFF gateway | operator manifest |
| Models survive restart | PF-07 | app models |
| Telemetry → HMI | PF-08, PF-09 bindings/simulator | demo dashboards |

**Критерий зрелости:** второй app (`warehouse`, `appId=wh`) собирается **тем же** API без нового Java в platform.

---

## 1. Definition of Done (platform)

Считаем REQ-PF закрытым на `main`, когда:

1. `POST /api/v1/applications/{appId}/deploy` поднимает metadata + migrations + functions + objects/dashboards/workflows из bundle.
2. Operator UI работает через `POST /api/v1/bff/invoke` и `operatorManifest` — без отраслевых маршрутов в server.
3. App-функции — **script/deploy**, не `FunctionHandler` в platform repo.
4. Расписания — `platform_schedules`, не `@Scheduled` в Java.
5. В `main` **нет** отраслевого кода и BFF (чеклист [PLUGINS.md](PLUGINS.md)).
6. `ApplicationPlatformApiTest` покрывает generic platform API.

---

## 2. Архитектурный принцип

```text
┌─────────────────────────────────────────────────────────────┐
│  ISPF platform (framework) — реализует ОДИН РАЗ            │
│  • Application Function Runtime (script engine)             │
│  • Application Data Layer (migrate API)                     │
│  • Bundle deploy, Scheduler, BFF gateway, BPMN hooks      │
│  • Generic simulators / SQL bindings (demo)                 │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ REST deploy (admin)
                              │
┌─────────────────────────────────────────────────────────────┐
│  App bundle (вне `main`) — БЕЗ Java в platform               │
│  • SQL migrations, JSON script functions                    │
│  • objects, dashboards, BPMN, schedules, operator manifest  │
│  • Web Console → /bff/invoke only                           │
└─────────────────────────────────────────────────────────────┘
```

**Запрещено смешивать:** отраслевой код в `packages/ispf-server/` под видом «временного» reference.

**Допустимо в platform:** только **generic** механизмы (REQ-PF ниже), применимые к любому `appId`.

---

## 3. Сводная матрица REQ-PF

| ID | Capability | Статус на `main` | Осталось сделать | P |
|:---|:---|:---|:---|:---:|
| **PF-01** | Application Function Runtime | **Частично** | `map`/`buildRecord` (PF-01c), regression parity | P0 |
| **PF-02** | Application Data Layer | **Готово** | — | P0 |
| **PF-03** | Application Package Deploy | **Частично** | models в bundle (P2) | P1 |
| **PF-04** | BPMN `invoke_function` | **Готово** | Regression tests с app functions | P1 |
| **PF-05** | Platform Scheduler | **Готово** | — | P1 |
| **PF-06** | BFF Wire Gateway | **Частично** | Custom field labels map (P2) | P1 |
| **PF-07** | Model Registry Persistence | **Готово** | — | P2 |
| **PF-08** | Variable ↔ SQL sync | **Да** | Declarative bindings (§4.5) | P2 |
| **PF-09** | Integration Simulator SPI | **Да** | virtual driver profiles (§4.6) | P2 |
| **PF-10** | Workflow cancel + signal | **Готово** | — | P1 |
| **PF-11** | Function rollback / versions | **Да** | Deploy previous version (§4.8) | P2 |
| **PF-12** | Application SQL reports | **Да** | PDF export (out of scope) | P2 |

---

## 4. Требования к реализации (детально)

### 4.1 REQ-PF-01 — Application Function Runtime (+ расширения)

**Уже есть (`main`):**

- `POST /api/v1/applications/{appId}/functions/deploy`
- `POST /api/v1/applications/{appId}/deploy` (functions в bundle)
- `ApplicationFunctionHandler` + `FunctionScriptEngine`
- Шаги script: `selectOne`, `exec`, `failIfNull`, `failIfNotEquals`, `return`
- Audit: `function_invoke_audit`
- Invoke без restart: `POST .../functions/invoke`, `POST /api/v1/bff/invoke`

**Нужно доработать (P2):**

#### PF-01a — `selectMany` ✅

#### PF-01b — `invoke_function` ✅

#### PF-01c — `map` / `buildRecord`

Построение output row из vars (field mapping без copy-paste SQL).

#### PF-01d — `setVar` / literals

Присвоение констант и простых выражений в vars.

#### PF-01e — транзакция script ✅

Один invoke = одна JDBC transaction (rollback при необработанном exception).

**Acceptance PF-01:**

- [ ] Типовой набор app-функций реализуем **только** JSON script + SQL (без Java в platform).
- [x] Deploy идемпотентен; invalid script → 400 на deploy.
- [x] RBAC как у `functions/invoke`.
- [x] Документация: `docs/APPLICATIONS.md` § Script steps (полный список).

**Не принимается как решение для app-команды:** hot-load **отраслевого** JAR в platform repo.

---

### 4.2 REQ-PF-02 — Application Data Layer

**Уже есть:**

- `POST /api/v1/applications` — register `appId`, `tablePrefix`
- `POST .../data/migrate` — SQL scripts, idempotent by `(version, script_id)`
- `GET .../data/status`

**Нужно доработать (P0):**

#### PF-02a — изоляция schema ✅

- `schemaName` (default `app_{appId}`) + `SET SCHEMA` / `search_path` на время migrate/invoke/seed.
- Guard DDL: reserved platform tables + `tablePrefix` validation.

#### PF-02b — seed API ✅

```http
POST /api/v1/applications/{appId}/data/seed
{ "profile": "smoke-demo" }
```

Idempotent INSERT для smoke (`demo_category`, `demo_item`, `demo_metric`).

#### PF-02c — миграции в app bundle ✅

Миграции **не** в Flyway platform — только через `POST .../data/migrate` / bundle `migrations[]`.

**Acceptance PF-02:**

- [x] Fresh ISPF + app deploy → изолированная app schema.
- [x] Повтор migrate/seed — no duplicate rows.
- [x] Две app schema не пересекаются.

---

### 4.3 REQ-PF-03 — Application Package Deploy

**Уже есть:** bundle `migrations` + `functions` + `schedules`.

**Нужно (P1):**

Расширить `BundleManifest`:

```json
{
  "version": "1.0.0",
  "objects": [ { "parentPath", "name", "type", "displayName", "functions": [...] } ],
  "dashboards": [ { "path", "title", "layoutJson" } ],
  "workflows": [ { "path", "bpmnXml", "status" } ],
  "models": [ ... ]
}
```

- Идempotent deploy каждой секции.
- Ответ: `{ applied, skipped, errors }` по секциям.
- **PF-03b (P2):** rollback bundle version.

**Acceptance:** один `POST .../deploy` поднимает app metadata + logic.

---

### 4.4 REQ-PF-06 — BFF Wire Gateway

**Уже есть:** `POST /api/v1/bff/invoke` → `{ error_code, error_message, result }`.

**Нужно (P1):**

#### PF-06a — wire profile `anima-operator-v1` ✅

По контракту wire profile `anima-operator-v1` (см. [APPLICATIONS.md](APPLICATIONS.md)):

| Правило | Реализация |
|:---|:---|
| Успех | `error_code === "OK"`, `error_message === ""` |
| Ошибка | фронт не читает `result` |
| Таблица | `result` = массив строк напрямую |
| Подписи | `result_field_labels` из descriptor функции или profile map |

```http
POST /api/v1/bff/invoke
{ "wireProfile": "anima-operator-v1", ... }
```

**Acceptance:** Web Console operator — manifest shell + `POST /api/v1/bff/invoke` (без отраслевых BFF routes в server).

---

### 4.5 REQ-PF-08 — Variable ↔ SQL / Event Sync

**Нужно (P2 — demo dashboards):**

Declarative binding на object variable:

```json
{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variable": "readyCount",
  "binding": {
    "type": "sql",
    "appId": "myapp",
    "query": "SELECT COUNT(*) AS value FROM demo_item WHERE status = 'ready'",
    "refresh": "on_schedule|on_function_success"
  }
}
```

**Acceptance:** legacy dashboard KPI без Java `JdbcDemoSyncService`.

---

### 4.6 REQ-PF-09 — Integration Simulator SPI

**Нужно (P2 — demo dashboards / virtual driver):**

Расширение **virtual driver** или object profile `simulator`:

| Signal | Поведение |
|:---|:---|
| Meter | `litersPerSecond`, elapsed from `filling` status |
| Weighbridge | `tareKg + actualLiters * density` |
| Gas / grounding | boolean по rackId |

Deploy profile из app bundle; prod — замена на real driver **без** смены function contract.

**Acceptance:** snapshot meter и suggested gross weight без Java imitator.

---

### 4.7 REQ-PF-10 — Workflow Signal / Cancel

**Уже есть:**

- `POST /api/v1/workflows/instances/{instanceId}/cancel`
- `workflow_cancel_journal`
- `WorkflowInstanceCancelService.cancelWaitingByWorkflowPath` (internal)

**Нужно (P1):**

#### PF-10a — script step `cancel_workflows` ✅

```json
{
  "type": "cancel_workflows",
  "workflowPath": "root.platform.myapp.workflows.main",
  "statusIn": ["WAITING", "RUNNING"],
  "reason": "incident",
  "detail": { "incidentId": "${input.incidentId}" }
}
```

#### PF-10b (P2) — BPMN signal catch ✅

`intermediateCatchEvent` + `ispf:signal` — движок, API `POST .../instances/{id}/signal` и `POST .../workflows/signal`.

**Acceptance:** экземпляр в `WAITING` на signal catch продолжается после доставки сигнала.

---

### 4.8 REQ-PF-11 — Function versioning & rollback (P2)

```http
GET  /api/v1/applications/{appId}/functions?objectPath=&name=
POST /api/v1/applications/{appId}/functions/rollback
{ "objectPath", "functionName", "version": "2" }
```

---

## 5. Уже реализовано — не дублировать, только поддерживать

| Компонент | Где |
|:---|:---|
| BPMN `invoke_function` | `WorkflowActionType.INVOKE_FUNCTION`, `docs/WORKFLOWS.md` |
| Scheduler | `PlatformSchedulerService`, `platform_schedules`, `/api/v1/schedules` |
| Model persist | `model_definitions`, restore on startup |
| Generic BFF invoke | `BffController` `/api/v1/bff/invoke` |
| Platform tables V10 | `V10__application_platform.sql` |
| Smoke baseline | `ApplicationPlatformApiTest` |

---

## 6. Operator UI (контракт для platform)

Web Console (`apps/web-console`):

- **Только** `POST /api/v1/bff/invoke` + `wireProfile: anima-operator-v1`.
- В `main`: manifest shell (`?mode=operator&app=<appId>`), экраны описываются в `operatorManifest` app bundle.
- Platform **не** знает про конкретные экраны app — только wire + invoke.

---

## 7. Smoke checklist (platform QA)

Выполнить на H2/PostgreSQL без industry Java:

| # | Step | Expected |
|:---:|:---|:---|
| 1 | `POST /applications` | 200 |
| 2 | `POST .../deploy` bundle | status OK |
| 3 | `GET .../data/status` | migrations applied |
| 4 | `POST /bff/invoke` list function | error_code OK, result |
| 5 | `POST .../data/seed` smoke-demo | idempotent applied/skipped |
| 6 | `POST /schedules` invoke_function | tick fires |
| 7 | `POST .../workflows/.../run` | instance progresses |
| 8 | `POST .../workflows/.../signal` | WAITING instance resumes |
| 9 | `POST .../deploy/rollback` | previous bundle restored |
| 10 | Operator manifest UI | `bff/invoke` без 404 |

Automate: `ApplicationPlatformApiTest`, `WorkflowSignalApiTest`.

---

## 8. Приоритет реализации (roadmap)

```text
Sprint A (P0) — без этого app не стартует
  PF-01a selectMany
  PF-01b invoke_function
  PF-01e transaction
  PF-02a schema isolation
  PF-02b seed API

Sprint B (P1) — operator wire + bundle
  PF-06a wire profile
  PF-03 objects/dashboards/workflows in bundle
  PF-10a cancel_workflows script
  Web Console → generic bff ✅ (manifest shell)

Sprint C (P2) — demo parity
  PF-08 SQL bindings ✅
  PF-09 simulator SPI ✅ (virtual profiles)
  PF-11 function rollback ✅

Sprint D (P2) — polish
  PF-03b bundle rollback ✅
  ApplicationPlatformApiTest (generic platform API) ✅
  PF-10b BPMN signals ✅
```

---

## 9. Вне scope platform (`main`)

| Артефакт | Где |
|:---|:---|
| Доменная модель, UC/US, канон БД app | Репозиторий приложения |
| JSON script bodies, SQL migrations | App bundle deploy |
| BPMN процессов app | App bundle deploy |
| Operator UI экраны app | `operatorManifest` в bundle |
| E2E smoke конкретного заказчика | CI репозитория приложения |

Platform **не** содержит бизнес-правил отраслей — только исполнение deploy.

---

## 10. Связанные документы

| Документ | Где |
|:---|:---|
| Platform apps API | [APPLICATIONS.md](APPLICATIONS.md) |
| Platform developer backlog | этот файл |
| PLUGINS / main hygiene | [PLUGINS.md](PLUGINS.md) |
| Workflows | [WORKFLOWS.md](WORKFLOWS.md) |

---

## 11. История изменений

| Дата | Изменение |
|:---|:---|
| 2026-06-19 | Первая consolidated версия: статус `main` + gap PF-01a…11 |
| 2026-06-19 | Синхронизация в framework `docs/`; baseline Apache 2.0 |
