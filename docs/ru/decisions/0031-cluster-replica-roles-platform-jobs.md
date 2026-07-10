> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0031-cluster-replica-roles-platform-jobs.md](../../en/decisions/0031-cluster-replica-roles-platform-jobs.md).

# ADR-0031: Cluster replica roles and platform job queue

Статус: **Принято**  
Дата: 2026-07-05

## Контекст

Кластер ISPF (ADR-0028) — symmetric active-active реплики с общей PostgreSQL. REST-отчёты (`ReportService.run`) выполняются **синхронно** на той реплике, куда попал запрос nginx. Тяжёлый SQL блокирует HTTP-поток и конкурирует с HMI/API на той же JVM.

Операторы просят:

- вынести тяжёлые отчёты на отдельные worker-узлы;
- не дублировать работу на всех репликах (не map-reduce «на каждой ноде»);
- сохранить единое дерево объектов и PG как source of truth.

**Федерация (ADR-0008)** — для multi-site hub/spoke, не для compute offload внутри одного ЦОД.

## Решение

### 1. Роли реплик

Env `ISPF_REPLICA_ROLE`:

| Роль | REST/WS | Driver poll | Job consumer |
| ---- | ------- | ----------- | ------------ |
| `all` (default) | да | да (при cluster + ownership) | да |
| `api` | да | нет | нет |
| `worker` | health + jobs API | нет | да |

Обратная совместимость: без роли = `all` (текущее поведение).

Дополнительно:

- `ISPF_CLUSTER_JOB_CONSUMER_ENABLED` — явное отключение consumer на `all`/`worker`.
- Driver ownership отключается на `api` и `worker` независимо от `ISPF_CLUSTER_DRIVER_OWNERSHIP`.

Роль пишется в `platform_cluster_replicas.replica_role` и отдаётся в `/api/v1/info`, cluster health.

### 2. Platform job queue (`platform_jobs`)

JDBC-очередь в PostgreSQL (как `platform_driver_locks` / `platform_leader_locks`):

```text
QUEUED → RUNNING → COMPLETED | FAILED
         ↑ expired TTL → re-QUEUED
```

- **Claim:** `SELECT … FOR UPDATE SKIP LOCKED` + UPDATE holder.
- **Worker:** `@Scheduled` loop на репликах с `isJobConsumerActive()`.
- **Результат:** JSONB в `platform_jobs.result` (для `report_run` — тот же payload, что sync `POST /run`).

### 3. Async reports API

| Method | Path | Response |
| ------ | ---- | -------- |
| POST | `/api/v1/reports/by-path/run-async?path=…` | `202 { jobId, status: QUEUED }` |
| GET | `/api/v1/platform/jobs/{jobId}` | `{ jobId, status, result?, errorMessage?, … }` |

Sync `POST /run` **без изменений** — для тестов и legacy.

Web console: `runReportByPath` → submit async + poll до `COMPLETED`.

### 4. Топология (пример)

```text
Clients → nginx (ip_hash) → api replicas (role=api)
                         ↘ worker replicas (role=worker, internal)
PostgreSQL ← platform_jobs (claim by workers)
```

На малом кластере без dedicated worker все реплики `all` — jobs выполняются локально (как single-node).

### 5. Вне scope v1

- Map-reduce / sharded report jobs.
- NATS JetStream как transport очереди (PG достаточно для v1).
- Async export (PDF/YARG) — отдельный BL.
- Federation как compute tier.

## Последствия

- Flyway: `V62__platform_jobs.sql`, `V63__platform_cluster_replica_role.sql`.
- Классы: `PlatformJobService`, `PlatformJobWorkerScheduler`, `PlatformJobController`.
- `ClusterProperties.replicaRole()`, `isJobConsumerActive()`, `isDriverOwnershipActive()` учитывает роль.
- ROADMAP: BL-144.

## Связанные материалы

- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md) — базовый кластер
- [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) — live sync
- [cluster](../cluster.md) — runbook ролей и jobs
- [roadmap](../roadmap.md) — replica roles + platform jobs
