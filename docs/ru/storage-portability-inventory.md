> **Язык:** русская версия. Английский оригинал: [en/storage-portability-inventory.md](../en/storage-portability-inventory.md).

# Переносимость реляционного ядра между СУБД (ADR-0037)

> **Статус:** Stable — архитектура диалектов реляционной БД метаданных (`RelationalDialect`) и совместимость с PostgreSQL, H2 и MS SQL Server. Метки зрелости: [doc-status.md](doc-status.md).

В соответствии с [ADR-0037](decisions/0037-relational-core-portability.md) все платформенные SQL-запросы к таблицам метаданных изолированы через интерфейс диалекта **`RelationalDialect`**, чтобы ядро сервера могло работать не только на PostgreSQL, но и на встроенной БД H2 и корпоративных реляционных СУБД (например, Microsoft SQL Server).

---

## 1. Соответствие типов данных в миграциях Flyway

Базовые скрипты схемы разделены по каталогам диалектов (`db/migration/postgresql/`, `db/migration/mssql/` и др.):

| Тип в PostgreSQL | Аналог в MS SQL Server / других СУБД |
|------------------|--------------------------------------|
| `UUID` | `UNIQUEIDENTIFIER` (MSSQL), `CHAR(36)` |
| `BYTEA` | `VARBINARY(MAX)` / `BLOB` |
| `JSONB` | `NVARCHAR(MAX)` с проверкой JSON / нативный тип `JSON` |
| `TIMESTAMPTZ` | `DATETIMEOFFSET` / `TIMESTAMP WITH TIME ZONE` |
| `CREATE SCHEMA` и индексы `GIN` | Специфичные конструкции конкретного диалекта |

> **Порядок смены СУБД метаданных:** при переходе на другой тип реляционной СУБД (`ISPF_METADATA_DB_KIND`) создаётся чистая база данных, при старте сервера автоматически накатывается базовая миграция выбранного диалекта (`V1__baseline.sql`), а прикладные проекты переносятся импортом пакетов `bundle.json`.

---

## 2. Изоляция специфичных SQL-конструкций в Java (`RelationalDialect`)

| Подсистема | Класс в коде | Особенность SQL | Метод в `RelationalDialect` |
|------------|--------------|-----------------|-----------------------------|
| Очередь фоновых заданий | `PlatformJobService` | Захват строки без блокировки соседей (`FOR UPDATE SKIP LOCKED`) | `queuedJobSelectSql()` |
| Изоляция схем приложений | `ApplicationSchemaSession` | Переключение текущей схемы (`SET search_path`) | `activateSchemaSql()` |
| Системный каталог таблиц | `PlatformSqlCatalog` | Префикс схемы (`public.` против `PUBLIC.`) | `platformSchemaPrefix()` |
| Интервальная агрегация истории | `JdbcVariableHistoryQueryStore` | Вычисление временных корзин (`extract(epoch...)`, `to_timestamp`) | `variableSampleBucketAggregateSql()` |
| Гипертаблицы TimescaleDB | `TimescaleHypertableInitializer` | Вызов `create_hypertable` (только при наличии расширения) | `supportsTimescale()` |
| Leader locks | `PlatformLeaderLockService` | Table leases (`platform_leader_locks`), не `pg_advisory_lock` | На MSSQL оставлять table leases ([0078](decisions/0078-mssql-metadata-certification.md)) |
| Audit / script locks | `AuditChainStore`, function `advisoryLock` | `pg_advisory_xact_lock` (+ SQLSTATE `23505`) | Dialect API до MSSQL prod (0078) |
| JDBC `LIMIT` stores | Audit/config + event journal | Жёсткий `LIMIT` | `OFFSET/FETCH` / `TOP` через диалект **до B1** (audit/config) и **B2** (journal) — [0078](decisions/0078-mssql-metadata-certification.md) |

---

## 3. Переменные окружения для выбора хранилища

| Переменная | Назначение |
|------------|------------|
| `ISPF_DB_URL` | JDBC-строка подключения к основной реляционной базе метаданных |
| `ISPF_METADATA_DB_KIND` | Явное указание диалекта: `postgresql`, `h2`, `mssql`, `mysql`, `oracle` (если не указано — по JDBC URL). **`mssql` — lab green; абсолютный запрет снят для B1-envelope** (не Prod Yes) ([0078](decisions/0078-mssql-metadata-certification.md), [0037](decisions/0037-relational-core-portability.md)); **`mysql` / `oracle` — boot refuse** до своих gate ([0079](decisions/0079-mysql-metadata-certification.md), [0080](decisions/0080-oracle-metadata-certification.md)) |
| `ISPF_EVENT_JOURNAL_STORE` | Хранилище журнала событий: `jdbc` (та же реляционная БД) или `clickhouse` |
| `ISPF_VARIABLE_HISTORY_STORE` | Хранилище истории переменных: `jdbc` (та же реляционная БД) или `clickhouse` |

**Связанные разделы:** [Развёртывание и конфигурация](deployment.md) · [Настройка ClickHouse](clickhouse-prod-playbook.md) · [ADR-0037](decisions/0037-relational-core-portability.md).
