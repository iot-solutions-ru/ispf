> **Язык:** русская версия (вычитка). Канонический английский: [en/applications.md](../en/applications.md).

# Приложения на платформе (REQ-PF)

> **Статус:** Stable — Bundles, BFF, scheduler. Теги: [doc-status](doc-status.md).

Платформенный уровень для разработки прикладных решений **без Java-кода отрасли в `ispf-server`**. Соответствует [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md) и требованиям REQ-PF.

**Дорожная карта и статус REQ-PF:** [roadmap](roadmap.md) (Часть A).

## Обзор

| РЕК-ПФ | Возможность | API / компонент |
|--------|------------|-----------------|
| 01 | Application Function Runtime | `POST /api/v1/applications/{appId}/functions/deploy`, JSON script engine |
| 02 | Application Data Layer | `POST /api/v1/applications/{appId}/data/migrate` |
| 03 | Application Package Deploy | `POST /api/v1/applications/{appId}/deploy` (JSON body) |
| 04 | BPMN `invoke_function` | `WorkflowActionType.INVOKE_FUNCTION` |
| 05 | Platform Scheduler | `GET/POST /api/v1/schedules` |
| 06 | BFF Wire Gateway | `POST /api/v1/bff/invoke` |
| 07 | Model Registry Persistence | `model_definitions` + автосохранение при CRUD моделей |
| 10 | Workflow Cancel | `POST /api/v1/workflows/instances/{id}/cancel` |

## Регистрация приложений

```http
POST /api/v1/applications
Content-Type: application/json

{
  "appId": "myapp",
  "displayName": "My Application",
  "tablePrefix": "myapp_",
  "schemaName": "myapp"
}
```

## Миграция данных (REQ-PF-02)

SQL-скрипты приложения **не** используются на платформе Flyway. Развертывание через API в **изолированной схеме** (`schemaName`, по умолчанию `app_{appId}`):

```http
POST /api/v1/applications/myapp/data/migrate
Content-Type: application/json

{
  "version": "1.0.0",
  "scripts": [
    { "id": "items", "sql": "CREATE TABLE IF NOT EXISTS demo_item (...);" }
  ]
}
```

Повторный вызов с тем же `version` + `id` — **идемпотентен** (скрипт пропускается).

**Guard:** DDL не может создавать платформы-таблицы (`applications`, `workflow_*`, …). При непустом `tablePrefix` названия таблиц следует начинать с префикса.

```http
GET /api/v1/applications/myapp/data/status
```

### Семя (дым)

```http
POST /api/v1/applications/myapp/data/seed
Content-Type: application/json

{ "profile": "smoke-demo" }
```

Встроенный профиль `smoke-demo` — идемпотентные INSERT в генерических-таблицах (`demo_category`, `demo_item`, `demo_metric`). Повторный вызов пропускает уже применённые семенные блоки.

```http
GET /api/v1/applications/myapp/data/status
```

## Функции развертывания (REQ-PF-01)

Функции — JSON **скрипт** с шагами:

| Шаг | Назначение |
|-----|------------|
| `selectOne` | Одна строка SQL → var (`Map`) |
| `selectMany` | Список строк SQL → var (`List<Map>`) |
| `exec` | DML/DDL с `params` |
| `setVar` | Присвоение литерала или `${path}` |
| `buildRecord` | Сборка `Map` в var из `fields` (field mapping) |
| `map` | Преобразование списка: `source` + `fields` с контекстом `${item.*}` |
| `invoke_function` | Вызов другой deploy-функции; propagate `error_code` |
| `when`, `if` | Ветвление (`then` / `else`) |
| `readVariable` | Чтение поля переменной объекта (`objectPath: self`) |
| `jsonParse` | Разбор JSON-строки в поля |
| `setDriverTelemetry` | Запись driver-телеметрии |
| `instantiateModelIfMissing` | Создание объекта из модели |
| `cancel_workflows` | Отмена workflow instances |
| `failIfNull` | Выход с `error_code` / `error_message` |
| `failIfNotEquals` | Проверка значения var |
| `return` | Сборка output (`fields`); массивы через `${var}` |

Полные примеры скриптов/java/встроенных функций на объектах дерева: [object-functions](object-functions.md). Те же шаги выполняются в `sourceType=script` на объекте (Inspector) и при развертывании приложения.

Один invoke = одна JDBC-транзакция (откат при необработанном исключении). Вложенные `invoke_function` — до 8 уровней. Недопустимый скрипт → **400** при deploy.

