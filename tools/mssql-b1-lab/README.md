# MSSQL B1 / B2-core lab harness (ADR-0078)

Operator scripts for a **single-node** stand. Not a production deploy path.

| Script | Role |
|--------|------|
| `run.sh` | aarch64 lab: Azure SQL Edge + branch `bootJar` + soak + cluster fence |
| `stand-local.ps1` | AMD64 B1: SQL Server 2022 + `bootJar` (history/journal off) |
| `stand-b2-local.ps1` | AMD64 Gate B: same SQL + `ISPF_MSSQL_SINGLE_ENGINE=true` on DB `ispf_b2` (:18082) |
| `gate-evidence.sh` | B1 §8 evidence collector (`MSSQL_SA_PASSWORD` required). Signed B1: `gate-evidence-local-evaluation.json` |
| `gate-b2-core.ps1` | Gate B evidence (history write, DATE_BUCKET, journal JDBC, purge). Output: `gate-evidence-b2-core-evaluation.json` |
| `gate-evidence-b2-prod-yes-evaluation.json` | B2 re-sign gate (≤1 h wall-clock; ~20 s) for ADR-0037 **Prod Yes (B2)** — [B2 checklist](../../docs/en/mssql-b2-sku-promotion-checklist.md) |
| `gate-evidence-switch-dryrun-evaluation.json` | ADR-0081 lab SWITCH dry-run on Evaluation (APPLIED plan + pre-V101.2 refuse). Not §2b |
| `gate-evidence-switch-confirm-evaluation.json` | ADR-0081 lab SWITCH **confirm** on disposable `ispf_switch_confirm`. Product retention still DELETE; not product SWITCH |
| `split-variable-samples-horizon.sql` | Gate C1: SPLIT empty monthly horizon + alignment check (no SWITCH) |
| `switch-variable-samples-month.sql` | ADR-0081 lab: dry-run-default SWITCH+TRUNCATE of one oldest eligible UTC month (maintenance login; not product retention) |
| `load.sh` | Cert-bundle import + concurrent `by-path` soak |
| `loadtest.py` / `loadtest-run.sh` | Metadata RPS/latency (read / serial import / write / mixed) |

## B1 fences (required env)

- `ISPF_METADATA_DB_KIND=mssql`
- `ISPF_TENANT_DB_ROW_ISOLATION=false`
- `ISPF_VARIABLE_HISTORY_ENABLED=false` (or non-JDBC store)
- `ISPF_EVENT_JOURNAL_ENABLED=false` (or non-JDBC store)
- `ISPF_CLUSTER_ENABLED` optional (**Prod Yes A-A**); when true, NATS JetStream is required

Boot also refuses dual-write on MSSQL. JDBC history/journal need the opt-in single-engine profile ([examples/mssql-single-engine](../../examples/mssql-single-engine/README.md)). **Prod Yes (B2)** and **A-A** are signed ([B2](../../docs/en/mssql-b2-sku-promotion-checklist.md), [A-A](../../docs/en/mssql-aa-sku-promotion-checklist.md)); licensed SKUs owner-extended. Product retention stays app `DELETE` + `max-retention-days`. AG soak residual: **P-MSSQL-AA-SOAK**.

### ADR-0081 SWITCH lab script

```text
sqlcmd -S <host> -d ispf -U <maint> -P <pwd> -b -v MaxRetentionDays=90 -v DryRun=1 -v ConfirmSwitch=NO -i switch-variable-samples-month.sql
sqlcmd -S <host> -d ispf -U <maint> -P <pwd> -b -v MaxRetentionDays=90 -v DryRun=0 -v ConfirmSwitch=YES -i switch-variable-samples-month.sql
```

ODBC `sqlcmd` requires every `-v` variable used in the script (`MaxRetentionDays`, `DryRun`, `ConfirmSwitch`); otherwise it skips the batch. SSMS/JDBC without substitution leave the `$(…)` tokens and default to dry-run. `MaxRetentionDays` has no default — pass the live `ispf.relational.mssql.max-retention-days` ceiling. Staging `dbo.variable_samples_switch_staging` must already exist empty on `PRIMARY` (Flyway `V101.5`); the script refuses if missing, nonempty, or not identical to `variable_samples`. Mutation needs `DryRun=0` and exact `ConfirmSwitch=YES`. One oldest nonempty month whose exclusive end is `<= SYSUTCDATETIME() − MaxRetentionDays`. Not a chart cutover; not a seal from the rollup cursor.

## Secrets

Set `MSSQL_SA_PASSWORD` (lab throwaway). Scripts refuse to start if unset.

## Notes

- Official `mcr.microsoft.com/mssql/server` is **amd64**; under QEMU on `ispf-lab` aarch64 it segfaults — use Azure SQL Edge there.
- Keep package **import serial** on Edge (concurrent import saturates Hikari pool=30).
- Ports bind to `127.0.0.1` by default (`BIND_HOST` to override).
- `mssql-b1-cert-bundle.json` mirrors `packages/ispf-server/src/test/resources/mssql-b1-cert-bundle.json` — keep them in sync.
