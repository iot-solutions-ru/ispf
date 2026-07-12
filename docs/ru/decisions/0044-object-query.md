# ADR-0044: Object Query (OQ)

## Статус

**Принято** (2026-07-12)

## Контекст

ISPF нужны **кросс-объектные табличные чтения** по дереву платформы (инвентарь устройств, строки SNMP ifTable, join родитель/потомок) без SQL и без отдельного каталога `ObjectType.QUERY`. Legacy tree-scan хранил фрагменты спецификации в переменных QUERY-объектов и отдавал `/api/v1/queries`.

## Решение

### 1. Хранение: только функции

Object Query хранится как **`FunctionDescriptor` с `sourceType=object-query`** и OQ JSON в `sourceBody`. Каталог `root.platform.queries` остаётся **FOLDER** объектов с функциями `run` (или с именем) типа object-query.

Вызов через существующий API функций:

```http
POST /api/v1/objects/by-path/functions/invoke?path={objectPath}&name=run
```

### 2. Спецификация OQ (v1)

```json
{
  "from": {
    "alias": "row",
    "sourcePathPattern": "root.platform.devices.*",
    "objectTypes": ["DEVICE"],
    "filter": "displayName != \"\"",
    "expand": { "variable": "ifTable", "rowKey": "ifIndex", "filter": "row.ifOperStatus == 2" }
  },
  "joins": [
    { "alias": "parent", "type": "left", "on": { "kind": "parent" } }
  ],
  "fields": [
    { "name": "path", "source": "path", "alias": "row" },
    { "name": "temp", "ref": "{row}/temperature/value" }
  ],
  "orderBy": [{ "field": "path", "dir": "asc" }],
  "limit": 1000,
  "offset": 0,
  "having": "temp > 0"
}
```

**Типы join (v1):** `parent`, `ancestor`, `ref`, `pathPrefix`, `eq`, `sameObject`, `lookup`, `pathSubstring`.

**Record expand:** `from.expand` разворачивает multi-row переменную (`DataRecord`) в по одной строке результата на строку записи; ref `{row}/field` адресует поля строки expand.

### 3. Runtime

- [`ObjectQueryService`](../../../packages/ispf-server/src/main/java/com/ispf/server/query/ObjectQueryService.java) — scan, join, project, sort, paginate, скалярные агрегаты.
- [`ObjectQueryFunctionHandler`](../../../packages/ispf-server/src/main/java/com/ispf/server/function/ObjectQueryFunctionHandler.java) — `@Order(-2)`, возвращает `rows` (JSON string) + `rowCount`.
- [`ObjectQueryCatalog`](../../../packages/ispf-server/src/main/java/com/ispf/server/query/ObjectQueryCatalog.java) — bootstrap папки `root.platform.queries`.

### 4. Platform bindings

| Binding | Назначение |
|---------|------------|
| `queryScalar(spec, aggregate[, field])` | Реактивный KPI (`count`, `sum`, `avg`, `min`, `max`, `first`) |
| `queryRows(spec)` / `executeQuery(spec)` | Полная таблица как JSON string |
| `countScan(pattern)` / `sumScan(pattern, field)` | Sugar для tree-scan |
| `write(ref, value)` | Writeback в удалённое поле переменной |

`spec` — inline JSON или ref `@/variable/value`.

### 5. Шаги скрипта

Шаги `FunctionScriptEngine`: `queryRows` / `scan_objects`, `for_each_row`, `apply_query_patch` (через `ObjectQueryService` / `PlatformRefExecutor.write`).

### 6. Удаление legacy

`ObjectType.QUERY`, `query-v1`, `/api/v1/queries/*` и хранение tree-scan в переменных **удалены**. Определения запросов — только `CUSTOM` под `root.platform.queries` с функцией `run` (`sourceType=object-query`).

### 7. Расширения после v1 (реализовано)

- **Patch writeback** при invoke через `patch` / `patches` на входе `run`
- `groupBy` + reducer-агрегаты в движке
- Historian-колонка: `{"ref": "{row}/temperature", "historian": {"fn": "avg", "window": "15m"}}`
- Introspection variables: `{"source": "variables", "alias": "row"}` → отсортированный список имён переменных
- Join `lookup` и `pathSubstring` в [`ObjectQueryJoinResolver`](../../../packages/ispf-server/src/main/java/com/ispf/server/query/oq/ObjectQueryJoinResolver.java)

Осталось: v1 UI готов — редактор spec в Web Console (`ObjectQuerySpecField`, интеграция в expression builder).

## Последствия

- Единый путь invoke для таблиц и KPI; bindings остаются только скалярными/reactive.
- Агенты и UI хранят spec в `run.sourceBody` и ссылаются через `@/…/value`.
- Существующие деплои должны мигрировать QUERY → CUSTOM + `run` до апгрейда (Flyway `V80__retire_object_query_type.sql` перетипизирует строки в БД).