```http
POST /api/v1/applications/myapp/functions/deploy
Content-Type: application/json

{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "myapp_ping",
  "version": "1",
  "descriptor": {
    "inputSchema": { "name": "in", "fields": [{"name": "orderId", "type": "STRING"}] },
    "outputSchema": {
      "name": "out",
      "fields": [
        {"name": "error_code", "type": "STRING"},
        {"name": "error_message", "type": "STRING"}
      ]
    }
  },
  "source": {
    "type": "script",
    "body": "{ \"steps\": [ ... ] }"
  }
}
```

При первом вызове функция дескриптора автоматически добавляется на объект.

Параметры в SQL: `"${input.orderId}"` или `"$input.orderId"`.

## Развертывание пакета (REQ-PF-03)

### Семверский контракт (БЛ-97)

`manifest.version` является **обязательным** и должно быть строгим значением `MAJOR.MINOR.PATCH` (например, `1.0.0`). Недопустимые значения отклоняются при анализе, проверке и развертывании.

Необязательные поля:

- `changelog` — top-level string shown in the solution catalog
- `metadata.changelog` — alternative location for the same text

При обновлении установленного приложения `POST …/bundle/validate` предупреждает о **major**-обновлениях версии (`1.x.x` → `2.0.0`), чтобы интеграторы проверили миграции и breaking changes operator UI.

API: `GET /api/v1/solutions/catalog`. Reference demo (MES, Warehouse, Building HVAC) устанавливаются из marketplace (`POST /api/v1/solutions/marketplaces/{marketplaceId}/listings/{slug}/install`).

Один запрос — регистрация, метаданные, миграции, функции, расписания:

```http
POST /api/v1/applications/myapp/deploy
Content-Type: application/json

{
  "version": "1.0.0",
  "displayName": "My Application",
  "tablePrefix": "",
  "schemaName": "myapp",
  "objects": [
    {
      "parentPath": "root.platform",
      "name": "myapp",
      "type": "CUSTOM",
      "displayName": "My Application"
    }
  ],
  "dashboards": [
    {
      "path": "root.platform.myapp.ops",
      "title": "Operator Board",
      "layoutJson": "{ \"columns\": 84, \"rowHeight\": 8, \"widgets\": [] }"
    }
  ],
  "workflows": [
    {
      "path": "root.platform.myapp.workflows.main",
      "bpmnXml": "<definitions ...>...</definitions>",
      "status": "ACTIVE"
    }
  ],
  "blueprints": [
    {
      "name": "my-device-v1",
      "description": "Custom device template",
      "type": "DEVICE",
      "targetObjectType": "DEVICE",
      "variables": []
    }
  ],
  "migrations": [ { "id": "...", "sql": "..." } ],
  "functions": [ ... ],
  "schedules": [
    {
      "scheduleId": "import-job",
      "enabled": true,
      "intervalMs": 60000,
      "actionType": "invoke_function",
      "action": {
        "objectPath": "root.platform.myapp.integration",
        "functionName": "myapp_importRecords"
      }
    }
  ]
}
```

Ответ: `{ "status": "OK", "applied": [...], "skipped": [...], "errors": [] }`.

## BFF (REQ-PF-06)

Универсальный шлюз для Operator UI:

```http
POST /api/v1/bff/invoke
Content-Type: application/json

{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "myapp_processRecord",
  "input": {
    "schema": { "name": "in", "fields": [{"name": "orderId", "type": "STRING"}] },
    "rows": [{ "orderId": "..." }]
  },
  "wireProfile": "ispf-operator-v1"
}
```

### Wire profile `ispf-operator-v1`

| Правило | Поведение |
|---------|-----------|
| Успех | `error_code === "OK"`, `error_message === ""` |
| Ошибка | `result` отсутствует |
| Таблица | `result` = массив строк (unwrap поля `rows`) |
| Подписи | `result_field_labels`: `description` поля output schema → profile map `ispf-operator-v1` → имя поля |

Ответ (scalar): `{ "error_code": "OK", "error_message": "", "result": { ... }, "result_field_labels": {...}, "wireProfile": "ispf-operator-v1" }`.

Ответ (таблица): `{ "error_code": "OK", "result": [ {...}, ... ], "result_field_labels": {...} }`.

## Расписания (REQ-PF-05)

```http
GET /api/v1/schedules
POST /api/v1/schedules
```

