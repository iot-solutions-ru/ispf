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
| Очередь фоновых заданий | `PlatformJobService` | Захват строки без блокировки соседей (`FOR UPDATE SKIP LOCKED`) | `queuedPlatformJobSelectSql()` |
| Очередь фоновых заданий | `PlatformJobService` | `NULLS LAST`; `(UUID) getObject` на `job_id` (на MySQL / MSSQL это строка) | `orderByNullsLast()`; `job_id` читается строкой |
| SQL-привязки | `ApplicationSqlBindingStore` | Конкатенация `\|\|` (на MySQL — логическое OR, в SQL Server до 2025 её нет) | `concatExpression()` |
| Изоляция схем приложений | `ApplicationSchemaSession` | Переключение текущей схемы (`SET search_path`) | `activateSchemaSql()` |
| Системный каталог таблиц | `PlatformSqlCatalog` | Префикс схемы (`public.` против `PUBLIC.`) | `platformSchemaPrefix()` |
| Интервальная агрегация истории | `JdbcVariableHistoryQueryStore` | Вычисление временных корзин (`extract(epoch...)`, `to_timestamp`) | `variableSampleBucketAggregateSql()` — MSSQL `DATE_BUCKET`; MySQL `TIMESTAMPDIFF` / `TIMESTAMPADD` (single-engine B2, [0079](decisions/0079-mysql-metadata-certification.md)) |
| Очистка журнала событий | `JdbcEventJournalStore` | Один неограниченный `DELETE` | MSSQL `DELETE TOP (5000)`, MySQL `DELETE … LIMIT 5000`, по транзакции на пачку |
| Гипертаблицы TimescaleDB | `TimescaleHypertableInitializer` | Вызов `create_hypertable` (только при наличии расширения) | `supportsTimescale()` |
| Leader locks | `PlatformLeaderLockService` | Table leases (`platform_leader_locks`), не `pg_advisory_lock` | На MSSQL оставлять table leases ([0078](decisions/0078-mssql-metadata-certification.md)) |
| Audit / script locks | `AuditChainStore`, function `advisoryLock` | `pg_advisory_xact_lock` (+ SQLSTATE `23505`) | Dialect / table lock для B1 ([0078](decisions/0078-mssql-metadata-certification.md)); MySQL — lock row через `INSERT … ON DUPLICATE KEY UPDATE` ([0079](decisions/0079-mysql-metadata-certification.md)) |
| JDBC `LIMIT` stores | Audit/config + event journal | Dialect `limitClause()` для audit/config и journal | Lab/CI на MSSQL Evaluation; B2 Prod Yes подписан на Evaluation — [0078](decisions/0078-mssql-metadata-certification.md), [B2 checklist](../en/mssql-b2-sku-promotion-checklist.md) |

---

## 3. Переменные окружения для выбора хранилища

| Переменная | Назначение |
|------------|------------|
| `ISPF_DB_URL` | JDBC-строка подключения к основной реляционной базе метаданных |
| `ISPF_METADATA_DB_KIND` | Явное указание диалекта: `postgresql`, `h2`, `mssql`, `mysql`, `oracle` (если не указано — по JDBC URL). **`mssql` — Prod Yes (B1 + B2 + A-A)** на Evaluation + owner-extended licensed SKUs ([0078](decisions/0078-mssql-metadata-certification.md), [A-A](../en/mssql-aa-sku-promotion-checklist.md)). **`mysql` — Prod Yes (B1 + opt-in B2)** на MySQL Community Server 8.4.x только с MariaDB Connector/J; boot только под своими fences (B1 или B2 с `ISPF_MYSQL_SINGLE_ENGINE=true`; cluster refuse); `jdbc:mysql://…?permitMysqlScheme=true` + `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver` ([0079](decisions/0079-mysql-metadata-certification.md), [B1](../en/mysql-sku-promotion-checklist.md), [B2](../en/mysql-b2-sku-promotion-checklist.md)); остальные ячейки MySQL — **P-MYSQL-SKU-RERUN**; TLS и SQL вне путей gate не сертифицированы (**P-MYSQL-TLS**, **P-MYSQL-PG-SQL**); **`mariadb` refuse** (не MySQL), сервер MariaDB refuse и при `mysql`. **`oracle` — B1 Lab green, Prod No** на Oracle Database Free; boot только с Flyway pack и JDBC-драйвером Oracle от оператора (`ojdbc17` через `loader.path`, `SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver`; ISPF его не поставляет), под B1 fences ([0080](decisions/0080-oracle-metadata-certification.md), [checklist](../en/oracle-sku-promotion-checklist.md)) |
| `ISPF_EVENT_JOURNAL_STORE` | Хранилище журнала событий: `jdbc` (та же реляционная БД) или `clickhouse` |
| `ISPF_VARIABLE_HISTORY_STORE` | Хранилище истории переменных: `jdbc` (та же реляционная БД) или `clickhouse` |

**Связанные разделы:** [Развёртывание и конфигурация](deployment.md) · [Настройка ClickHouse](clickhouse-prod-playbook.md) · [ADR-0037](decisions/0037-relational-core-portability.md).
