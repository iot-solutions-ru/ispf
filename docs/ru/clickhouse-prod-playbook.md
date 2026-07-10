> **Язык:** русская версия (вычитка). Канонический английский: [en/clickhouse-prod-playbook.md](../en/clickhouse-prod-playbook.md).

# Учебное пособие по производству ClickHouse (BL-114)

Руководство по эксплуатации ClickHouse в рабочей среде ISPF: **журнал событий**, **архиватор переменных** и путь миграции **двойная запись**.

Связано: [deployment](deployment.md), [0016-clickhouse-event-journal](decisions/0016-clickhouse-event-journal.md), [0035-historian-dual-write](decisions/0035-historian-dual-write.md), [0017-telemetry-ingest-pipeline](decisions/0017-telemetry-ingest-pipeline.md).

---

## Когда использовать ClickHouse

| Рабочая нагрузка | По умолчанию | КликХаус |
|----------|---------|------------|
| Event journal | PostgreSQL/Timescale | `ISPF_EVENT_JOURNAL_STORE=clickhouse` @ ~100+ events/s |
| Variable historian | PostgreSQL/Timescale | `ISPF_VARIABLE_HISTORY_STORE=clickhouse` @ high sample rate |
| Миграция | — | **Двойная запись** (первичный PG + вторичный CH) перед переключением |

Prod VPS baseline: `jdbc` for both until load requires CH ([roadmap](roadmap.md)).

---

## Этап 0 — Предварительные условия

- ISPF server healthy (`curl https://ispf.iot-solutions.ru/api/v1/info`)
- Docker на хосте (VPS) или внешнем кластере CH
- Admin SSH access to `/opt/ispf`

---

## Этап 1. Установка ClickHouse

```bash
# On VPS (from repo deploy/)
bash deploy/vps-clickhouse-setup.sh
```

Or compose locally: `docker compose -f deploy/docker-compose.clickhouse.yml up -d`

Артефакты:

- Container `ispf-clickhouse`
- Password file `/opt/ispf/clickhouse-password.txt`
- Database `ispf` (created on first ISPF write)

---

## Этап 2 — Журнал событий → ClickHouse

1. Set in `/opt/ispf/ispf-server.env`:

| Переменная | Значение |
|----------|-------|
| `ISPF_EVENT_JOURNAL_STORE` | `clickhouse` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_URL` | `http://127.0.0.1:8123` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_DATABASE` | `ispf` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_TABLE` | `event_history` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_USERNAME` | `default` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_PASSWORD` | from `clickhouse-password.txt` |

2. `systemctl restart ispf-server`
3. Строка журнала: `ClickHouse event journal ready`
4. Гипертаблица шкалы времени для `event_history` **пропускается**, если store=clickhouse.

---

## Этап 3 — переменный архиватор

### Вариант A — двойная запись (рекомендуемый первый шаг, BL-116)

PostgreSQL остается источником истины для чтения; ClickHouse получает асинхронную копию.

```bash
bash /opt/ispf/bin/vps-variable-history-dual-write.sh
```

Конверт:

| Переменная | Значение |
|----------|-------|
| `ISPF_VARIABLE_HISTORY_STORE` | `jdbc` |
| `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED` | `true` |
| `ISPF_VARIABLE_HISTORY_CLICKHOUSE_*` | same as event journal |

Log: `ClickHouse dual-write secondary ready`

### Вариант Б — Полный переход на ClickHouse

```bash
bash /opt/ispf/bin/vps-variable-history-clickhouse.sh
```

Sets `ISPF_VARIABLE_HISTORY_STORE=clickhouse`. Reads and writes use CH only.

### Откат

| От | Действие |
|------|--------|
| Dual-write | `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false` + restart |
| CH-only historian | `ISPF_VARIABLE_HISTORY_STORE=jdbc` + restart (CH data retained) |
| Event journal CH | `ISPF_EVENT_JOURNAL_STORE=jdbc` + restart |

---

## Этап 4 — Проверка

```bash
bash /opt/ispf/vps-clickhouse-verify.sh [expected-version]
```

Проверки:

- ClickHouse `/ping`
- ISPF `/api/v1/info`, `/actuator/health`
- `ISPF_EVENT_JOURNAL_STORE=clickhouse`
- Tables `ispf.event_history`, optional `ispf.variable_samples`
- Маркеры журнала запуска
- Дым: `POST /api/v1/events/fire` → увеличение количества строк (пропускается с WARN на 404/403, когда у продукта нет демонстрационных приспособлений — `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false`)
- Если `store=clickhouse` или двойная запись: переменная выборка дыма через setVariable (то же предостережение относительно прибора)

Из развертывания Windows:

```powershell
.\deploy\vps-deploy-direct.ps1 -Version 0.9.x -SkipTests -VerifyClickHouse
```

Только двойная запись (вторичный архиватор, первичный PG — S28):

```bash
ISPF_CLICKHOUSE_VERIFY_MODE=dual-write-only bash /opt/ispf/vps-clickhouse-verify.sh
```

---

## Этап 5 — Мониторинг

| Сигнал | Где |
|--------|-------|
| CH disk / parts | `system.parts` on `ispf.*` tables |
| ISPF historian queue | `/actuator/prometheus` → `ispf_variable_history_queue_size` |
| Dual-write failures | `ispf-server` log: `ClickHouse dual-write append failed` |
| Event journal lag | `ispf_event_history_records` gauge |

Retention: CH TTL on `sampled_at` / `occurred_at` from `ISPF_VARIABLE_HISTORY_RETENTION_DAYS` / event journal config.

---

## Матрица решений

| Этап | Журнал событий | Переменный историк | Риск |
|-------|---------------|-------------------|------|
| Продукт по умолчанию | jdbc | jdbc | Самый низкий |
| Высокая частота событий | кликхаус | jdbc + двойная запись | Средний |
| Высокая телеметрия | кликхаус | двойная запись → кликхаус | Средне-высокий |
| Архив только для аналитики | jdbc | двойная запись (CH прочитано позже) | Низкий |

---

## Ссылка на файлы

| Скрипт | Цель |
|--------|---------|
| `deploy/vps-clickhouse-setup.sh` | Install CH + password |
| `deploy/vps-variable-history-clickhouse.sh` | Historian cutover |
| `deploy/vps-variable-history-dual-write.sh` | Dual-write enable |
| `deploy/vps-clickhouse-verify.sh` | Post-rollout verify |
| `deploy/docker-compose.clickhouse.yml` | Local / lab CH |

---

## Поиск неисправностей

| Симптом | Проверить |
|---------|-------|
| verify fails ping | `docker ps`, firewall 8123 |
| count not increasing | historian enabled on device variable; `ISPF_VARIABLE_HISTORY_ENABLED=true` |
| login smoke skipped | set `ISPF_VERIFY_ADMIN_USER/PASS` or fixtures disabled on prod |
| Несоответствие PG + CH | двойная запись: всегда читает PG до переключения |