Тик каждые 5 с; действие `invoke_function` с JSON `{ objectPath, functionName, input? }`.

## BPMN invoke_function (REQ-PF-04)

Задача службы BPMN:

```xml
<ispf:serviceTask action="invoke_function"
  objectPath="root.platform.devices.demo-sensor-01"
  functionName="myapp_acknowledge"
  inputMap="deviceId=${workflow.deviceId}"
  outputMap="ackResult=result" />
```

## Отмена рабочего процесса (REQ-PF-10)

```http
POST /api/v1/workflows/instances/{instanceId}/cancel
Content-Type: application/json

{
  "reason": "incident",
  "detailJson": "{\"incidentId\":\"...\"}",
  "cancelledBy": "operator-1"
}
```

## Модели (REQ-PF-07)

Пользовательские модели сохраняются в `model_definitions` и восстанавливаются при старте.

## Права доступа

| Конечная точка | Роль |
|----------|------|
| `/applications/**`, `/schedules/**` (POST/PUT) | `admin` |
| `/applications/*/operator-ui`, `/applications/*/hmi-ui`, `/applications/*/operator-manifest` (GET) | `operator`, `admin` |
| `/bff/invoke`, `/workflows/instances/*/cancel`, `/applications/*/reports/*/run` | `operator`, `admin` |

## Привязки SQL (REQ-PF-08)

Декларативная переменная объекта синхронизации ← SQL в схеме приложения:

```http
POST /api/v1/applications/{appId}/bindings/deploy
{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variable": "readyCount",
  "query": "SELECT COUNT(*) AS value FROM demo_item WHERE status = 'ready'",
  "refresh": "on_schedule",
  "refreshIntervalMs": 30000,
  "valueField": "value"
}
```

| `refresh` | Поведение |
|-----------|-----------|
| `on_schedule` | Периодический poll (`ApplicationSqlBindingScheduler`) |
| `on_function_success` | После успешного invoke функции (опционально `triggerObjectPath` / `triggerFunctionName`) |
| `on_event` | После `EVENT_FIRED` на объекте (опционально фильтр по `triggerObjectPath` / событию) |

**Variable-level binding:** на переменной объекта можно задать `bindingExpression`:

```text
sqlBinding('warehouse-app', 'readyCount')
```

Платформа выполняет привязку SQL из таблицы `application_sql_bindings` для этого приложения и переменной (см. `ServerBindingEvaluator`).

Также deploy через bundle: `bindings[]` в manifest.

```http
GET  /api/v1/applications/{appId}/bindings
POST /api/v1/applications/{appId}/bindings/refresh
```

## Версии функции (REQ-PF-11)

```http
GET  /api/v1/applications/{appId}/functions?objectPath=&functionName=
POST /api/v1/applications/{appId}/functions/rollback
{ "objectPath", "functionName", "version": "2" }
```

Rollback поднимает `deployed_at` выбранной версии — `findLatest` снова её использует.

## Bundle deploy history and rollback (PF-03b)

Каждый `POST .../deploy` сохраняет snapshot manifest в `application_bundle_deployments`.

```http
GET  /api/v1/applications/{appId}/deploy/history
POST /api/v1/applications/{appId}/deploy/rollback
{ "version": "1.0.0" }
```

Откат повторно применяет сохранённый манифест (миграции пропускаются, функции применяются повторно).

## Экспорт и проверка пакета (туда и обратно)

```http
GET  /api/v1/applications/{appId}/export
GET  /api/v1/applications/{appId}/export?version=1.0.0
POST /api/v1/applications/{appId}/bundle/validate?dryRun=true|false
```

`export` возвращает активный (или указанный) манифест моментального снимка из `application_bundle_deployments`. Веб-консоль: вкладка **Deploy** на узле `APPLICATION` — редактор JSON, проверка, развертывание, таблица ресурсов (добавление/удаление в манифесте).

```http
POST /api/v1/applications/{appId}/bundle/pull-from-tree
{
  "sections": ["dashboards", "workflows"],
  "paths": ["root.platform.dashboards.demo"],
  "mergeActive": true
}
```

Собирает манифест из **дерева живых объектов** (визуальные группы + магазины приложений). `paths` — опционально, только проверенные узлы; без `paths` — все участники объединяют визуальные группы. `models[]` не занимается реверс-инжинирингом (остается из базового манифеста).

Дерево объектов Поддерево (JSON, `formatVersion: 1`): `GET /api/v1/platform/backup/export?rootPath=root.platform.devices.demo-sensor-01` — вкладка **Экспорт JSON** в Инспекторе.

