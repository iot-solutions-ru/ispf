> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0028-horizontal-active-active-cluster.md](../../en/decisions/0028-horizontal-active-active-cluster.md).

# ADR-0028: Горизонтальный active-active кластер

## Статус

Принято (2026-07-04)

## Контекст

ISPF предполагает **одно дерево объектов** (`root.platform.*`), общее для всех узлов приложения. Операторам и администраторам нужно:

- **Active-active API/HMI** — любая здоровая реплика обслуживает REST/BFF/WebSocket.
- **Scale-out** — добавлять Docker-узлы за nginx при росте нагрузки.
- **Failover** — при падении узла остальные принимают трафик без ручного вмешательства.
- **Без дублирования driver I/O** — две реплики не должны одновременно опрашивать/писать одно устройство.

Существующие блоки (Phase 2–22):

- PostgreSQL — единый source of truth для дерева объектов.
- `PlatformLeaderLockService` — JDBC locks для singleton schedulers.
- NATS/JetStream replica fan-out — cross-replica WebSocket/object-change sync ([MESSAGING](../MESSAGING.md)).
- Redis optional — correlator windows + ACL cache, общие для реплик.
- Optimistic concurrency (`If-Match` / revision) — безопасное параллельное редактирование конфигурации ([COLLABORATION](../COLLABORATION.md)).
- Demand-driven object change pub/sub ([0024-demand-driven-variable-change-pubsub](0024-demand-driven-variable-change-pubsub.md)) — меньше лишней работы при масштабировании API tier.

**Cluster ≠ federation:** cluster = N реплик, **одна БД**, один site. Federation ([0008-federation-topology](0008-federation-topology.md)) = несколько sites / edge agents с catalog sync.

## Решение

### 1. Топология

```text
Clients → nginx (round-robin REST, sticky WS) → ispf-server × N
                                              ↓
                         PostgreSQL + Redis (optional) + NATS (required for multi-replica WS sync)
```

Каждая реплика:

- Уникальный `ISPF_REPLICA_ID` (по умолчанию random UUID; явно в Docker/K8s).
- Один и тот же `ISPF_DB_*` JDBC URL.
- `ISPF_NATS_ENABLED=true`, `ISPF_NATS_REPLICA_EVENTS=true` рекомендуется.
- `ISPF_CLUSTER_ENABLED=true` для driver ownership и cluster health API.

### 2. Active-active tiers

| Tier | Mode | Mechanism |
| ---- | ---- | --------- |
| REST / BFF / reports | Active-active | Stateless handlers; shared DB; nginx round-robin |
| WebSocket `/ws/objects` | Active-active + sticky | Client pinned to one replica; NATS fan-out syncs other replicas |
| Platform schedulers | Active-passive (one leader) | `platform_leader_locks` ([PlatformLeaderLockService](../../../packages/ispf-server/src/main/java/com/ispf/server/platform/PlatformLeaderLockService.java)) |
| Device driver poll loops | **Exactly-one owner** | `platform_driver_locks` + `DriverOwnershipService` (BL-136) |
| Binding periodic tick | Active-passive (one leader) | Existing leader lock on `binding_periodic_scheduler` |
| Event journal / historian writes | Active-active (DB) | Append to shared store; ClickHouse optional for scale ([roadmap](../roadmap.md#часть-e--полный-реестр-bl-01139)) |

### 3. Driver ownership

Перед запуском driver poll loop реплика **должна захватить** JDBC lock на `device_path`:

- Heartbeat renew каждые `ispf.cluster.driver-lock-renew-seconds` (default 10s).
- TTL `ispf.cluster.driver-lock-ttl-seconds` (default 30s).
- При stop/shutdown: release lock.
- При renew failure: stop local poll (другая реплика может взять ownership).
- Background reaper: expired locks → eligible replica auto-starts configured driver.

When `ispf.cluster.enabled=false` (default single-node): ownership is no-op (always local owner).

### 4. Ingress (nginx)

- `/api/` — `upstream` with multiple `ispf-server-*` backends, `max_fails` + `fail_timeout`.
- `/ws/` — `ip_hash` for sticky sessions + WebSocket upgrade headers.
- Static web-console served from nginx; API proxied to backend pool.

Reference: `deploy/nginx-cluster.conf`, `deploy/docker-compose.cluster.yml`.

### 5. Environment variables (multi-replica)

| Variable | Required | Description |
| -------- | -------- | ----------- |
| `ISPF_REPLICA_ID` | Recommended | Stable per-node id (e.g. `replica-1`) |
| `ISPF_CLUSTER_ENABLED` | For cluster | `true` enables driver ownership + cluster health |
| `ISPF_NATS_ENABLED` | Recommended | Cross-replica event fan-out |
| `ISPF_NATS_REPLICA_EVENTS` | Recommended | `true` (default) |
| `ISPF_REDIS_ENABLED` | Optional | Shared correlator windows / ACL cache |
| `ISPF_DB_URL` | Required | Same JDBC URL on all replicas |

Platform properties mirror: `ispf.cluster.*`, `ispf.nats.*` in [application.yml](../../../packages/ispf-server/src/main/resources/application.yml).

## Последствия


- Horizontal scale for API/automation read path.
- Survives single-node failure with nginx passive health.
- Driver I/O safe across replicas via DB locks.

Risks:

- PostgreSQL remains single writer — scale-out has limits on write-heavy historian; use ClickHouse path ([roadmap](../roadmap.md#часть-e--полный-реестр-bl-01139)).
- NATS + Redis become operational dependencies for full multi-replica UX.
- Sticky WS optional when [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) live sync + Redis path interest enabled; REST round-robin safe for HMI reads.

## Связанные материалы

- [0024-demand-driven-variable-change-pubsub](0024-demand-driven-variable-change-pubsub.md) — demand-driven pub/sub (complements cluster; update: horizontal scale = N JVMs + shared DB, not only bigger host)
- [roadmap](../roadmap.md#часть-e--полный-реестр-bl-01139) — EX-CLUSTER implementation backlog
- [deployment](../deployment.md) — Multi-instance cluster runbook
- [cluster-chaos-soak-runbook](../cluster-chaos-soak-runbook.md) — Wave 6 chaos/soak (REAL vs PARTIAL)
- [MESSAGING](../MESSAGING.md) — NATS replica fan-out
- [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) — live variable RAM mirror (closes stale-read gap)
