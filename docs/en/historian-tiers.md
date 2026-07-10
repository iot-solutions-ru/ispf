# Historian tiers (BL-159, BL-202)

Turnkey **hot → warm → cold** profile. Config: `ispf.historian.tiers` in `application.yml`; tier routing when `deploy-profile=three-tier` (BL-202).

**See also:** [variable-history](variable-history.md), [0035-historian-dual-write](decisions/0035-historian-dual-write.md), [analytics-platform-roadmap](analytics-platform-roadmap.md), [analytics-historian-cookbook](analytics-historian-cookbook.md), [deployment](deployment.md).

---

## Tier model

| Tier | Default store | Retention | Role |
|------|---------------|-----------|------|
| **hot** | PostgreSQL / Timescale (`jdbc`) | 7 days | Live writes, operator trends, recent aggregates |
| **warm** | ClickHouse | 90 days | Analytics, long-range dashboards, dual-write target |
| **cold** | S3 + Parquet (`cold`) | 10 years | Compliance archive, bulk export source |

Hot tier may enable **dual-write** to warm ClickHouse (`dual-write-enabled: true`) while recent samples stay on JDBC; with tier routing enabled, aged samples route to warm automatically.

---

## Tier routing enforcement (BL-202)

When `ispf.historian.deploy-profile=three-tier` (default), the server enables **warm tier routing** unless `ispf.historian.tiers.warm.enabled` is set explicitly:

| Path | Behaviour |
|------|-----------|
| **Write** | `TierRoutingVariableHistoryWriteStore` — recent samples → JDBC hot; samples older than hot retention → ClickHouse warm; optional dual-write for hot window |
| **Read** | `TierRoutingVariableHistoryQueryStore` — ranges spanning hot cutoff merge JDBC + ClickHouse |
| **Cold** | Nightly `HistorianColdArchiveRunner` exports Parquet for the day leaving warm retention (when `ispf.historian.cold-archive.enabled=true`) |

`hot-only` profile keeps JDBC-only path (`warm.enabled=false`).

Java: `HistorianTierDeployProfileEnvironmentPostProcessor`, `HistorianColdArchiveService`.

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
ISPF_HISTORIAN_TIER_HOT_RETENTION_DAYS=7
ISPF_HISTORIAN_TIER_WARM_RETENTION_DAYS=90
ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL=http://127.0.0.1:8123
ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE=ispf
ISPF_HISTORIAN_TIER_COLD_BUCKET=ispf-historian-archive
ISPF_HISTORIAN_TIER_COLD_PREFIX=variable-samples/
# Optional: enable nightly Parquet export (local tree or S3-mounted path)
ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=true
ISPF_HISTORIAN_COLD_ARCHIVE_LOCAL_ROOT=/var/lib/ispf/historian-cold
```

`three-tier` sets `ispf.historian.tiers.warm.enabled=true` automatically. Override with `ISPF_HISTORIAN_TIER_WARM_ENABLED=false` if needed.

**Lab-only (single tier):** set `ISPF_HISTORIAN_DEPLOY_PROFILE=hot-only` — JDBC-only, no warm routing.

**Warm read cutover (full CH primary):** set `ISPF_VARIABLE_HISTORY_STORE=clickhouse` only after dual-write soak and lab SLO gate — see [variable-history](variable-history.md). Tier routing is bypassed when store is `clickhouse`.

---

## Ops checklist

1. Enable Timescale hypertable on `variable_samples` (automatic when extension present).
2. Deploy ClickHouse; run `vps-clickhouse-verify.sh`.
3. Set `ISPF_HISTORIAN_DEPLOY_PROFILE=three-tier` and tier retention env vars above.
4. Monitor CH append errors in server logs (warm path is best-effort on dual-write).
5. Enable `ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=true` when Parquet archive is required; sync `local-root` tree to S3 or mount bucket path. Cold **query** via Athena/Trino is out of scope (export-only in BL-202).

---

## Related roadmap

| BL | Topic |
|----|-------|
| BL-159 | Tier config model |
| BL-202 | Tier routing enforcement + cold Parquet export (this doc) |
| BL-161 | Query SLO — [variable-history](variable-history.md) |
| BL-201 | AF-lite templates — [reference-asset-analytics](reference-asset-analytics.md) |
| BL-163 | On-demand trend export CSV/Parquet |