## Отчёты (REQ-PF-12)

SQL-отчёты в схеме приложения: развертывание через пакет `reports[]` или `POST .../reports/deploy`, выполнение `POST .../reports/{id}/run`, экспорт CSV `GET .../reports/{id}/export`.

Подробнее: [reports](reports.md).

## Operator UI (дашборды из дерева)

Единая оболочка оператора: навигация по объектам `DASHBOARD` из дерева, те же виджеты, что и в Dashboard Builder (только для чтения).

Контракт `operatorUi`:

```json
{
  "appId": "platform",
  "title": "Platform HMI",
  "defaultDashboard": "root.platform.dashboards.snmp-host-monitoring",
  "dashboards": [
    { "path": "root.platform.dashboards.snmp-host-monitoring", "title": "SNMP Host Monitoring" }
  ]
}
```

Источники (по приоритету):

1. `GET /api/v1/operator-apps/{appId}/ui` — встроенные и настроенные в админке приложения (таблица `operator_app_ui`, узел дерева `root.platform.operator-apps`)
2. Поле `operatorUi` в deploy bundle → `GET /api/v1/applications/{appId}/operator-ui`
3. Автогенерация из `dashboards[]` в bundle (path + title)
4. Legacy fallback: `public/operator-apps/{appId}.ui.json` (только dev)

URL: `?mode=operator&app=platform&dashboard=<path>` (JAR `:8080`, Vite dev `:5173`).

### Legacy: operator manifest

Поле `operatorManifest` в deploy bundle → `GET /api/v1/applications/{appId}/operator-manifest` — **deprecated** (таблицы/отчёты через BFF). Для новых приложений используйте `operatorUi` + dashboards.

Web Console: API first, fallback `public/operator-apps/<appId>.manifest.json` (legacy shell only).

### Объекты дерева

После register/deploy сущности приложения синхронизируются в `root.platform.applications.{appId}`:

| Папка | Содержимое |
|-------|------------|
| `reports` | SQL-отчёты (`ObjectType.REPORT`) |
| `functions` | Deployed script functions |
| `schedules` | `platform_schedules` |
| `bindings` | SQL bindings |
| `migrations` | Применённые data migrations |
| `screens` | Экраны operator manifest (legacy) |

Синхронизация: при развертывании/регистрации/переносе и при запуске сервера.

**Пример:** `appId=platform` настраивается в admin: дерево → `root.platform.operator-apps`. Deploy-приложения — `operatorUi` в bundle.

## Связанная документация

- [api](api.md) — таблица endpoints
- [workflows](workflows.md) — BPMN `invoke_function`, отмена instances
- [web-console](web-console.md) — BPMN editor и auto-layout
- [roadmap](roadmap.md) — REQ-PF, sprint roadmap
- [plugins](plugins.md) — граница core vs commercial plugins
- [security](security.md) — матрица RBAC

## Deprecation path (PF-03, Phase 5.5)

Таблица `applications` и REST `/api/v1/applications/*` — **только metadata и schema isolation** (реестр приложений, SQL migrations, deploy history). Не параллельный runtime.

После `POST /api/v1/applications/{appId}/deploy`:

- Функции адресуются через object tree: `{appId}.functions.{name}` на path узла приложения.
- Объекты, дашборды, рабочие процессы, модели из бандла — узлы дерева (`objects[]` согласовывать, а не только создавать).
- Invoke: `POST /api/v1/bff/invoke` или `POST /api/v1/objects/by-path/functions/invoke` по tree path.
- SQL bindings: `bindingExpression: sqlBinding('appId','var')` на переменной узла.

**Legacy (deprecated, warn-only):** operator manifest `screens[]` in bundle — Phase 3.5; предпочитайте `operatorUi` + dashboards в дереве. Legacy manifest screen types: `table`/`report` (BFF), `dashboard` (embedded DASHBOARD path), `chart` (single-variable trend), `map` (child devices). См. [solution-developer-guide](solution-developer-guide.md).

## Next steps (backlog)

Фаза 17 и Фаза 19 закрыты. Текущая волна — **[ROADMAP.md § Phase 18](roadmap.md)**:

- **18.1** Администратор Playwrightа e2e (Explorer, оператор глубокой ссылки) — хвост Фаза 3.4
- **18.2** Продвижение заглушек драйверов (по требованию)

Планирование спринта: [roadmap](roadmap.md).
