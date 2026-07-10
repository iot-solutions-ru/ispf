> **Language:** Canonical English. Russian edition: [ru/vps-demostand.md](../ru/vps-demostand.md).

# VPS demostand (example host)

> **General guide:** [demostands](demostands.md) — production, throughput, demo-idle, edge.

This page is **operational notes** for one prod example: single-node Docker behind nginx. Use as a template; replace container names and hosts with your own.

## Profile

**Demo / idle** from [demostands.md § Demo / idle](demostands.md):

- `ISPF_CLUSTER_ENABLED=false`, unified role `all`
- Overlay [`deploy/ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env)
- 2 RUNNING drivers (others STOPPED): SNMP `TELEMETRY_ONLY`, one virtual `FULL` for demo automation

## Typical topology (single-node Docker)

```text
Internet → nginx :8080
              └→ ISPF JVM :8081 (host network)
              PostgreSQL, Redis
```

Compose: [`deploy/docker-compose.vps-single.yml`](../deploy/docker-compose.vps-single.yml).

## Operations

| Action | Script |
|----------|--------|
| Apply idle env + recreate | [`deploy/vps-apply-prod-idle-env.sh`](../deploy/vps-apply-prod-idle-env.sh) |
| Tune drivers via API | [`deploy/vps-demostand-tune-drivers.sh`](../deploy/vps-demostand-tune-drivers.sh) |
| Full jar+UI rollout | [`deploy/vps-single-rollout.sh`](../deploy/vps-single-rollout.sh) |
| Thread diagnostics | [`deploy/vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py) |
| Build + SCP staging | [`deploy/vps-deploy-direct.ps1`](../deploy/vps-deploy-direct.ps1) |

**Important:** on Docker stand do not use [`apply-platform-update.sh`](../deploy/apply-platform-update.sh) — it is for systemd. After env change — recreate container, not `docker restart`.

Migration from multi-replica: `vps-single-rollout.sh`. Back to cluster: [cluster](cluster.md).

## Versions with hot-path fixes

| Version | Fix |
|--------|-------------|
| ≥ 0.9.100 | `AlarmShelfService` — read-only hot path |
| ≥ 0.9.101 | `ScheduleObjectService.listEnabled()` — no write in read-only tx |

See [0033-prod-idle-demostand-tuning](decisions/0033-prod-idle-demostand-tuning.md).
