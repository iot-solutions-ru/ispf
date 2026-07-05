# Кластер ISPF (multi-replica)

Руководство по горизонтальному масштабированию API: несколько JVM-реплик, одно дерево объектов в PostgreSQL, синхронизация live-значений через NATS ([ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md)).

См. также: [ADR-0028](decisions/0028-horizontal-active-active-cluster.md), [DEPLOYMENT.md](DEPLOYMENT.md), [MESSAGING.md](MESSAGING.md), [BINDINGS.md](BINDINGS.md).

## Кластер ≠ федерация

| | **Кластер** | **Федерация** |
|---|-------------|---------------|
| Дерево объектов | Одно `root.platform.*` в одной БД | Несколько площадок / edge-агентов |
| Реплики | N stateless JVM за LB | Связь hub ↔ spoke |
| Драйвер | Exactly-one poll на устройство | См. [FEDERATION.md](FEDERATION.md) |

## Топология (пример VPS / lab)

```text
                    nginx :8080
           REST least_conn  │  WS ip_hash
        ┌──────────┬───────┴───────┬──────────┐
        ▼          ▼               ▼          │
   replica-1   replica-2      replica-3      │
   :8081       :8082           :8083          │
        └──────────┴───────┬───┴──────────────┘
                           │
              PostgreSQL (одно дерево)
              NATS (fan-out между репликами)
              Redis (path interest, ACL, correlator)
```

Compose: [`deploy/docker-compose.cluster.yml`](../deploy/docker-compose.cluster.yml), VPS: [`deploy/docker-compose.vps-cluster.yml`](../deploy/docker-compose.vps-cluster.yml).

Каждая реплика при старте:

1. Flyway (один раз на БД).
2. `loadFromDatabase()` — одинаковое дерево на всех узлах.
3. Регистрация в `platform_cluster_replicas` + heartbeat.
4. Захват `platform_driver_locks` для устройств, назначенных этому узлу.

## Где живут данные

| Данные | Хранение | Кластерное поведение |
|--------|----------|----------------------|
| Структура объектов, config, bindings | PostgreSQL | Запись на любой реплике → NATS structure sync → reload на peers |
| **Live telemetry** (`ifInOctets`, `temperature`, …) | **RAM на owner-реплике** | Не пишется в PG на каждый tick |
| **Live mirror на follower** | RAM (копия snapshot) | ADR-0029: NATS payload с `value` |
| Historian / event journal | PG / ClickHouse / Cassandra | Пишет **только owner** |
| Bindings / alerts / functions cascade | RAM + automation pipeline | Выполняется **только на owner** |

### Driver ownership

Ровно одна реплика опрашивает каждое DEVICE (`platform_driver_locks`, TTL + renew). При падении узла lock истекает, другая реплика забирает устройство.

Проверка: `GET /api/v1/platform/cluster/health` (admin) — поле `heldDevicePaths` на каждом узле.

## Bindings, variables, functions, dashboards

### Переменные

- **Raw telemetry** приходит с драйвера на owner → `setDriverTelemetryValue()` → RAM.
- **REST** `GET /api/v1/objects/{path}/variables` читает **локальный RAM** реплики, обслужившей запрос.
- **WebSocket** `/ws/objects` — push `VARIABLE_UPDATED` клиентам, подписанным на path.

До ADR-0029 follower RAM для telemetry был пуст → round-robin REST мог вернуть `null`/устаревшее значение.

### Bindings

**Локальные** (на том же DEVICE):

```cel
counterRate(ifInOctets)   → переменная ifInOctetsRate
```

**Cross-object** (на hub-объекте):

```cel
refAt("root.platform.devices.snmp-router-01", ifInOctetsRate)   → routerNetDown
```

Цепочка на **owner** реплике, где живёт исходное устройство:

```text
SNMP poll → ifInOctets (RAM)
         → binding counterRate → ifInOctetsRate (RAM)
         → (если hub на том же owner или ref читается с owner RAM) → derived vars
```

Follower **не пересчитывает** bindings — получает уже вычисленные значения через NATS mirror (`replicaIngress` events без automation).

### Functions

`INVOKE_FUNCTION`, script handlers, platform functions — выполняются на реплике, принявшей HTTP-запрос. Для side-effect функций учитывайте идемпотентность. Live-read переменных device — с любой реплики после ADR-0029.

### Dashboards

Дашборд — объект `DASHBOARD` с layout JSON. Виджеты ссылаются на `objectPath` / bindings. HMI:

