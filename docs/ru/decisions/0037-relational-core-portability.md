> **Язык:** русская версия. Английский оригинал: [en/decisions/0037-relational-core-portability.md](../../en/decisions/0037-relational-core-portability.md).

# ADR-0037: Relational core portability

Статус: **Принято**  
Дата: 2026-07-07

## Контекст

ISPF хранит конфигурацию платформы (дерево объектов, RBAC, workflow, federation, cluster jobs) в **одном** relational backend через Flyway + JPA/JDBC. Time-series (журнал событий, история переменных) уже абстрагированы (`EventJournalStore`, `VariableHistoryQueryStore`).

Операторам нужны:

1. **Single-DB** — metadata + events + history в одной СУБД (`store=jdbc`).
2. **Certification matrix** — PostgreSQL (prod), PG-совместимые, H2 (edge), enterprise (MSSQL, MySQL, Oracle) по мере готовности migration packs.
3. **External JDBC Data Sources** — объекты `data-source-v1` как коннекторы к **внешним** БД для отчётов, SQL bindings и миграций приложений (не заменяют metadata DB).

Ранее ADR-0015/0016/0025 фиксировали «PostgreSQL для relational core» — этот ADR **не отменяет** PostgreSQL как primary certified prod engine, а вводит **SPI и дорожную карту** переносимости.

## Решение

### 1. RelationalDialect SPI

Пакет `com.ispf.server.relational`:

- `RelationalDialect` — quoting, schema switch, `SKIP LOCKED`, bucket aggregation SQL, Flyway locations, Timescale support flag.
- `RelationalDialectDetector` — `ISPF_METADATA_DB_KIND` или auto-detect по JDBC URL.
- Реализации: `PostgreSqlDialect`, `H2Dialect`, `MssqlDialect`, `MysqlDialect`, `OracleDialect` (enterprise — по мере сертификации).

### 2. Flyway migration packs

```
db/migration/postgresql/   — V1__baseline.sql + инкрементальные V71+ (prod-путь)
db/migration/h2/           — только README; по умолчанию H2 использует postgresql pack в MODE=PostgreSQL
db/migration/mssql/        — generated greenfield baseline (Prod Yes B1+B2+A-A; licensed SKUs owner-extended; product SWITCH deferred)
db/migration/mysql/        — generated greenfield baseline (B1 Lab green на MySQL 8.4 Community; Prod: No; ADR-0079)
db/migration/oracle/       — README + Phase 1 lab (Prod: No; boot refuse; ADR-0080)
```

`FlywayConfigurationCustomizer` выбирает `locations` по активному dialect.

#### Поправка (2026-10-12) — PostgreSQL после squash V1–V70 + expand/contract

Старые инкременты **V1–V70 схлопнуты** в `V1__baseline.sql` (2026-07-07). **После этого squash** PostgreSQL снова использует **инкрементальные скрипты Flyway** (`V71__…` и далее). Новая установка применяет `V1`, затем каждую следующую версию по порядку. Предпочтителен DDL в стиле **expand/contract** ([0064-release-compatibility-lts](0064-release-compatibility-lts.md)). Пакет **не** считать «только baseline навсегда».

| Сценарий | Действие |
|----------|----------|
| Новая установка PostgreSQL | Пустая БД → Flyway применяет `V1__baseline.sql`, затем `V71+` |
| Обновление версии ISPF на том же PostgreSQL | Flyway применяет новые `Vnn__*.sql`, которых ещё нет в `flyway_schema_history` |
| Смена `ISPF_METADATA_DB_KIND` | Новая пустая БД/схема; конфигурация через **бандлы** (`package import`) — **без** in-place смены движка |
| Legacy DB всё ещё на истории V1–V70 до squash | Однократно `deploy/vps-flyway-repair.sh` — squash history до V1 + repair checksum, затем применить `V71+` |
| MSSQL | **Prod Yes (B1 + B2 + A-A)** на Evaluation `16.0.4325.4` и owner-extended licensed Standard / Enterprise / Azure SQL DB\|MI ([B1](../../en/mssql-sku-promotion-checklist.md), [B2](../../en/mssql-b2-sku-promotion-checklist.md), [A-A](../../en/mssql-aa-sku-promotion-checklist.md)). B2 retention = DELETE + 90 д; product SWITCH вне claim; AG soak tech-debt. Scope: [0078](0078-mssql-metadata-certification.md) |
| MySQL / Oracle | MySQL **B1 Lab green, Prod: No** ([0079](0079-mysql-metadata-certification.md), [checklist](../../en/mysql-sku-promotion-checklist.md)) — boot только под B1 fences; MariaDB не алиасится в MySQL. Oracle **Phase 1 lab, Prod: No** ([0080](0080-oracle-metadata-certification.md)) — только честность dialect и fences; B1 pack Oracle ждёт MySQL B1 Prod Yes, boot Oracle запрещён до готовности его Flyway pack и certification gate |

