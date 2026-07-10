> **Язык:** русская версия (вычитка). Канонический английский: [en/deployment.md](../en/deployment.md).

# Развёртывание и конфигурация

## Docker Compose

```bash
docker compose up -d
```

| Сервис | Image | Порты | Credentials |
|--------|-------|-------|-------------|
| postgres | timescale/timescaledb:latest-pg16 | 5432 | ispf/ispf |
| redis | redis:7-alpine | 6379 | — |
| nats | nats:2.10-alpine (-js) | 4222, 8222 | — |
| mosquitto | eclipse-mosquitto:2 | 1883 | config: deploy/mosquitto/ |
| keycloak | keycloak:26.0 dev | 8180 | admin/admin |

Volume: `ispf_pg_data`.

Profiles Compose **не используются** — все сервисы стартуют вместе.

## Spring Boot Server

Артефакт: `:packages:ispf-server:bootRun` или JAR из `build/libs/`.

### Переменные окружения

| Переменная | Default | Описание |
|------------|---------|----------|
| `ISPF_DB_URL` | `jdbc:postgresql://localhost:5432/ispf` | JDBC URL |
| `ISPF_DB_USER` | ispf | DB user |
| `ISPF_DB_PASSWORD` | ispf | DB password |
| `ISPF_SERVER_PORT` | 8080 | HTTP port |
| `ISPF_OAUTH_ISSUER` | Keycloak realm URL | JWT issuer |
| `ISPF_MQTT_ENABLED` | false | MQTT integration |
| `ISPF_MQTT_BROKER` | tcp://localhost:1883 | Broker URL |
| `ISPF_NATS_ENABLED` | false | NATS integration |
| `ISPF_NATS_URL` | nats://localhost:4222 | NATS URL |
| `ISPF_BOOTSTRAP_FIXTURES_ENABLED` | true | Demo/lab fixture models и demo-узлы (`mqtt-sensor-v1`, …). См. [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md). **Prod VPS:** `vps-deploy-direct.ps1` выставляет `false`; lab demo — `vps-factory-reset.sh --fixtures`. |

### Профили

| Profile | Файл | Сценарий |
|---------|------|----------|
| default | application.yml | PostgreSQL + JWT |
| local | application-local.yml | H2 file, X-ISPF-Role |
| dev | application-dev.yml | Full stack + MQTT/NATS |
| test | application-test.yml | H2 memory, tests |

### База данных

- **Flyway** — миграции при старте (`ddl-auto: validate`); locations по `RelationalDialect` (см. [ADR-0037](decisions/0037-relational-core-portability.md))
- **local:** H2 `./data/ispf-local` (PostgreSQL compatibility mode)
- **prod:** PostgreSQL; TimescaleDB extension (docker image `timescale/timescaledb`) — hypertables `variable_samples` и `event_history`, retention 90d ([0009](decisions/0009-timescaledb-retention.md), [0015](decisions/0015-event-history-timescale.md))

#### Режимы хранилища

| Режим | Metadata (`ISPF_DB_*`) | События | История переменных |
|-------|------------------------|---------|-------------------|
| **Одна БД** (default) | PostgreSQL | `ISPF_EVENT_JOURNAL_STORE=jdbc` | `ISPF_VARIABLE_HISTORY_STORE=jdbc` |
| **Split telemetry** | PostgreSQL | `clickhouse` / `cassandra` | `clickhouse` / `cassandra` |

Одна БД — штатный сценарий: конфигурация, объекты, `event_history` и `variable_samples` в одном PostgreSQL. ClickHouse/Cassandra — только при высокой нагрузке на time-series.

Опционально: `ISPF_METADATA_DB_KIND` (`postgresql`, `h2`, `mssql`, …) — см. [STORAGE_PORTABILITY_INVENTORY.md](storage-portability-inventory.md).

**Смена движка metadata (greenfield, до v1.0):** новая пустая БД, Flyway baseline выбранного dialect, конфигурация — импорт бандлов. In-place миграция PG→MSSQL не поддерживается.

Platform **Data Sources** (`root.platform.data-sources.*`): `internal` — схема в `ISPF_DB`; `external` — JDBC к внешней БД для отчётов и SQL bindings (не metadata).

### Messaging

| Broker | Включение | Использование |
|--------|-----------|---------------|
| MQTT | `ispf.mqtt.enabled=true` | Device drivers |
| NATS | `ispf.nats.enabled=true` | Workflow messageTask |

## Web Console

Static build:

