# MSSQL B1 / B2-core lab harness (ADR-0078)

Operator scripts for a **single-node** stand. Not a production deploy path.

| Script | Role |
|--------|------|
| `run.sh` | aarch64 lab: Azure SQL Edge + branch `bootJar` + soak + cluster fence |
| `stand-local.ps1` | AMD64 B1: SQL Server 2022 + `bootJar` (history/journal off) |
| `stand-b2-local.ps1` | AMD64 Gate B: same SQL + `ISPF_MSSQL_SINGLE_ENGINE=true` on DB `ispf_b2` (:18082) |
| `gate-evidence.sh` | B1 §8 evidence collector (`MSSQL_SA_PASSWORD` required). Signed B1: `gate-evidence-local-evaluation.json` |
| `gate-b2-core.ps1` | Gate B evidence (history write, DATE_BUCKET, journal JDBC, purge). Output: `gate-evidence-b2-core-evaluation.json` |
| `load.sh` | Cert-bundle import + concurrent `by-path` soak |
| `loadtest.py` / `loadtest-run.sh` | Metadata RPS/latency (read / serial import / write / mixed) |

## B1 fences (required env)

- `ISPF_METADATA_DB_KIND=mssql`
- `ISPF_TENANT_DB_ROW_ISOLATION=false`
- `ISPF_VARIABLE_HISTORY_ENABLED=false` (or non-JDBC store)
- `ISPF_EVENT_JOURNAL_ENABLED=false` (or non-JDBC store)
- `ISPF_CLUSTER_ENABLED=false`

Boot also refuses dual-write on MSSQL. JDBC history/journal need the opt-in single-engine profile ([examples/mssql-single-engine](../../examples/mssql-single-engine/README.md)). Gate B lab ≠ ADR-0037 **Prod Yes (B2)** (Gate C archive still open). Active-active stays refused.

## Secrets

Set `MSSQL_SA_PASSWORD` (lab throwaway). Scripts refuse to start if unset.

## Notes

- Official `mcr.microsoft.com/mssql/server` is **amd64**; under QEMU on `ispf-lab` aarch64 it segfaults — use Azure SQL Edge there.
- Keep package **import serial** on Edge (concurrent import saturates Hikari pool=30).
- Ports bind to `127.0.0.1` by default (`BIND_HOST` to override).
- `mssql-b1-cert-bundle.json` mirrors `packages/ispf-server/src/test/resources/mssql-b1-cert-bundle.json` — keep them in sync.