1. WS subscribe на paths таблицы/графиков.
2. Push `VARIABLE_UPDATED` (локально или после NATS mirror).
3. Иногда REST refetch — должен попасть на реплику с актуальным RAM (после ADR-0029 — любая).

## ADR-0029: live variable replica sync

### Проблема (до 0029)

| Механизм | Пробел |
|----------|--------|
| NATS `ispf.events.*` | Только `path` + `variableName`, **без value** |
| `NatsEventBridge` | Пропускал `telemetry=true` |
| REST vs WS LB | `least_conn` vs `ip_hash` → разные JVM |
| WS path interest | Только per-JVM — owner не знал о подписчиках на другой реплике |

### Решение

```text
Owner (driver)
  → RAM update
  → automation (bindings, alerts, historian) — только здесь
  → ClusterLiveVariableReplicaPublisher (coalesced NATS + full DataRecord)

Follower
  → ClusterVariableReplicaApplier → RAM mirror
  → ObjectChangeEvent(replicaIngress=true) → WS push
  → REST GET /variables — свежее значение
```

```mermaid
flowchart TB
    subgraph owner [Driver owner replica]
        DRV[Driver poll / MQTT]
        RAM1[RAM update]
        AUTO[Bindings / alerts / historian]
        INT[Redis path interest]
        PUB[ClusterLiveVariableReplicaPublisher]
        DRV --> RAM1 --> AUTO
        RAM1 --> PUB
        INT --> PUB
    end
    subgraph bus [NATS]
        NATS["ispf.events.variable_updated + value"]
    end
    subgraph follower [Любая другая реплика]
        SUB[NatsObjectChangeSubscriber]
        RAM2[RAM mirror apply]
        WS[WebSocket push]
        REST[GET /variables]
        SUB --> RAM2 --> WS
        RAM2 --> REST
    end
    PUB --> NATS --> SUB
```

### replicaIngress

События с `replicaIngress=true` на follower:

| Consumer | Поведение |
|----------|-----------|
| NATS / `ClusterLiveVariableReplicaPublisher` | Пропуск (нет loop) |
| Bindings / historian / alerts | Пропуск |
| WebSocket | Push клиентам |

### Cluster-wide path interest (Redis)

При `ispf.cluster.cluster-path-interest-enabled=true` и Redis:

- WS `subscribe` / `unsubscribe` обновляет ref-count в Redis (`ispf:cluster:ws:interest:{path}`).
- Owner публishes NATS sync, даже если все браузеры на других репликах.

Без Redis — только local interest; рекомендуется sticky REST+WS или включить Redis.

## Пример: SNMP fleet monitoring (3 реплики)

### Объекты

| Path | Тип | Назначение |
|------|-----|------------|
| `root.platform.devices.snmp-router-01` | DEVICE | SNMP router, model `snmp-agent-v1` |
| `root.platform.devices.snmp-switch-02` | DEVICE | SNMP switch |
| `root.platform.devices.snmp-fleet.hub` | CUSTOM | Cross-object агрегатор |
| `root.platform.dashboards.snmp-host-monitoring` | DASHBOARD | btop-таблица + графики |

### Bindings

На каждом DEVICE (локальные):

```json
{
  "targetVariable": "ifInOctetsRate",
  "expression": "counterRate(ifInOctets)"
}
```

На hub (cross-object):

```json
{
  "targetVariable": "routerNetDown",
  "expression": "refAt(\"root.platform.devices.snmp-router-01\", ifInOctetsRate)"
}
```

```json
{
  "targetVariable": "totalNetDown",
  "expression": "routerNetDown + switchNetDown"
}
```

### Сценарий по шагам

**T0 — старт:** R1, R2, R3 загружают одно дерево из PG. R1 захватывает lock на `snmp-router-01`, R2 — на `snmp-switch-02`.

**T1 — оператор открывает HMI:** браузер → nginx → WS на R3 (`ip_hash`), subscribe на paths дашборда. Redis фиксирует global interest → owners R1/R2 начинают публиковать updates.

**T2 — SNMP poll на R1:** `ifInOctets` обновляется → `counterRate` binding → `ifInOctetsRate`. Demand-driven: есть interest → `ObjectChangeEvent` → coalesced NATS с полным `value`.

**T3 — REST refetch на R2:** `GET .../snmp-router-01/variables/ifInOctetsRate` — follower уже применил NATS snapshot → актуальное значение (без sticky REST).

**T4 — hub `totalNetDown`:** вычисляется на owner hub-объекта (или owner исходных device). Derived value тоже уходит в NATS → все реплики показывают одну сумму на дашборде.

