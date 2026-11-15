> **Язык:** русская версия. Английский оригинал: [en/clickhouse-prod-playbook.md](../en/clickhouse-prod-playbook.md).

# Руководство по подключению и эксплуатации ClickHouse в продакшене

> **Статус:** Lab — пошаговый перевод журнала событий и архива истории переменных на СУБД ClickHouse, режим параллельной записи (`dual-write`) и порядок проверки. Метки зрелости: [doc-status.md](doc-status.md).

По умолчанию ISPF хранит и журнал событий, и историю переменных в основной базе данных PostgreSQL / TimescaleDB (`jdbc`). Переводить хранение временных рядов на **ClickHouse** рекомендуется на объектах с интенсивным потоком данных (от сотен событий в секунду и десятков тысяч опрашиваемых точек).

**Связанные разделы:** [Развёртывание и конфигурация](deployment.md) · [Уровни хранения истории](historian-tiers.md) · [История переменных](variable-history.md) · [ADR-0016](decisions/0016-clickhouse-event-journal.md) · [ADR-0035](decisions/0035-historian-dual-write.md).

---

## 1. Когда переходить на ClickHouse

| Подсистема | Базовый режим (малые и средние внедрения) | Режим высокой нагрузки (ClickHouse) |
|------------|-------------------------------------------|-------------------------------------|
| **Журнал событий** (`event_history`) | PostgreSQL / TimescaleDB (`ISPF_EVENT_JOURNAL_STORE=jdbc`) | `ISPF_EVENT_JOURNAL_STORE=clickhouse` (при потоке от ~100 событий/с и выше) |
| **История переменных** (`variable_samples`) | PostgreSQL / TimescaleDB (`ISPF_VARIABLE_HISTORY_STORE=jdbc`) | Сначала параллельная запись (`ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true`), затем при необходимости полный переход на `ISPF_VARIABLE_HISTORY_STORE=clickhouse` |

---

## 2. Шаг 1 — Установка контейнера ClickHouse

На выделенном Linux-сервере запустите установочный скрипт:

```bash
bash deploy/vps-clickhouse-setup.sh
```

Либо поднимите контейнер локально через Docker Compose:

```bash
docker compose -f deploy/docker-compose.clickhouse.yml up -d
```

Скрипт поднимает контейнер `ispf-clickhouse` (HTTP-порт `8123`), сохраняет сгенерированный пароль в `/opt/ispf/clickhouse-password.txt` и подготавливает базу данных `ispf`.

---

## 3. Шаг 2 — Перевод журнала событий на ClickHouse

Пропишите параметры в `/opt/ispf/ispf-server.env`:

| Переменная окружения | Значение |
|----------------------|----------|
| `ISPF_EVENT_JOURNAL_STORE` | `clickhouse` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_URL` | `http://127.0.0.1:8123` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_DATABASE` | `ispf` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_TABLE` | `event_history` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_USERNAME` | `default` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_PASSWORD` | пароль из `/opt/ispf/clickhouse-password.txt` |

Перезапустите сервер (`systemctl restart ispf-server` или пересоздайте контейнер Docker) и убедитесь, что в журнале запуска появилась строка `ClickHouse event journal ready`.

---

## 4. Шаг 3 — Подключение ClickHouse для истории переменных

### Вариант А. Безопасный переход через параллельную запись (`dual-write`, рекомендуется)

В этом режиме основным источником для чтения графиков остаётся PostgreSQL, а все новые точки одновременно дублируются в ClickHouse (при кратковременной недоступности ClickHouse сервис `HistorianRangeCatchUpService` автоматически докачивает отставание из PostgreSQL):

```bash
bash /opt/ispf/bin/vps-variable-history-dual-write.sh
```

Или вручную в файле окружения:

| Переменная окружения | Значение |
|----------------------|----------|
| `ISPF_VARIABLE_HISTORY_STORE` | `jdbc` |
| `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED` | `true` |
| `ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL` | `http://127.0.0.1:8123` |
| `ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE` | `ispf` |
| `ISPF_VARIABLE_HISTORY_CLICKHOUSE_USERNAME` / `PASSWORD` | реквизиты доступа к ClickHouse |

При старте в логе появится отметка `ClickHouse dual-write secondary ready`.

### Вариант Б. Полный перевод истории переменных только на ClickHouse

Когда в ClickHouse накоплена история за нужный оперативный период, чтение и запись можно полностью переключить на ClickHouse:

```bash
bash /opt/ispf/bin/vps-variable-history-clickhouse.sh
```

(Устанавливает `ISPF_VARIABLE_HISTORY_STORE=clickhouse`.) При необходимости для защиты от обрывов связи с ClickHouse в этом режиме можно включить локальный дисковый буфер `ispf.variable-history.spool` — см. [История переменных (`variable-history.md`)](variable-history.md).

### Вариант В — Disk spool (после варианта Б)

Опциональный локальный store-and-forward, когда ClickHouse — **единственный** store историана. **Выкл. по умолчанию.** Не применяется к dual-write (вариант A).

| Переменная | Рекомендация |
|------------|--------------|
| `ISPF_VARIABLE_HISTORY_SPOOL_ENABLED` | `true` |
| `ISPF_VARIABLE_HISTORY_SPOOL_DIRECTORY` | `/opt/ispf/data/historian-spool` |
| `ISPF_VARIABLE_HISTORY_SPOOL_MAX_BYTES` | размер, который выдержит volume (по умолчанию 8 GiB) |

Метрики: `ispf.historian.spool.*`. Подробности: [variable-history](variable-history.md) § Disk spool. Non-claims: [known-limitations](known-limitations.md).

### Как вернуться обратно на PostgreSQL

- Отключить параллельную запись: `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false` и перезапуск.
- Вернуть чтение и запись истории на PostgreSQL: `ISPF_VARIABLE_HISTORY_STORE=jdbc` и перезапуск.
- Вернуть журнал событий на PostgreSQL: `ISPF_EVENT_JOURNAL_STORE=jdbc` и перезапуск.

---

## 5. Шаг 4 — Проверка работы и мониторинг

Запустите проверочный скрипт на сервере:

```bash
bash /opt/ispf/vps-clickhouse-verify.sh
```

Скрипт проверяет доступность ClickHouse (`/ping`), наличие таблиц `ispf.event_history` и `ispf.variable_samples` и прохождение контрольной записи.

### Ключевые метрики для наблюдения в Prometheus (`/actuator/prometheus`)

| Метрика / сигнал | За чем следить |
|------------------|----------------|
| `ispf_variable_history_queue_size` | Текущая очередь записи точек истории (в норме близка к нулю) |
| `ispf_historian_dual_write_secondary_failures_total` | Счётчик ошибок дублирующей записи в ClickHouse (`ispf.historian.dual_write.secondary_failures`) |
| `ispf_historian_dual_write_catchup_ranges` | Число открытых catch-up ranges |
| `ispf_historian_dual_write_catchup_oldest_age_ms` | Возраст oldest open catch-up range по sample clock (**не** CH-vs-PG lag) — [ADR-0035](decisions/0035-historian-dual-write.md), [known-limitations](known-limitations.md) |
| `ispf_historian_dual_write_catchup_wal_bytes` | Размер catch-up WAL на диске |
| `ispf_event_history_records` | Количество записей в журнале событий |
| Системная таблица `system.parts` в ClickHouse | Объём данных на диске и число партиций таблиц `ispf.*` |
