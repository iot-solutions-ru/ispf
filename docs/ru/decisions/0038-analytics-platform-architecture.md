> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0038-analytics-platform-architecture.md](../../en/decisions/0038-analytics-platform-architecture.md).

# ADR-0038: Архитектура аналитической платформы (AF-capable)

Статус: **Предложено** (09.07.2026)

## Контекст

Фаза 28 дала основу масштабирования historian (конфигурация tiers, dual-write, aggregate API, каталог AF-lite BL-160). Это покрывает **хранение и on-read rollups** для дашбордов, но не полноценный **calculation + OLAP plane** уровня OSIsoft PI Asset Framework.

Операторам нужно:

1. **Производные теги** как live-переменные (alarms, bindings, dashboards — одно значение).
2. **Предвычисленные rollups** для длинных трендов без повторной агрегации миллионов сэмплов на каждый chart.
3. **Мультитеговая аналитика** (ad-hoc запросы, цепочки KPI) на warm tier ClickHouse.
4. **Гибкая топология** — один JVM на малой площадке **или** кластер с разделением ролей без форка продукта.

Binding rules ([0010-binding-rules-only](0010-binding-rules-only.md)) остаются механизмом **реактивной логики на объекте**. Аналитические расчёты с окнами historian, DAG зависимостей и backfill — **отдельный движок**, пишущий результат обратно в дерево объектов (и NATS live sync в кластере).

## Решение

### 1. Слоистая модель

| Слой | Ответственность | Хранилища | Сейчас |
|------|-----------------|-----------|--------|
| **OLTP / tree** | Объекты, blueprints, RBAC, workflow | PostgreSQL | Да |
| **Historian ingest** | Append сэмплов, retention, tiers | PG/Timescale, CH, cold | Частично (BL-159) |
| **Calculation engine** | Derived tags, DAG, расписания, backfill | Stateless; читает historian, пишет vars | Нет (BL-203–204) |
| **OLAP rollups** | Материализованные buckets | ClickHouse MV / таблицы | Нет (BL-205) |
| **Query API** | Мультитеговые тренды, выражения | hot/warm/cold routing | Частично (single-tag REST) |

Дерево объектов — **source of truth для метаданных и live derived values**. ClickHouse — **analytics read plane** для warm/cold и rollup-таблиц.

### 2. Расширения object model (BL-209)

> **Поправка (ADR-0041, 2026-07-09):** Historian-вычисления — **`BindingRule` с `kind: historian`** в `@bindingRules`, а не объекты `ANALYTICS_TEMPLATE` в дереве. Путь тега = `objectPath#ruleId`; пресеты — код + [analytics-historian-cookbook](../analytics-historian-cookbook.md). Ниже — исходное предложение BL-209; каталог шаблонов и metadata vars `analytics-tag-v1` **устарели** для новых конфигураций.

- ~~Каталог `ANALYTICS_TEMPLATE` (`root.platform.analytics`)~~ → статические пресеты + binding rules
- Опционально **`ANALYTICS_TAG`** (или RELATIVE blueprint `analytics-tag-v1` на `DEVICE`):
  - `expression` / `helper` + `sourcePaths[]`
  - `schedule` (periodicMs, alignToWallClock)
  - `rollupBuckets[]`
  - `lineage` (upstream paths для impact analysis)

Haystack / Brick — semantic overlay ([0021-haystack-semantic-overlay](0021-haystack-semantic-overlay.md)); analytics metadata добавляется поверх.

### 3. Calculation engine (BL-203–204)

Новый пакет: `packages/ispf-analytics-engine` (библиотека в `ispf-server`; позже optional worker).

- **DAG** зависимостей (детекция циклов).
- Триггеры: **periodic**, **on source sample** (coalesced), **manual backfill** API.
- Функции v1: `rollingAvg`, `rateOfChange`, `totalizer`, `min`/`max` window, `oeeComposite`.
- Выход: `setVariableValue` + NATS fan-out ([0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md)).

**Не в v1:** полный PI Analytics DSL, ML inference (BL-175).

### 4. OLAP rollups (BL-205)

- ClickHouse materialized views или `variable_rollups`.
- Materializer на нодах с capability **`analytics`**.
- Dashboard / Analytics API предпочитает rollup при `range > hotCutoff`.

### 5. Analytics Query API (BL-206)

```
POST /api/v1/platform/analytics/query
```

- Body: `{ tags: [...], from, to, bucket, agg }`
- Auth: operator+; variable ACL при BL-154.

### 6. Replica profile `analytics` (BL-207)

Расширение [0032-replica-profiles-and-capabilities](0032-replica-profiles-and-capabilities.md):

| Profile | Capabilities |
|---------|----------------|
| `analytics` | `analytics`, `replica-sync`, `schedulers` |

- **Один сервер:** `unified` — движок in-process.
- **Prod L:** отдельные `analytics` pods + CH cluster.

### 7. Event frames (BL-208)

Лёгкие **временные окна** (не полные PI Event Frames): `shift`, `batch`, `downtime`, `custom`. Источники: MES `SHIFT`, `batch-v1`, API, correlator.

### 8. Профили развёртывания

| Профиль | Тегов | Топология | Historian | Analytics |
|---------|-------|-----------|-----------|-----------|
| **Lab S** | <500 | 1× unified | hot-only PG | in-process |
| **Site M** | 500–5k | 2–4× unified + CH | three-tier | in-process + rollups |
| **Enterprise L** | 5k–50k+ | io + hmi-read + analytics×N | CH primary read | dedicated replicas |

Детали приёмки: [analytics-platform-roadmap](../analytics-platform-roadmap.md).

### 9. Связь с BL-160 (AF-lite)

BL-160 завершается как **BL-201** до enterprise analytics. Шаблоны BL-160 — seed catalog для built-in helpers BL-203.

## Последствия

- Новый backlog BL-200…210 (Фаза 33).
- ClickHouse обязателен для Enterprise L SLO; PG обязателен для tree.
- Binding rules без изменений семантики.

## Связанные документы

- [0035-historian-dual-write](0035-historian-dual-write.md), [0032-replica-profiles-and-capabilities](0032-replica-profiles-and-capabilities.md)
- [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md), [analytics-historian-cookbook](../analytics-historian-cookbook.md)
- [roadmap.md § Фаза 33](../roadmap.md#фаза-33--аналитическая-платформа-af-capable)
