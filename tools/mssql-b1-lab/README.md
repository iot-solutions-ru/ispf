# MSSQL B1 lab harness (ADR-0078)

Operator scripts for a **single-node** B1 metadata stand. Not a production deploy path.

| Script | Role |
|--------|------|
| `run.sh` | aarch64 lab: Azure SQL Edge + branch `bootJar` + soak + row-isolation fence |
| `stand-local.ps1` | AMD64: SQL Server 2022 Evaluation (`MSSQL_PID`, default Evaluation) + `bootJar` (Docker Desktop) |
| `gate-evidence.sh` | §8 evidence collector (`MSSQL_SA_PASSWORD` required). Signed B1 run: `gate-evidence-local-evaluation.json`. B2/A-A lab smoke: `gate-evidence-b2-aa-lab.json` |
| `load.sh` | Cert-bundle import + concurrent `by-path` soak |
| `loadtest.py` / `loadtest-run.sh` | Metadata RPS/latency (read / serial import / write / mixed) |

## Boot fences still enforced

- `ISPF_TENANT_DB_ROW_ISOLATION=false` (row isolation still refuses on MSSQL)
- `ISPF_TENANT_ISOLATION_MODE` must not be `HARD`

## B1 scope env (typical)

- `ISPF_METADATA_DB_KIND=mssql`
- JDBC history/journal and `ISPF_CLUSTER_ENABLED=true` are allowed (lab smoke; not Prod Yes for B2/A-A)

## Secrets

Set `MSSQL_SA_PASSWORD` (lab throwaway). Scripts refuse to start if unset.

## Notes

- Official `mcr.microsoft.com/mssql/server` is **amd64**; under QEMU on `ispf-lab` aarch64 it segfaults — use Azure SQL Edge there.
- Keep package **import serial** on Edge (concurrent import saturates Hikari pool=30).
- Ports bind to `127.0.0.1` by default (`BIND_HOST` to override).
- `mssql-b1-cert-bundle.json` mirrors `packages/ispf-server/src/test/resources/mssql-b1-cert-bundle.json` — keep them in sync.