```bash
cd apps/web-console && npm run build
```

`dist/` — раздавать через nginx/ingress с proxy на API.

Dev: `npm run dev`, proxy на backend.

## Production quick start (BL-127)

Одна команда поднимает **lab / localhost** стек на Linux-хосте с Docker (PostgreSQL + Redis + `ispf-server` + nginx для UI). Не привязан к VPS `ispf.iot-solutions.ru`.

**Не для internet-facing production без hardening:** порты привязаны к `127.0.0.1`, demo-fixtures выключены, дефолтные пароли БД (`ispf/ispf`) нужно сменить перед выносом в сеть. Образы — pinned tags из `deploy/air-gap-images.env`. Полный чеклист prod: [DEMOSTANDS.md § Production](demostands.md).

**Требования:** Docker Engine + Compose v2, JDK 25 (для сборки), Node.js 20+ (для UI).

```bash
bash deploy/prod-quickstart.sh
```

Скрипт:

1. Собирает `ispf-server` JAR (`bootJar`, без тестов).
2. Собирает `apps/web-console` (`npm ci && npm run build`).
3. Копирует JAR в `deploy/staging/ispf-server.jar`.
4. Запускает `deploy/docker-compose.prod-stack.yml`.
5. Ждёт readiness через `deploy/health-check.sh`.

| Endpoint | URL |
|----------|-----|
| API info | http://127.0.0.1:8080/api/v1/info |
| Actuator health | http://127.0.0.1:8080/actuator/health (не проксируется через nginx) |
| Web UI (nginx) | http://127.0.0.1:8088/ |

Остановка:

```bash
docker compose -f deploy/docker-compose.prod-stack.yml down
```

Volumes PostgreSQL сохраняются (`ispf_prod_pg`). Полная очистка: добавьте `-v`.

**Файлы:**

| Файл | Назначение |
|------|------------|
| `deploy/docker-compose.prod-stack.yml` | postgres, redis, ispf-server (Temurin JRE + mounted JAR), nginx |
| `deploy/air-gap-images.env` | Pinned image tags (shared with BL-128 air-gap pack) |
| `deploy/nginx-local-prod.conf` | proxy `/api/`, `/ws/` → server; static SPA (без `/actuator/`) |
| `deploy/health-check.sh` | Poll `/actuator/health` + smoke `/api/v1/info` |

**Prod VPS:** для `ispf.iot-solutions.ru` по-прежнему используйте `deploy/vps-deploy-direct.ps1` (direct SCP + staging), не этот quick start.

## Air-gap deployment (BL-128)

Hosts без исходящего интернета: offline bundle (JAR, UI zip, driver packs, Docker images) и runbook.

```bash
# Build host (connected)
bash deploy/air-gap-pack.sh --version 0.9.32

# Target host (isolated)
bash deploy/air-gap-apply.sh /path/to/ispf-airgap-0.9.32.tar.gz
```

Полный checklist, licensing и update без сети: [AIR_GAP_DEPLOYMENT.md](air-gap-deployment.md).

## Bundle signing (BL-100)

Commercial bundle manifests могут содержать RSA-signed блок `license`. По умолчанию подпись **опциональна**; для production marketplace включите обязательную проверку:

| Переменная / property | Default | Описание |
|-----------------------|---------|----------|
| `ISPF_LICENSE_PUBLIC_KEY_PEM` / `ispf.license.public-key-pem` | пусто | PEM RSA public key(s); несколько блоков для ротации |
| `ISPF_LICENSE_ENFORCE` / `ispf.license.enforce` | `false` | invalid license → HTTP 403 на deploy/import |
| `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES` / `ispf.license.require-signed-bundles` | `false` | manifest **без** `license` или с invalid signature → 403 |

Поведение при import/deploy:

| Условие | `require-signed-bundles=false` | `require-signed-bundles=true` |
|---------|-------------------------------|------------------------------|
| Нет `license` | OK | HTTP 403 |
| Valid signed `license` | OK | OK |
| Invalid / tampered signature | WARN (если `enforce=false`) или 403 | HTTP 403 |

Подробнее: [COMMERCIAL_LICENSING.md](commercial-licensing.md) (поведение deploy, key rotation).

## Production topology (target)

```
                    ┌─────────────┐
   Clients ────────►│   Ingress   │  nginx round-robin (REST) + sticky WS
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ispf-server-1   ispf-server-2   ispf-server-N
           │               │               │
           └───────────────┼───────────────┘
                           ▼
              PostgreSQL + Redis + NATS JetStream
```