Конфигурация решения (объекты, приложения, мимики) живёт в **export/import бандлов**, не в «переносе» строк metadata между СУБД.

### 3. Cluster policy

Все реплики кластера (ADR-0028) используют **одинаковый** `ISPF_METADATA_DB_KIND` и `ISPF_DB_URL`. Смена engine — новая БД + импорт бандлов (planned maintenance), не in-place миграция данных.

### 4. Data Sources: internal vs external

| `connectionMode` | Поля | Назначение |
|------------------|------|------------|
| `internal` (default) | `schemaName` | Схема внутри `ISPF_DB` (как раньше) |
| `external` | `jdbcUrl`, `jdbcDriverClass`, `jdbcUsername`, `jdbcPassword`, `poolSize` | Внешняя БД для SQL bindings / reports / migrations |

`ExternalDataSourceRegistry` — Hikari pool per object; `DataSourceSqlSession` — единая точка выполнения SQL.

### 5. Single-DB preset

Режим по умолчанию: `ISPF_EVENT_JOURNAL_STORE=jdbc`, `ISPF_VARIABLE_HISTORY_STORE=jdbc` — всё в `ISPF_DB`. UI: preset «Одна БД» на вкладке «База данных».

## Certification matrix

| Tier | Engine | Prod | Migrations | Notes |
|------|--------|------|------------|-------|
| 0 | PostgreSQL 16 + Timescale | Yes | `postgresql/V1` + `V71+` | Primary; реестр ПО |
| 1 | PG-compatible (Yugabyte, Cockroach) | On request | `postgresql/` | Тестировать per vendor |
| 2 | H2 (edge/dev) | Edge only | `postgresql/` + PG compat mode | `application-local.yml` |
| 3 | MS SQL Server | **Prod Yes (B1 + B2 + A-A)** | Generated greenfield `mssql/V1` | Evaluation `16.0.4325.4` + owner-extended licensed SKUs (2026-11-22). B2 не сертифицирует product SWITCH / ≤20 с. A-A fence снят; AG soak tech-debt. Контракт [0078](0078-mssql-metadata-certification.md) |
| 4 | MySQL | **No — Lab green** | Generated greenfield `mysql/V1` | B1 metadata Lab green на MySQL 8.4.13 Community, 2026-11-22 ([checklist](../../en/mysql-sku-promotion-checklist.md)); клиент MariaDB Connector/J (LGPL), серверы MariaDB — refuse. Boot только под B1 fences. Named-SKU Prod Yes нет; `ddl-auto=validate` проходит (`MysqlHibernateDialect`). Контракт [0079](0079-mysql-metadata-certification.md) |
| 4 | Oracle | **No — Phase 1 lab** | README + fences | Честность dialect и заранее подключённые B1 fences ([0080](0080-oracle-metadata-certification.md), [эпик](../oracle-certification-epic.md)); без driver и Flyway SQL. B1 pack ждёт MySQL B1 Prod Yes; boot запрещён до готовности Flyway pack и gate |

## Последствия

- единая абстракция; явный single-DB сценарий; external JDBC для интеграций без смешивания с metadata.

Risks:

- enterprise-движки сертифицируются по одному; cluster требует одинаковый engine на всех репликах; смена СУБД — greenfield + бандлы, не live migration.

**Код:** `com.ispf.server.relational.*`, `DataSourceSqlSession`, `ExternalDataSourceRegistry`, расширение `data-source-v1`, UI DataSourceEditor + Database settings.

## Связанные материалы

- [storage-portability-inventory](../storage-portability-inventory.md)
- [deployment](../deployment.md) § Storage deployment modes
- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md), [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md)
- [0064-release-compatibility-lts](0064-release-compatibility-lts.md) — expand/contract + LTS платформы
- [0078-mssql-metadata-certification](0078-mssql-metadata-certification.md) — контракт сертификации MSSQL B1/B2
- [0079-mysql-metadata-certification](0079-mysql-metadata-certification.md) (MySQL B1 Lab green), [0080-oracle-metadata-certification](0080-oracle-metadata-certification.md) (Oracle Phase 1 lab, boot refuse) — B1 charters без production claim
- [BLUEPRINTS](../BLUEPRINTS.md) — `data-source-v1`
