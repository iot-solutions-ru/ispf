> **Язык:** русская версия. Английский оригинал: [en/historian-tiers.md](../en/historian-tiers.md).

# Уровни хранения истории (PostgreSQL, ClickHouse, Parquet)

> **Статус:** Beta — трёхуровневая схема архива временных рядов (оперативный, аналитический и долгосрочный архив) и автоматическая маршрутизация запросов. Метки зрелости: [doc-status.md](doc-status.md).

Для систем с большим потоком телеметрии и многолетними сроками хранения в ISPF предусмотрена трёхуровневая схема архива (**оперативный уровень → аналитический уровень → долгосрочный файловый архив**), настраиваемая в блоке `ispf.historian.tiers` файла `application.yml`.

Готовые примеры конфигураций находятся в каталоге [`examples/historian-tiers/`](../../examples/historian-tiers/):
- `hot-only.env` — хранение всей истории в одной базе PostgreSQL / TimescaleDB;
- `three-tier.env` — связка PostgreSQL (оперативный слой), ClickHouse (аналитический слой) и выгрузки в Parquet (долгосрочный архив).

**Связанные разделы:** [История переменных](variable-history.md) · [Рецепты исторических расчётов](analytics-historian-cookbook.md) · [Развёртывание и конфигурация](deployment.md) · [ADR-0035](decisions/0035-historian-dual-write.md).

---

## 1. Назначение трёх уровней хранения

| Уровень | Хранилище по умолчанию | Срок хранения | Назначение |
|---------|------------------------|---------------|------------|
| **Оперативный (`hot`)** | PostgreSQL / TimescaleDB (`jdbc`) | 7 дней | Запись текущих показаний, оперативные графики на экранах оператора, быстрые агрегаты за смену |
| **Аналитический (`warm`)** | ClickHouse (`clickhouse`) | 90 дней | Быстрые аналитические запросы по длинным интервалам и десяткам тегов, параллельная запись (`dual-write`) |
| **Долгосрочный (`cold`)** | Файлы Parquet (локальный каталог или S3) | До 10 лет | Долговременный архив для регламентной отчётности и внешних аналитических систем |

---

## 2. Как работает маршрутизация при записи и чтении

При выборе профиля `ispf.historian.deploy-profile=three-tier`:

| Операция | Как обрабатывается сервером |
|----------|-----------------------------|
| **Запись точек (`Write`)** | `TierRoutingVariableHistoryWriteStore` сохраняет свежие точки в оперативную БД PostgreSQL (`hot`), а при включённом `dual-write-enabled: true` одновременно дублирует их в ClickHouse (`warm`). Точки со штампом времени старше оперативного окна направляются напрямую в ClickHouse. |
| **Чтение графиков и агрегатов (`Read`)** | `TierRoutingVariableHistoryQueryStore` при запросе периода, выходящего за пределы оперативного окна (например, за 30 дней), автоматически объединяет данные из PostgreSQL и ClickHouse. |
| **Выгрузка в долгосрочный архив (`Cold`)** | Ночной планировщик `HistorianColdArchiveRunner` (при `ispf.historian.cold-archive.enabled=true`) выгружает суточные срезы, выходящие за срок хранения уровня `warm`, в сжатые файлы формата **Parquet** (используется модуль `ispf-export-parquet`). |

Если на объекте не требуется ClickHouse, выберите профиль `hot-only` (`ISPF_HISTORIAN_DEPLOY_PROFILE=hot-only`) — тогда вся история хранится только в PostgreSQL (`warm.enabled=false`).

---

## 3. Конфигурация в `application.yml`

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

### Переменные окружения для запуска трёхуровневого архива

```bash
ISPF_HISTORIAN_DEPLOY_PROFILE=three-tier
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_HISTORIAN_TIER_HOT_RETENTION_DAYS=7
ISPF_HISTORIAN_TIER_WARM_RETENTION_DAYS=90
ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL=http://127.0.0.1:8123
ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE=ispf
ISPF_HISTORIAN_TIER_COLD_BUCKET=ispf-historian-archive
ISPF_HISTORIAN_TIER_COLD_PREFIX=variable-samples/

# Включение ночной выгрузки в файлы Parquet:
ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=true
ISPF_HISTORIAN_COLD_ARCHIVE_LOCAL_ROOT=/var/lib/ispf/historian-cold
```

> Если вы хотите перевести вообще **всю** запись и чтение истории переменных исключительно на ClickHouse (без оперативного слоя в PostgreSQL), установите `ISPF_VARIABLE_HISTORY_STORE=clickhouse` согласно инструкции [Настройка ClickHouse](clickhouse-prod-playbook.md).

**Опциональный disk spool после cutover:** для sole-store ClickHouse включите `ISPF_VARIABLE_HISTORY_SPOOL_ENABLED=true` и укажите `ISPF_VARIABLE_HISTORY_SPOOL_DIRECTORY` на путь в data volume (Helm: `/opt/ispf/data/historian-spool`). Выкл. по умолчанию — [variable-history](variable-history.md) § Disk spool.

---

## 4. Порядок включения на промышленном сервере

1. Убедитесь, что в PostgreSQL активно расширение TimescaleDB для таблицы `variable_samples`.
2. Разверните ClickHouse и проверьте доступность базы скриптом `deploy/vps-clickhouse-verify.sh`.
3. Задайте в окружении сервера `ISPF_HISTORIAN_DEPLOY_PROFILE=three-tier` и нужные сроки хранения уровней `hot` и `warm`.
4. Следите за ошибками append в ClickHouse в логах сервера (warm path на dual-write — best-effort).
5. При необходимости многолетнего файлового архива включите `ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=true` и укажите каталог `ISPF_HISTORIAN_COLD_ARCHIVE_LOCAL_ROOT` (например, смонтированный раздел сетевого хранилища или S3-совместимого бакета). Чтение архивных файлов Parquet за прошлые годы выполняется стандартными средствами аналитики (DuckDB, ClickHouse, Trino).
6. После cutover на sole-store ClickHouse при необходимости включите disk spool на persistent volume; следите за `ispf.historian.spool.*` — [variable-history](variable-history.md) § Disk spool.

---

## Связанные разделы

- [История переменных](variable-history.md) — целевые показатели скорости запросов (SLO), выгрузка и агрегация
- [Настройка ClickHouse в продакшене](clickhouse-prod-playbook.md) — подготовка и проверка ClickHouse
- [Эталон аналитики активов](reference-asset-analytics.md) — шаблоны расчётов по оборудованию
- [Дорожная карта аналитической платформы](analytics-platform-roadmap.md) — проектный план развития (charter)
