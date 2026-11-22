# Parked backlog board (Post-S33)

> **Policy:** [roadmap — quality over features](roadmap.md#execution-policy--quality-over-features).  
> Parked items stay parked until a **named** field / integration / customer task. This board is the honest status — not a schedule.

| ID | Item | Status | Unpark criteria | Latest note |
|----|------|--------|-----------------|-------------|
| P-ENT-L | Enterprise L (50k history tags + 1B CH + multi-tag SLO) | **Consumed** (scorecard 2026-09-09) | — | Folded into Historian **9.5** on [competitive-scorecard](competitive-scorecard.md). Evidence: [`historian-scale/2026-09-05-…`](../evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md). Synthetic CH fill noted in scorecard honesty. |
| P-HMI-8H | HMI offline stretch **8 h** (lab CDP) | **Consumed** (scorecard 2026-09-09) | — | Folded into HMI **8.0** (with FPS evidence). CDP only — **P-HMI-FIELD** still required for 9+. Evidence: [`hmi-offline/2026-09-05-…`](../evidence/hmi-offline/2026-09-05-ispf-vps-0.9.207-offline-8h.json). |
| P-HMI-FIELD | On-site tablet / airplane-mode soak (2h min / 8h stretch) | **Parked** | Named site + tablet + journal | Playbook: [hmi-offline-field-soak.md](hmi-offline-field-soak.md). |
| P-OT | Phase 25 OT Trust field pilots (BL-140…) | **Consumed (lab)** — soak **stopped** 2026-09-14 | Named plant soak + OT sign-off (field only) | Lab timers stopped; P1/P2/P3 JSON through 2026-09-14 all `pass: true`. [stop note](../evidence/ot-trust/2026-09-14-bl140-lab-soak-stopped.md) · [soak pull](../evidence/ot-trust/2026-09-14-bl140-lab-soak-pull.md). **Not** field Done / **not** OT 10/10. |
| P-ERP | Live ERP connector (BL-169) | **Parked** | Named 1C/SAP integration | Sandbox / simulate catalog may exist; **live** round-trip is not claimed. |
| P-BPMN | BPMN/DMN depth beyond ADR-0047 | **Parked** | Named customer blocker + ADR | Freeze holds. |
| P-WEBAUTHN | WebAuthn / passkeys (BL-194) | **Parked** | Tender / customer MFA requirement | TOTP MFA GA remains the shipped path. ADR [0056](decisions/0056-webauthn-idp-mfa.md) explicitly **Parked** (2026-11-15). |
| P-MYSQL-SKU-RERUN | MySQL B1 checklist re-run per named non-Community cell (Enterprise, HeatWave, RDS / Aurora MySQL, Azure Database for MySQL) | **Parked** | Named customer cell + host to run the [checklist](mysql-sku-promotion-checklist.md) on | MySQL is **Prod Yes (B1)** only on self-managed Community Server 8.4.x with MariaDB Connector/J (owner extension, [ADR-0079](decisions/0079-mysql-metadata-certification.md)); this debt covers every other cell. MariaDB is never covered. |
| P-MYSQL-TLS | MySQL B1 gate re-run over TLS on the `ISPF_DB` connection (`sslMode=verify-full`, no `allowPublicKeyRetrieval`) | **Parked** (Prod Yes B1 signed without it) | First named MySQL host with a server certificate (the recommended named-host re-run) | TLS is operator-required and not certified by the B1 signature; the evidence cell ran without TLS, and its `allowPublicKeyRetrieval=true` is lab-only ([checklist](mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)). |
| P-MYSQL-PG-SQL | Port or fence PostgreSQL-only SQL on MySQL paths the B1 gate does not exercise | **Parked** (known non-claim) | Named MySQL deployment that needs an affected path, or any wider MySQL claim | Read from the code, not run: `PlatformJobService` is expected to break queued report runs and `GET /api/v1/platform/metrics` (`(UUID) getObject` on `CHAR(36)`, `NULLS LAST`); `ApplicationSqlBindingStore` `\|\|` concat is expected to over-match function-success triggers. No full SQL parity is claimed ([checklist](mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)). |
| P-PENTEST | Third-party pen-test report (G-01) | **Parked** (vendor-pack ready) | Hired assessment + dated report + retest | **Send:** [outreach draft](pen-test-vendor-outreach-draft.md) · [vendor pack](pen-test-vendor-pack.md). SOW/RoE/RFP/prep/cases linked from pack; lab pin [2026-09-09 preflight](../evidence/security-pentest/2026-09-09-lab-192.168.100.10-preflight.md). Dogfood hold: [web-console-dogfood-checklist.md](web-console-dogfood-checklist.md). Docs ≠ cert. |
| P-MSSQL-AA-SOAK | MSSQL A-A AG failover + dual-JVM chaos journal | **Parked** (Prod Yes signed; soak residual) | Named AG stand + soak journal row | Dialect fence lifted 2026-11-22 ([A-A checklist](mssql-aa-sku-promotion-checklist.md)). `ispf-lab` SSH unreachable from signer host that day; Edge ≠ AG. |
| P-MSSQL-SKU-RERUN | Per-SKU checklist re-run (Standard / Azure SQL DB\|MI) | **Parked** (owner-extended Yes) | Named licensed / PaaS stand + gate JSON | Evaluation engine-class evidence extended by owner 2026-11-22 ([sku evidence](../../tools/mssql-b1-lab/gate-evidence-sku-extension-evaluation.json)). |

## Lab access (operators)

Private lab used for Enterprise L:

| Role | Address | Notes |
|------|---------|-------|
| Jump | `84.42.21.226:5031` | SSH user provided out-of-band |
| ISPF/DB node | `192.168.100.10` | Passwordless SSH **from jump**; Postgres `:5432`, ClickHouse `:8123`, ISPF `:8080` when lab stack is up |

**Do not commit passwords or tokens.** Rotate if exposed in chat logs.

## What “done” means here

- **Lab PASS** closes the *measurement* gap for scorecard inputs; scores move only on a **named full competitive audit** ([competitive-scorecard](competitive-scorecard.md) — last: 2026-09-09 → ~8.0/10).
- **Consumed** means that Lab PASS was folded into the scorecard; residual field gaps stay on other parked IDs (e.g. **P-HMI-FIELD**).
- **Parked** means no active delivery plan — only resume with a named task.
- **In progress** means automation is running; attach evidence when the run finishes.

RU: [parked-backlog.md](../ru/parked-backlog.md)