- Stateless API replicas share one PostgreSQL tree ([ADR-0028](decisions/0028-horizontal-active-active-cluster.md)).
- Driver poll loops: exactly-one owner per device via `platform_driver_locks` (BL-136).
- Singleton schedulers: JDBC leader locks (`platform_leader_locks`).

## Multi-instance cluster (BL-134…139)

Подробное руководство: **[CLUSTER.md](cluster.md)** — топология, bindings, ADR-0029, tuning и **[порядок запуска кластера](cluster.md#порядок-запуска-и-настройка-кластера)** (staged bootstrap, профили, nginx, lab BL-210).

**Lab / Enterprise L (4 профиля: edge×2 + analytics + io):**

```bash
# На lab-хосте после загрузки jar/UI:
bash ~/ispf/lab-cluster-bootstrap.sh
# Ingress: http://<lab>:8000/
```

Артефакты: [`deploy/lab-cluster-compose.yml`](../deploy/lab-cluster-compose.yml), [`deploy/lab-cluster-bootstrap.sh`](../deploy/lab-cluster-bootstrap.sh), [`deploy/local/nginx/cluster-lab.conf`](../deploy/local/nginx/cluster-lab.conf).

**Локальный CI-стек (3 универсальные реплики):**

```bash
bash deploy/cluster-quickstart.sh
# UI + API: http://127.0.0.1:8088/
```

Compose: [`deploy/docker-compose.cluster.yml`](../deploy/docker-compose.cluster.yml).  
Ingress: [`deploy/nginx-cluster.conf`](../deploy/nginx-cluster.conf).

### Required env (each replica)

| Variable | Example | Notes |
|----------|---------|-------|
| `ISPF_REPLICA_ID` | `replica-1` | Unique per node; exposed in `/api/v1/info` |
| `ISPF_DB_URL` | `jdbc:postgresql://postgres:5432/ispf` | Same DB for all replicas |
| `ISPF_CLUSTER_ENABLED` | `true` | Enables driver ownership + cluster health API |
| `ISPF_NATS_ENABLED` | `true` | Cross-replica WS/object-change fan-out |
| `ISPF_NATS_REPLICA_EVENTS` | `true` | Required for multi-replica UI sync |
| `ISPF_CLUSTER_LIVE_VARIABLE_SYNC` | `true` | NATS live-value RAM mirror ([ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md)) |
| `ISPF_CLUSTER_PATH_INTEREST` | `true` | Redis global WS interest (requires Redis) |
| `ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS` | `500` | NATS fan-out coalesce (отдельно от `ISPF_RUNTIME_TELEMETRY_COALESCE_MS`) |
| `ISPF_REDIS_ENABLED` | `true` | Recommended (correlator windows, ACL cache, cluster path interest) |

Optional tuning: `ISPF_CLUSTER_DRIVER_LOCK_TTL_SECONDS` (default 30), `ISPF_CLUSTER_DRIVER_LOCK_RENEW_MS` (default 10000).

### Ops runbook

**Add node**

1. Copy an existing `ispf-server-N` service block in compose; set unique `ISPF_REPLICA_ID`.
2. Add `server ispf-server-N:8080` to `upstream ispf_backend` and `ispf_ws_backend` in nginx config.
3. `docker compose -f deploy/docker-compose.cluster.yml up -d ispf-server-N nginx`
4. Verify: `GET /api/v1/platform/cluster/health` (admin) shows `replicaId` and held driver locks.

**Remove node (graceful)**

1. `docker stop ispf-server-N` — nginx marks upstream down after `max_fails`.
2. Driver locks on that node expire within TTL; another replica reclaims via `DriverOwnershipScheduler`.
3. Remove service from compose/nginx when drained.

**Failover verify**

1. `curl -sf http://127.0.0.1:8088/api/v1/info` — should succeed with any replica up.
2. Stop one replica: REST must not 502; WS clients on other replicas stay connected; NATS propagates live variable snapshots ([ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md)).
3. Admin → System → Metrics → cluster health card (`/api/v1/platform/cluster/health`).

**Ops checklist (BL-139)**

- [ ] `ISPF_CLUSTER_ENABLED=true` and unique `ISPF_REPLICA_ID` on every replica
- [ ] Shared PostgreSQL reachable from all nodes; Flyway migrations applied once
- [ ] NATS enabled with `ISPF_NATS_REPLICA_EVENTS=true` for cross-replica UI sync
- [ ] Redis enabled (recommended) for correlator windows / ACL cache
- [ ] Nginx upstream lists all healthy `ispf-server-*` backends; `/ws/` uses `ip_hash`
- [ ] `GET /api/v1/platform/cluster/health` (admin) — all nodes `UP`, driver locks visible
- [ ] Smoke: `bash deploy/cluster-smoke-test.sh` (round-robin, REST failover, driver reclaim)
- [ ] Config sync: `bash deploy/cluster-smoke-test.sh --config-sync` ([ADR-0030](decisions/0030-cluster-config-structure-replica-sync.md))
- [ ] Scale gate (lab/CI): `python deploy/cluster-scale-load-test.py --scale-factor-floor 1.8`
- [ ] Kill one replica: REST via LB stays 200; driver locks migrate within TTL + failover scan

**Automated gates**

| Gate | Command / workflow |
| ---- | ------------------ |
| JDBC ownership | `./gradlew :packages:ispf-server:test --tests com.ispf.server.driver.ClusterFailoverIntegrationTest` |
| Compose smoke | `bash deploy/cluster-smoke-test.sh` |
| Config/structure sync smoke | `bash deploy/cluster-smoke-test.sh --config-sync` |
| Scale-out 1.8× | `python deploy/cluster-scale-load-test.py` |
| CI | [`.github/workflows/cluster-load-test.yml`](../.github/workflows/cluster-load-test.yml) |

### Prod VPS (пример single-node)

Текущий публичный пример использует **single unified node**. Обобщённые профили развёртывания: **[DEMOSTANDS.md](demostands.md)** (production, throughput, demo-idle, edge). Ops-шаблон single-node: [VPS_DEMOSTAND.md](vps-demostand.md).

| Script | When |
|--------|------|
| [`vps-single-rollout.sh`](../deploy/vps-single-rollout.sh) | Jar + UI на single-node; миграция cluster → single |
| [`vps-apply-prod-idle-env.sh`](../deploy/vps-apply-prod-idle-env.sh) | Merge prod-idle overlay + **recreate** container (не `docker restart`) |
| [`vps-demostand-tune-drivers.sh`](../deploy/vps-demostand-tune-drivers.sh) | После recreate: SNMP `TELEMETRY_ONLY`, demo-sensor `FULL` |
| [`vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py) | Thread CPU diagnostic (SSH) |
| [`ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env) | Demo-idle overlay ([DEMOSTANDS.md](demostands.md)) |
| [`vps-deploy-direct.ps1`](../deploy/vps-deploy-direct.ps1) | Локальная сборка + SCP staging |

```powershell
.\deploy\vps-deploy-direct.ps1 -Version <ver> -SkipTests -SkipDriverPacks
```

На хосте после upload:

```bash
bash /opt/ispf/bin/vps-single-rollout.sh /opt/ispf/staging/<ver>
bash /opt/ispf/bin/vps-demostand-tune-drivers.sh   # если нужен demo-idle profile для драйверов
curl -sf http://127.0.0.1:8080/api/v1/info
```

**Не использовать** [`apply-platform-update.sh`](../deploy/apply-platform-update.sh) на Docker single-node — скрипт для **systemd** `:8080`.

#### Multi-replica cluster (опционально)

| Script | When |
|--------|------|
| [`vps-deploy-direct.ps1 -Cluster`](../deploy/vps-deploy-direct.ps1) | Routine jar + UI rollout |
| [`vps-cluster-rollout.sh`](../deploy/vps-cluster-rollout.sh) | Restart replicas after staging upload |
| [`vps-cluster-bootstrap.sh`](../deploy/vps-cluster-bootstrap.sh) | First-time cluster install only |
| [`vps-cluster-factory-reset.sh`](../deploy/vps-cluster-factory-reset.sh) | Drop PG + reset Scylla/Redis |
| [`vps-cluster-verify.sh`](../deploy/vps-cluster-verify.sh) | Post-deploy health; `--config-sync` smoke |

```powershell
.\deploy\vps-deploy-direct.ps1 -Version <ver> -SkipTests -Cluster
ssh user@host 'bash /opt/ispf/bin/vps-cluster-verify.sh --config-sync'
```

**Rollback cluster → single:** [DEMOSTANDS.md](demostands.md), [VPS_DEMOSTAND.md](vps-demostand.md).

## ClickHouse variable history (prod playbook, BL-114)

Full ops guide: **[CLICKHOUSE_PROD_PLAYBOOK.md](clickhouse-prod-playbook.md)** — install, event journal, historian cutover, **dual-write** (BL-116), verify, rollback.

Historian по умолчанию: PostgreSQL/Timescale (`ISPF_VARIABLE_HISTORY_STORE=jdbc`). Dual-write: `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true` + CH credentials (reads остаются в PG).

## Мониторинг

Actuator endpoints:

- `/actuator/health` — liveness/readiness
- `/actuator/prometheus` — metrics (admin role); ISPF gauges `ispf_event_history_records`, `ispf_alert_fires_total`, `ispf_object_change_queue_size`, …
- `/actuator/metrics` — Micrometer

**Metrics probe:** sync `/api/v1/platform/metrics` → `root.platform.devices.platform-metrics-probe`. Включение — **runtime** через Admin → System → Metrics → Load diagnostics (чекбокс) или `PUT /api/v1/platform/diagnostics/metrics-probe` с `{ "enabled": true }`. Boot env `ISPF_PLATFORM_METRICS_PROBE_ENABLED` не стартует scheduler (см. [OBSERVABILITY.md](observability.md)). Создание device: `python deploy/setup-platform-metrics-monitor.py`.

## Логирование

```yaml
logging.level.com.ispf: INFO   # default
logging.level.com.ispf: DEBUG  # local/dev
```

## Локальные данные

| Профиль | Путь данных |
|---------|-------------|
| local | `./data/ispf-local.mv.db` |
| dev | PostgreSQL volume `ispf_pg_data` |

Очистка стенда: удалить H2 file или `docker compose down -v` (PostgreSQL).

## Remote host (systemd + nginx)

Скрипт `deploy/remote-setup-ispf.sh` — одноразовая установка на Linux VPS (Ubuntu 24.04+):

1. Останавливает лишние сервисы на хосте (настраивается под конкретный сервер).
2. Ставит Temurin JDK 25.
3. Кладёт артефакты в `/opt/ispf`:
   - `ispf-server.jar` ← `/tmp/ispf-server.jar`
   - `web-console/` ← `/tmp/ispf-web-console-dist/`
4. Создаёт `ispf-server.service` (порт **8080**). На **prod VPS** (`ispf.iot-solutions.ru`) datasource задаётся через `/opt/ispf/ispf-server.env` → **PostgreSQL** в Docker (`ispf-postgres`), несмотря на `--spring.profiles.active=local` в unit-файле. Каталог `/opt/ispf/data/` — служебные файлы (auto-update, installation-id), **не** H2.
5. Настраивает nginx на **80** (`ai.iot-solutions.ru`): static UI + proxy `/api/`, `/ws/`, `/actuator/`.
6. Опционально: если в `/tmp/snmpd-ispf.conf` — ставит snmpd для demo `snmp-localhost`.

Подготовка и запуск на сервере:

```bash
# Локально: сборка
./gradlew :packages:ispf-server:bootJar
cd apps/web-console && npm run build
scp packages/ispf-server/build/libs/ispf-server-*.jar user@host:/tmp/ispf-server.jar
scp -r apps/web-console/dist user@host:/tmp/ispf-web-console-dist
scp deploy/remote-setup-ispf.sh deploy/snmpd-ispf.conf user@host:/tmp/
ssh user@host 'bash /tmp/remote-setup-ispf.sh'
```

Проверка: `http://host/` (UI), `http://host/api/v1/info`.

Дополнительно:

| Скрипт | Назначение |
|--------|------------|
| `deploy/remote-cleanup-apache-ispf-only.sh` | Убрать Apache/ISPmanager, оставить только ISPF на :80 |
| `deploy/update-snmp-mappings.sh` | Обновить OID mappings на уже работающем сервере |
| `deploy/start-snmp-driver.sh` | Login + start SNMP driver |
| `deploy/apply-platform-update.sh` | Установка jar + UI из staging и перезапуск systemd (вызывается API обновления) |
| `deploy/remote-update-ispf.sh` | Ручное обновление с `/tmp` артефактов |

### Автообновление с GitHub Releases

1. Опубликуйте релиз: `git tag v0.1.1 && git push origin v0.1.1` — workflow `.github/workflows/release.yml` соберёт `ispf-server.jar` и `web-console.zip`.
2. На VPS в `ispf-server.service` включено:
   - `ISPF_UPDATE_CHECK_ENABLED=true` — периодическая проверка (по умолчанию раз в час)
   - `ISPF_UPDATE_APPLY_ENABLED=true` — кнопка «Обновить и перезапустить» в админ-консоли
3. Админ видит баннер, когда на GitHub есть более новая версия, чем текущий jar (`/api/v1/info` → `version` из build-info).

Локально `apply-enabled=false` — только уведомление / ссылка на релиз.

### SNMP demo driver на remote

После деплоя SNMP-агент на хосте (`127.0.0.1:161`, см. `deploy/snmpd-ispf.conf`) и устройство `root.platform.devices.snmp-localhost`:

```bash
bash deploy/start-snmp-driver.sh
```

Скрипт логинится (`admin`/`admin`), вызывает `POST /api/v1/drivers/runtime/start?devicePath=...` и печатает status. По умолчанию `API=http://127.0.0.1:8080` (JVM); через nginx: `API=http://127.0.0.1`.

## Обновление до v0.8.0+

> **Runbook для апгрейда с pre-0.8.0.** Разовый breaking change [0010](decisions/0010-binding-rules-only.md): колонка `binding_expr` удалена (`V41`), checksum `V1` изменён — **проще пересоздать БД**, чем мигрировать legacy-привязки. На prod 0.9.x обычный deploy через [vps-deploy-direct.ps1](../deploy/vps-deploy-direct.ps1) — без пересоздания БД.

### Prod VPS (`ispf.iot-solutions.ru`) — PostgreSQL в Docker

БД: контейнер **`ispf-postgres`** (`timescale/timescaledb`), JDBC из `/opt/ispf/ispf-server.env` (`SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/ispf`).

```bash
# 1. Backup (опционально)
docker exec ispf-postgres pg_dump -U ispf ispf > ispf-pre-0.8.27.sql

# 2. Остановить server, пересоздать БД
systemctl stop ispf-server
docker exec ispf-postgres psql -U ispf -d postgres \
  -c 'DROP DATABASE IF EXISTS ispf;' \
  -c 'CREATE DATABASE ispf OWNER ispf;'

# 3. Deploy jar + UI (deploy/vps-deploy-direct.ps1 или apply-platform-update.sh)
#    Если jar/UI уже в staging — достаточно systemctl start
systemctl start ispf-server

# 4. Flyway накатит схему с V1 без binding_expr на чистую БД
curl -sf https://ispf.iot-solutions.ru/api/v1/info
```

После пересоздания: binding rules — Web Console → «Привязки»; mini-TEC — `MiniTecPlatformBootstrap` при старте.

### PostgreSQL (generic / docker compose)

```bash
docker compose exec postgres psql -U ispf -d postgres \
  -c 'DROP DATABASE IF EXISTS ispf;' -c 'CREATE DATABASE ispf OWNER ispf;'
```

### H2 (local dev only)

Удалите `./data/ispf-local.mv.db` или смените `spring.datasource.url`. См. [BINDINGS.md](bindings.md).

## SCADA NFR (mini-TEC / production)

Ориентиры из требований к мнемосхеме ТЭЦ (§4) — **операционные**, не bootstrap:

| Требование | Рекомендация ISPF |
|------------|-------------------|
| Доступность 99,9% | Кластер `ispf-server` + health checks; см. [CLUSTER.md](cluster.md) |
| ≤50 одновременных операторов | Redis session/cache (`redis` в compose); nginx sticky sessions |
| Архив телеметрии 7 лет | TimescaleDB retention policies + ClickHouse `store=clickhouse` для events |
| Резервное копирование | `pg_dump` / volume snapshots; runbook в [AIR_GAP_DEPLOYMENT.md](air-gap-deployment.md) |
| Обновление без простоя | Blue/green через `apply-platform-update.sh` + staged jar/UI |
| Сессии / timeout | Keycloak realm: SSO idle timeout, max session; см. [SECURITY.md](security.md) |
| Email/SMS алармы | `ISPF_NOTIFICATIONS_EMAIL_RELAY_URL` + webhook на SMS gateway; [AUTOMATION.md](automation.md) |
| Offline operator HMI | PWA cache web-console static; HMI layout в `dashboard-v1.layout` |
| OPC-UA полевой уровень | Driver pack `opcua`; mapping переменных GPU — lab note в [examples/mini-tec/README.md](readme.md) |

**Lab demo mini-TEC:** `ISPF_BOOTSTRAP_FIXTURES_ENABLED=true` (default local). **Prod VPS:** fixtures off — импорт через `POST /api/v1/applications/mini-tec/deploy` или factory-reset с `--fixtures`.
