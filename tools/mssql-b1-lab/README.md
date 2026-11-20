# MSSQL B1 lab harness (ADR-0078)

Operator scripts for a **single-node** B1 metadata stand. Not a production deploy path.

| Script | Role |
|--------|------|
| `run.sh` | aarch64 lab: Azure SQL Edge + branch `bootJar` + soak + cluster fence |
| `stand-local.ps1` | AMD64: SQL Server 2022 + `bootJar` (Docker Desktop) |
| `load.sh` | Cert-bundle import + concurrent `by-path` soak |
| `loadtest.py` / `loadtest-run.sh` | Metadata RPS/latency (read / serial import / write / mixed) |

## B1 fences (required env)

- `ISPF_METADATA_DB_KIND=mssql`
- `ISPF_TENANT_DB_ROW_ISOLATION=false`
- `ISPF_VARIABLE_HISTORY_ENABLED=false` (or non-JDBC store)
- `ISPF_EVENT_JOURNAL_ENABLED=false` (or non-JDBC store)
- `ISPF_CLUSTER_ENABLED=false`

## Secrets

Set `MSSQL_SA_PASSWORD` (lab throwaway). Scripts refuse to start if unset.

## Notes

- Official `mcr.microsoft.com/mssql/server` is **amd64**; under QEMU on `ispf-lab` aarch64 it segfaults — use Azure SQL Edge there.
- Keep package **import serial** on Edge (concurrent import saturates Hikari pool=30).
- Ports bind to `127.0.0.1` by default (`BIND_HOST` to override).
- `mssql-b1-cert-bundle.json` mirrors `packages/ispf-server/src/test/resources/mssql-b1-cert-bundle.json` — keep them in sync.
