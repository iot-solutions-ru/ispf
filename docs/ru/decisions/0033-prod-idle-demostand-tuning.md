> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0033-prod-idle-demostand-tuning.md](../../en/decisions/0033-prod-idle-demostand-tuning.md).

# ADR-0033: Профили demostand (idle / edge) и read-only hot paths

Статус: **Принято**  
Дата: 2026-07-06

## Контекст

Платформенные defaults ([ADR-0026](0026-elastic-telemetry-ingress.md)) рассчитаны на **throughput**: elastic L0–L3 pools (ingress 4–32 workers, binding-async, object-change bus). На стендах с **малым** числом устройств (demo, HMI, edge gateway) те же defaults дают:

- лишние потоки и churn elastic scale без реальной очереди;
- высокий CPU при poll-драйверах в режиме `FULL` (полный automation pipeline на каждый OID);
- необходимость **одной** JVM вместо нескольких реплик на ограниченном CPU.

Дополнительно обнаружены **exception storms** из read-only транзакций со скрытыми INSERT/UPDATE в hot path (`AlarmShelfService`, `ScheduleObjectService`).

Остановка драйверов снижает нагрузку, но **не заменяет** выбор профиля: для demo нужны живые устройства при урезанных pools и корректных publish modes.

Обобщённое руководство: [DEMOSTANDS.md](../DEMOSTANDS.md).

## Решение

### 1. Три профиля развёртывания (+ throughput bench)

| Профиль | Elastic | Топология | Документация |
|---------|---------|-----------|--------------|
| **Production** | ON (defaults) | 1–N реплик, PG + TS/CH | [DEMOSTANDS.md § Production](../DEMOSTANDS.md#профиль-production-промышленная-эксплуатация) |
| **Demo / idle** | OFF, fixed pools | single unified node | [DEMOSTANDS.md § Demo](../DEMOSTANDS.md#профиль-demo--idle-малая-нагрузка) |
| **Edge** | OFF, minimal 1 | single node, coalesce↑ | [DEMOSTANDS.md § Edge](../DEMOSTANDS.md#профиль-edge-ограниченный-cpu) |
| **Throughput** | ON (peak tuning) | benchmark | [LOAD_TESTING.md](../load-testing.md) |

### 2. Профиль `prod-idle` (env overlay для demo / idle)

Файл [`deploy/ispf-server.prod-idle.env`](../../deploy/ispf-server.prod-idle.env) — эталон **demo-idle**; для **edge** уменьшить workers до 1 (см. DEMOSTANDS).

- `ISPF_*_ELASTIC=false` для object-change, L3 ingress, binding-async, driver I/O;
- фиксированные `*_WORKERS` / `*_THREADS` (не только min/max);
- historian/journal: `jdbc`, малые writer pools;
- `ISPF_PLATFORM_METRICS_PROBE_ENABLED=false` at boot;
- `ISPF_CLUSTER_JOB_CONSUMER_ENABLED=false` на single-node без async job queue.

**Конвенция:** при `elastic=false` Spring `*Properties.resolved*()` использует **фиксированное** поле `*WORKERS`/`*THREADS`.

Применение: merge в `ispf-server.env` + **recreate** контейнера ([`deploy/vps-apply-prod-idle-env.sh`](../../deploy/vps-apply-prod-idle-env.sh) — шаблон для любого хоста).

### 3. Driver publish modes

- Опросные драйверы с многими точками → `TELEMETRY_ONLY` + coalesce ≥ poll interval.
- `FULL` — только на устройствах, где нужны alerts/workflows в demo.
- Пример API-скрипта: [`deploy/vps-demostand-tune-drivers.sh`](../../deploy/vps-demostand-tune-drivers.sh).

### 4. Single unified node для idle demo

`ISPF_CLUSTER_ENABLED=false`, `replicaRole=all`. Multi-replica — только при запасе CPU и цели HA/throughput ([CLUSTER.md](../cluster.md)).

### 5. Read-only hot paths без write

| Сервис | Hot path | Было | Стало |
|--------|----------|------|-------|
| `AlarmShelfService` | `isShelved()`, `listActive()` | `expireStale()` UPDATE в read-only tx | `@Scheduled expireStaleScheduled()` 60s |
| `ScheduleObjectService` | `listEnabled()` | `ensureCatalog` INSERT в read-only tx | Пустой список; catalog в bootstrap `ensureCatalog()` |

Правило: `@Transactional(readOnly = true)` на частых тиках **не вызывает** ensure/create/update.

### 6. Container recreate для env (Docker)

После смены `env_file` — `compose rm` + `up`, не `docker restart`.

## Последствия

**Плюсы**

- Предсказуемый низкий CPU на demo/edge при живых драйверах.
- Явное разделение «load-test defaults» vs «idle overlay».
- Меньше скрытых ERROR в логах.

**Минусы**

- Prod-idle **не** подходит для flood MQTT без смены env на throughput profile.
- Один `FULL` demo-device — осознанный CPU cost.
- Docker single-node требует отдельного deploy path (не systemd `apply-platform-update.sh`).

**Документация**

- [DEMOSTANDS.md](../DEMOSTANDS.md) — основной guide
- [VPS_DEMOSTAND.md](../VPS_DEMOSTAND.md) — пример ops на одном хосте
- [DEPLOYMENT.md](../deployment.md), [CLUSTER.md](../cluster.md)

## Связанные материалы

- [ADR-0026](0026-elastic-telemetry-ingress.md) — elastic ingress defaults (throughput)
- [ADR-0027](0027-event-journal-ingress-fast-path.md) — journal fast path
- [ADR-0028](0028-horizontal-active-active-cluster.md) — cluster vs single
- [LOAD_TESTING.md](../load-testing.md) — throughput
- [OBSERVABILITY.md](../OBSERVABILITY.md) — Load diagnostics
