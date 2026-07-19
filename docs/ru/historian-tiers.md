> **Язык:** русская версия (вычитка). Канонический английский: [en/historian-tiers.md](../en/historian-tiers.md).

# Уровни историка (BL-159, BL-202)

> **Статус:** Done (БЛ-159) — turnkey env-профили + routing. Хаб: [doc-status.md](doc-status.md).

Готовый профиль **hot → warm → cold**. Конфиг: `ispf.historian.tiers` в `application.yml`; маршрутизация при включённом warm (BL-202).

**Профили в git:** [examples/historian-tiers/](../../examples/historian-tiers/) — `three-tier.env` / `hot-only.env`. Helm: `ispf.historian.deployProfile` + `warmEnabled`.

**См. также:** [variable-history](variable-history.md), [0035-historian-dual-write](decisions/0035-historian-dual-write.md), [analytics-platform-roadmap](analytics-platform-roadmap.md) (charter / roadmap), [analytics-historian-cookbook](analytics-historian-cookbook.md), [deployment](deployment.md).

---

## Модель уровней

| Уровень | Store по умолчанию | Retention | Роль |
|---------|--------------------|-----------|------|
| **hot** | PostgreSQL / Timescale (`jdbc`) | 7 дней | Live-запись, тренды оператора, недавние агрегаты |
| **warm** | ClickHouse | 90 дней | Analytics, длинные дашборды, цель dual-write |
| **cold** | S3 + Parquet (`cold`) | 10 лет | Compliance-архив, источник bulk-экспорта |

Hot может включить **dual-write** в warm ClickHouse (`dual-write-enabled: true`), пока недавние сэмплы остаются на JDBC; при включённой маршрутизации устаревшие сэмплы уходят в warm автоматически.

---

## Маршрутизация уровней (BL-202)

При `ispf.historian.deploy-profile=three-tier` (по умолчанию) сервер включает **warm tier routing**, если `ispf.historian.tiers.warm.enabled` не задан явно:

| Путь | Поведение |
|------|-----------|
| **Write** | `TierRoutingVariableHistoryWriteStore` — недавние сэмплы → JDBC hot; старше hot retention → ClickHouse warm; опциональный dual-write для hot-окна |
| **Read** | `TierRoutingVariableHistoryQueryStore` — диапазоны через hot cutoff объединяют JDBC + ClickHouse |
| **Cold** | Ночной `HistorianColdArchiveRunner` экспортирует Parquet за день, покидающий warm retention (когда `ispf.historian.cold-archive.enabled=true`) |

Профиль `hot-only` оставляет только JDBC (`warm.enabled=false`).

Java: `HistorianTierDeployProfileEnvironmentPostProcessor`, `HistorianColdArchiveService`.

## Конфигурация по умолчанию

```yaml
ispf:
  historian:
    deploy-profile: three-tier
    tiers:
      hot:
        store: jdbc
        retention-days: 7
        min-interval-ms: 5000
        dual-write-enabled: true
      warm:
        store: clickhouse
        retention-days: 90
        clickhouse:
          url: http://localhost:8123
          database: ispf
          table: variable_samples
      cold:
        store: cold
        retention-days: 3650
        cold:
          provider: s3
          bucket: ispf-historian-archive
          prefix: variable-samples/
          format: parquet
```

Java binding: `HistorianTierProperties` + `HistorianTierProfile` (`com.ispf.server.config`).

---

## Фрагмент deploy-профиля (prod / VPS)

Однокликовый профиль **three-tier** для production-историка на масштабе. Применять после проверки ClickHouse (`vps-clickhouse-verify.sh`).

```bash
# /opt/ispf/config/runtime-settings.properties (or env on systemd unit)
ISPF_HISTORIAN_DEPLOY_PROFILE=three-tier
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_HISTORIAN_TIER_HOT_RETENTION_DAYS=7
ISPF_HISTORIAN_TIER_WARM_RETENTION_DAYS=90
ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL=http://127.0.0.1:8123
ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE=ispf
ISPF_HISTORIAN_TIER_COLD_BUCKET=ispf-historian-archive
ISPF_HISTORIAN_TIER_COLD_PREFIX=variable-samples/
# Optional: enable nightly Parquet export (local tree or S3-mounted path)
ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=true
ISPF_HISTORIAN_COLD_ARCHIVE_LOCAL_ROOT=/var/lib/ispf/historian-cold
```

`three-tier` автоматически ставит `ispf.historian.tiers.warm.enabled=true`. Override: `ISPF_HISTORIAN_TIER_WARM_ENABLED=false`.

**Только lab (один уровень):** `ISPF_HISTORIAN_DEPLOY_PROFILE=hot-only` — только JDBC, без warm routing.

**Cutover warm read (полный CH primary):** `ISPF_VARIABLE_HISTORY_STORE=clickhouse` только после dual-write soak и lab SLO gate — см. [variable-history](variable-history.md). При store=`clickhouse` маршрутизация уровней обходится.

---

## Ops-чеклист

1. Включить Timescale hypertable на `variable_samples` (автоматически при наличии extension).
2. Развернуть ClickHouse; запустить `vps-clickhouse-verify.sh`.
3. Задать `ISPF_HISTORIAN_DEPLOY_PROFILE=three-tier` и env retention выше.
4. Следить за ошибками append в CH в логах сервера (warm path на dual-write — best-effort).
5. Включить `ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=true`, когда нужен Parquet-архив; синхронизировать `local-root` в S3 или смонтировать bucket. Cold **query** через Athena/Trino вне scope (в BL-202 — только export).

---

## Связанный roadmap

| BL | Тема |
|----|------|
| BL-159 | Модель конфига уровней |
| BL-202 | Маршрутизация уровней + cold Parquet export (этот документ) |
| BL-161 | Query SLO — [variable-history](variable-history.md) |
| BL-201 | AF-lite templates — [reference-asset-analytics](reference-asset-analytics.md) |
| BL-163 | On-demand trend export CSV/Parquet |

> **Note:** [analytics-platform-roadmap](analytics-platform-roadmap.md) остаётся charter/roadmap; статус исполнения смотрите в EN hub и backlog, не как «уже shipped».
