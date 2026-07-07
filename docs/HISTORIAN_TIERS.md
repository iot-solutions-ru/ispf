# Historian tiers (BL-159)

Turnkey **hot → warm → cold** historian profile for ISPF deployments. Configuration lives in `application.yml` under `ispf.historian.tiers`; tier routing enforcement is follow-up work (BL-160+).

**See also:** [VARIABLE_HISTORY.md](VARIABLE_HISTORY.md), [decisions/0035-historian-dual-write.md](decisions/0035-historian-dual-write.md), [DEPLOYMENT.md](DEPLOYMENT.md).

---

## Tier model

| Tier | Default store | Retention | Role |
|------|---------------|-----------|------|
| **hot** | PostgreSQL / Timescale (`jdbc`) | 7 days | Live writes, operator trends, recent aggregates |
| **warm** | ClickHouse | 90 days | Analytics, long-range dashboards, dual-write target |
| **cold** | S3 + Parquet (`cold`) | 10 years | Compliance archive, bulk export source |

Hot tier may enable **dual-write** to warm ClickHouse (`dual-write-enabled: true`) without changing read path (still JDBC until cutover).

---

## Default configuration

```yaml
ispf:
  historian:
    deploy-profile: three-tier
    tiers:
      hot:
        store: jdbc
        retention-days: 7
        min-interval-ms: 5000
        dual-write-enabled: true
      warm:
        store: clickhouse
        retention-days: 90
        clickhouse:
          url: http://localhost:8123
          database: ispf
          table: variable_samples
      cold:
        store: cold
        retention-days: 3650
        cold:
          provider: s3
          bucket: ispf-historian-archive
          prefix: variable-samples/
          format: parquet
```

Java binding: `HistorianTierProperties` + `HistorianTierProfile` (`com.ispf.server.config`).

---

## Deploy profile snippet (prod / VPS)

One-click **three-tier** profile for production historian at scale. Apply after ClickHouse is verified (`vps-clickhouse-verify.sh`).

```bash
# /opt/ispf/config/runtime-settings.properties (or env on systemd unit)
ISPF_HISTORIAN_DEPLOY_PROFILE=three-tier
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true
ISPF_VARIABLE_HISTORY_RETENTION_DAYS=7
ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL=http://127.0.0.1:8123
ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE=ispf
ISPF_HISTORIAN_TIER_HOT_RETENTION_DAYS=7
ISPF_HISTORIAN_TIER_WARM_RETENTION_DAYS=90
ISPF_HISTORIAN_TIER_COLD_BUCKET=ispf-historian-archive
ISPF_HISTORIAN_TIER_COLD_PREFIX=variable-samples/
```

**Lab-only (single tier):** set `ISPF_HISTORIAN_DEPLOY_PROFILE=hot-only`, `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false`, omit cold bucket.

**Warm query cutover:** set `ISPF_VARIABLE_HISTORY_STORE=clickhouse` only after dual-write soak and lab SLO gate — see [VARIABLE_HISTORY.md § SLO](VARIABLE_HISTORY.md#query-slo-bl-161).

---

## Ops checklist

1. Enable Timescale hypertable on `variable_samples` (automatic when extension present).
2. Deploy ClickHouse; run `vps-clickhouse-verify.sh`.
3. Set hot dual-write + tier retention env vars above.
4. Monitor CH append errors in server logs (secondary write is best-effort).
5. Schedule cold-tier export job (BL-163) when Parquet archive is required.

---

## Related roadmap

| BL | Topic |
|----|-------|
| BL-159 | This document + tier config |
| BL-161 | Query SLO — [VARIABLE_HISTORY.md](VARIABLE_HISTORY.md) |
| BL-162 | Event journal CH cutover |
| BL-163 | Trend export CSV/Parquet bulk |
