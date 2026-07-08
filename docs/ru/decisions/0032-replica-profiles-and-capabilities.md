> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0032-replica-profiles-and-capabilities.md](../../en/decisions/0032-replica-profiles-and-capabilities.md).

# ADR-0032: Replica profiles and capabilities

Статус: **Принято**  
Дата: 2026-07-05

## Контекст

ADR-0031 ввёл enum `ISPF_REPLICA_ROLE` (`all` / `api` / `worker`). На prod replica-3 использует `all` вне nginx для drivers + jobs — роль не выражает intent. Нужна composable модель без костылей `ISPF_CLUSTER_JOB_CONSUMER_ENABLED`.

## Решение

### Profiles (пресеты)

| Profile | Env | Capabilities |
| ------- | --- | ------------ |
| `unified` | `ISPF_REPLICA_PROFILE=unified` (default) | все |
| `edge-api` | `edge-api` | http-public, ws, replica-sync, config-write, schedulers |
| `hmi-read` | `hmi-read` | http-public, ws, replica-sync |
| `io` | `io` | drivers, replica-sync, schedulers |
| `compute` | `compute` | jobs, replica-sync |

Обратная совместимость: `ISPF_REPLICA_ROLE=all|api|worker` мапится в profile.

Явный override: `ISPF_REPLICA_CAPABILITIES=http-public,ws,replica-sync` (заменяет caps пресета).

Приоритет: `ISPF_REPLICA_CAPABILITIES` > `ISPF_REPLICA_PROFILE` > `ISPF_REPLICA_ROLE`.

### HTTP / WS gate

- `io` / `compute` — без публичного API (503 `REPLICA_CAPABILITY_DENIED`)
- `hmi-read` — без config mutations
- `/api/v1/info`, `/actuator/health`, `/api/v1/auth/*` — всегда
- `/ws/objects` — требует `ws`

### Subsystem gates

| Subsystem | Capability |
| --------- | ---------- |
| Driver ownership | `drivers` |
| platform_jobs consumer | `jobs` |
| Leader schedulers | `schedulers` |
| NATS replica subscribe | `replica-sync` |

### DB

`platform_cluster_replicas`: `replica_profile`, `replica_capabilities` (V64). `replica_role` — deprecated alias.

### API

`/api/v1/info` и `/api/v1/platform/cluster/health` отдают `replicaProfile`, `replicaCapabilities[]`, `replicaRole` (legacy).

## Последствия

- Классы: `ReplicaCapability`, `ReplicaProfile`, `ReplicaCapabilitySet`, `ReplicaCapabilityHttpFilter`
- ROADMAP: BL-145

## Связанные материалы

- [ADR-0031](0031-cluster-replica-roles-platform-jobs.md)
- [CLUSTER.md](../cluster.md)