**T5 — падение R1:** lock истекает → R2/R3 перераспределяют устройства; краткий gap telemetry до reclaim; structure и config не теряются (PG).

## Настройки

### Обязательные (каждая реплика)

```bash
# /opt/ispf/ispf-server.env
ISPF_CLUSTER_ENABLED=true
ISPF_REPLICA_ID=replica-1          # уникально на узел
ISPF_DB_URL=jdbc:postgresql://postgres:5432/ispf
ISPF_NATS_ENABLED=true
ISPF_NATS_REPLICA_EVENTS=true
ISPF_REDIS_ENABLED=true
ISPF_CLUSTER_LIVE_VARIABLE_SYNC=true
ISPF_CLUSTER_PATH_INTEREST=true
```

### Coalesce: два независимых параметра

| Параметр | Env | Default | Где применяется |
|----------|-----|---------|-----------------|
| `ispf.runtime-telemetry.coalesce-ms` | `ISPF_RUNTIME_TELEMETRY_COALESCE_MS` | **250** | Ingress на owner: слияние tick'ов драйвера |
| `ispf.cluster.live-variable-sync-coalesce-ms` | `ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS` | **500** | NATS fan-out owner → followers |

**Зачем раздельно:** telemetry coalesce оптимизирует CPU/automation на owner; cluster coalesce — **межрепликовый трафик NATS**. Для HMI часто достаточно 500–1000 ms на cluster, оставив 250 ms на ingress.

Пример агрессивного ingress + экономный NATS:

```bash
ISPF_RUNTIME_TELEMETRY_COALESCE_MS=250
ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS=1000
```

Per-device override (только ingress, **не** NATS):

```json
{
  "host": "192.168.1.1",
  "community": "public",
  "telemetryCoalesceMs": 1000
}
```

### Runtime settings UI

Admin → Platform → Runtime settings → секция **Cluster**:

- `cluster.live-variable-sync-coalesce-ms` — hot-reloadable
- `cluster.live-variable-sync`, `cluster.path-interest`, driver lock TTL

### Отключение live sync (отладка)

```bash
ISPF_CLUSTER_LIVE_VARIABLE_SYNC=false
```

Followers снова без RAM mirror; нужен sticky session для REST+WS или чтение только с owner.

## Нагрузка и tuning

### Demand-driven (ADR-0024)

NATS sync только если есть подписчики: historian, bindings, alerts, UI (local или Redis global interest). «Мёртвый» telemetry без history и без открытого дашборда — **0 NATS**.

### Оценка msg/s

```text
NATS_msg_per_sec ≈ (N_variables_with_interest × replicas_followers) / cluster_coalesce_ms × 1000
```

Пример: 200 переменных на экране, 2 follower, coalesce 500 ms:

```text
200 × 2 / 0.5 ≈ 800 msg/s  (worst case, все vars меняются каждый coalesce window)
```

На практике counterRate/SNMP меняются реже; coalesce last-value-wins сильно снижает пик.

### Когда беспокоиться

- 10k+ historized vars с interest и coalesce &lt; 250 ms.
- Очень большие `DataRecord` (wide tables) в каждом NATS message.

Mitigation: увеличить `ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS`, сузить historian flags, проверить JetStream vs core NATS.

## Операции

### Health API

```http
GET /api/v1/platform/cluster/health
Authorization: Bearer …
```

Ответ включает: `liveVariableSyncEnabled`, `liveVariableSyncCoalesceMs`, `clusterPathInterestEnabled`, список узлов, driver locks.

UI: Admin → System → Metrics → карточка «Кластер».

### Smoke / CI

```bash
bash deploy/cluster-smoke-test.sh
python deploy/cluster-scale-load-test.py --scale-factor-floor 1.8
```

### Failover checklist

1. `curl https://ispf.example/api/v1/info` — 200 с любой живой реплики.
2. Остановить одну реплику — REST через LB без 502.
3. Driver locks мигрируют в пределах TTL + failover scan.
4. HMI на других репликах продолжает получать live values (ADR-0029).

### nginx

REST и WS **могут** использовать разные политики LB при включённом ADR-0029 — значения зеркалируются на всех JVM.

Опционально единый upstream + `ip_hash` для снижения NATS (меньше cross-replica interest).

## Связанные ADR и backlog

- [ADR-0028](decisions/0028-horizontal-active-active-cluster.md) — topology, driver locks
- [ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md) — live RAM mirror
- [ADR-0024](decisions/0024-demand-driven-variable-change-pubsub.md) — demand-driven publish
- ROADMAP BL-134…142
