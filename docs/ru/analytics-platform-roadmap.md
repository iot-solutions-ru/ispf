> **Язык:** русская версия (вычитка). Канонический английский: [en/analytics-platform-roadmap.md](../en/analytics-platform-roadmap.md).

# Roadmap аналитической платформы — БЛ-200…211 (AF-capable)

**Цель:** эволюция от **AF-lite** (БЛ-160) к **AF-capable** analytics plane — derived tags, материализованные rollups, мультитеговые запросы — на **одном сервере** или в **кластере с разделением ролей**.

| | |
| --- | --- |
| **Фаза** | 33 (расширяет Фазу 28 historian) |
| **ADR** | [0038-analytics-platform-architecture.md](decisions/0038-analytics-platform-architecture.md) |
| **Предпосылки** | БЛ-159 (tiers), БЛ-160 (AF-lite), БЛ-161 (SLO), dual-write CH на prod |
| **Конкурентная цель** | Historian **10/10** — «petabyte + asset framework» — [roadmap.md § Фаза 33](roadmap.md#фаза-33--аналитическая-платформа-af-capable) |
| **Статус** | BL ID и Done/Partial — только в [roadmap.md](roadmap.md); этот файл — глубокий чартер |

---

## Позиционирование

| | AF-lite (БЛ-160) | Аналитическая платформа (БЛ-200…210) |
| --- | --- | --- |
| Rollups | On-read в Chart / REST aggregate | Предвычисленные + fallback on-read |
| Derived values | Один `derivedValue` на шаблон | DAG analytics tags, backfill |
| Масштаб | ~500 тегов | 50k+ тегов, enterprise SLO |
| Топология | В JVM ISPF | `unified` **или** replicas `analytics` |

---

## Сценарии развёртывания

### Сценарий A — один сервер (~500 тегов с history)

```text
ispf-server (unified) + PostgreSQL/Timescale + опционально ClickHouse
```

| Параметр | Значение |
|----------|----------|
| `ISPF_REPLICA_PROFILE` | `unified` |
| `ISPF_HISTORIAN_DEPLOY_PROFILE` | `hot-only` или `three-tier` |
| Materializer | выкл. до >1k тегов |

**Приёмка:** БЛ-201 + БЛ-204; alarm на `derivedValue`.

### Сценарий B — кластер площадки (~5k тегов)

```text
nginx → 2–4× unified + ClickHouse
```

**Приёмка:** БЛ-202…206; multi-tag API; rollups `5m`/`1h`.

### Сценарий C — enterprise (~50k+ тегов)

```text
hmi-read + io + analytics×N + CH cluster + S3 cold
```

**Приёмка:** БЛ-210 — 50k тегов, 1B samples, p95 multi-tag <3s.

---

## Backlog БЛ

| ID | Задача | Приоритет | Краткая приёмка |
|----|--------|-----------|-----------------|
| **БЛ-200** | Charter + ADR-0038 | P2 | ADR принят, Фаза 33 в roadmap |
| **БЛ-201** | Завершение AF-lite (БЛ-160 full) | P1 | Inspector, PUT API, `derivedValue` runtime, example |
| **БЛ-202** | Historian tier enforcement | P1 | hot→warm→cold write/read, cold Parquet job |
| **БЛ-203** | Calculation engine core | P1 | DAG, scheduler, built-in evaluators |
| **БЛ-204** | Write-back derived tags + cluster sync | P1 | NATS sync, backfill API |
| **БЛ-205** | Materialized rollups (OLAP) | P2 | CH rollups, materializer lag SLO |
| **БЛ-206** | Multi-tag Analytics Query API | P2 | POST query 10 tags × 7d <3s p95 |
| **БЛ-207** | Replica profile `analytics` | P2 | capability `analytics`, compose/Helm |
| **БЛ-208** | Event frames & shift context | P2 | OEE по смене, batch frames |
| **БЛ-209** | Каталог тегов, lineage, UI | P2 | Done — inspector, impact graph |
| **БЛ-210** | Enterprise scale gates & DoD | P2 | Done — lab scripts, SLO, examples |

Полные критерии приёмки — в [английской версии](../en/analytics-platform-roadmap.md).

---

## Порядок реализации

```text
БЛ-200 → БЛ-201 ─┬→ БЛ-203 → БЛ-204
                 └→ БЛ-209
БЛ-202 → БЛ-205 → БЛ-206
БЛ-203 + БЛ-202 → БЛ-207
БЛ-203 + БЛ-165 → БЛ-208
Все → БЛ-210
```

| Волна | БЛ | Срок (оценка) | Результат |
|-------|-----|---------------|-----------|
| W1 | 200, 201 | 1–2 нед. | AF-lite Done |
| W2 | 202, 203, 204 | 3–4 нед. | Tiers + calculation engine |
| W3 | 205, 206, 207 | 2–3 нед. | OLAP + API + analytics replicas |
| W4 | 208, 209, 210 | 2–3 нед. | Event frames, каталог, scale proof |

**Итого:** ~2–3 месяца до AF-capable v1 (не полный PI parity).

---

## БЛ-212–215: Каталог функций и расширяемость ([ADR-0042](decisions/0042-analytics-function-catalog.md))

| БЛ | Scope | Результат |
|----|--------|---------|
| **212a** | `GET /platform/analytics/catalog`; dormant evaluators | Единый API для builtins + `hist.*` + reactive CEL |
| **212b** | Браузер формул в модальном редакторе выражений | PI-like discoverability без дерева AF |
| **213** | SPI `ispf-analytics-api` + optional packs JAR | Отраслевые KPI вне ядра |
| **214** | `@analyticsFormulas` + import из bundle приложения | Параметризованные формулы пользователя |
| **215** | `formulaRef` на правилах, blueprint/marketplace | Переиспользуемые стандарты площадки |

**БЛ-212a** — **Готово** (2026-07-10). Catalog API + dormant evaluators (`totalizer`, `min`, `max`, `last`).

**БЛ-212b** — **Готово** (2026-07-10). Браузер формул в модальном редакторе выражений; дублирующие статические TS-каталоги удалены.

**БЛ-213** — **Готово** (2026-07-10). SPI `ispf-analytics-api`; первый open pack `ispf-analytics-core-ext` (`energyDelta`).

---

## Вне scope (БЛ-211+)

- Полный PI Analytics DSL
- Отдельная AF-база / дублирующее дерево активов
- ML inference на тегах (БЛ-175)
- PI Vision–class UI (Фаза 26)

---

## Changelog

| Дата | Изменение |
|------|-----------|
| 10.07.2026 | БЛ-212a/b готово; БЛ-213 готово (pack `energyDelta`); ADR-0042 принят |
| 09.07.2026 | Первоначальный charter БЛ-200…210 + ADR-0038 |
