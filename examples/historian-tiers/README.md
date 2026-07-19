# Historian turnkey deploy profiles (BL-159)

One-file env profiles for **hot → warm → cold** historian routing. Apply after ClickHouse is up (`deploy/docker-compose.clickhouse.yml` or [clickhouse-prod-playbook](../../docs/en/clickhouse-prod-playbook.md)).

| File | Profile | Use when |
|------|---------|----------|
| [`three-tier.env`](three-tier.env) | `ISPF_HISTORIAN_DEPLOY_PROFILE=three-tier` | Prod / Site M+: JDBC hot + CH warm + optional Parquet cold |
| [`hot-only.env`](hot-only.env) | `hot-only` | Lab / CI / laptop without ClickHouse |

## Apply (systemd / compose)

```bash
# 1) Start ClickHouse (local)
docker compose -f deploy/docker-compose.clickhouse.yml up -d

# 2) Merge into ISPF env (example)
set -a
source examples/historian-tiers/three-tier.env
set +a
# restart ispf-server / compose stack
```

Helm: set `ispf.historian.deployProfile` / `ispf.historian.warmEnabled` in `deploy/helm/ispf/values.yaml`.

## Verification

1. `GET /api/v1/info` healthy
2. Warm routing: aged samples land in ClickHouse `variable_samples` (see [historian-tiers](../../docs/en/historian-tiers.md))
3. Query SLA: `bash tools/historian-scale/historian-scale-benchmark.sh` (BL-161)
