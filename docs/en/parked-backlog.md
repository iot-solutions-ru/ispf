# Parked backlog board (Post-S33)

> **Policy:** [roadmap — quality over features](roadmap.md#execution-policy--quality-over-features).  
> Parked items stay parked until a **named** field / integration / customer task. This board is the honest status — not a schedule.

| ID | Item | Status | Unpark criteria | Latest note |
|----|------|--------|-----------------|-------------|
| P-ENT-L | Enterprise L (50k history tags + 1B CH + multi-tag SLO) | **Consumed** (scorecard 2026-09-09) | — | Folded into Historian **9.5** on [competitive-scorecard](competitive-scorecard.md). Evidence: [`historian-scale/2026-09-05-…`](../evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md). Synthetic CH fill noted in scorecard honesty. |
| P-HMI-8H | HMI offline stretch **8 h** (lab CDP) | **Consumed** (scorecard 2026-09-09) | — | Folded into HMI **8.0** (with FPS evidence). CDP only — **P-HMI-FIELD** still required for 9+. Evidence: [`hmi-offline/2026-09-05-…`](../evidence/hmi-offline/2026-09-05-ispf-vps-0.9.207-offline-8h.json). |
| P-HMI-FIELD | On-site tablet / airplane-mode soak (2h min / 8h stretch) | **Parked** | Named site + tablet + journal | Playbook: [hmi-offline-field-soak.md](hmi-offline-field-soak.md). **Not** on the active roadmap queue (2026-11-24). |
| P-OT | Phase 25 OT Trust field pilots (BL-140…) | **Consumed (lab)** — soak **stopped** 2026-09-14 | Named plant soak + OT sign-off (field only) | Lab timers stopped; P1/P2/P3 JSON through 2026-09-14 all `pass: true`. [stop note](../evidence/ot-trust/2026-09-14-bl140-lab-soak-stopped.md) · [soak pull](../evidence/ot-trust/2026-09-14-bl140-lab-soak-pull.md). **Not** field Done / **not** OT 10/10. **Not** on the active roadmap queue (2026-11-24). |
| P-AI-FIELD | AI Autopilot field soak / soft &lt;15 min multi-day plant journal | **Parked** | Named site + live LLM secrets + journal | Lab harness Done (BL-177…180). Playbook: [ai-field-soak-playbook.md](ai-field-soak-playbook.md). Removed from Post-S33 active plan 2026-11-24. |
| P-MES-FIELD | MES production-site pilot on `mes-platform` (not lab smoke) | **Parked** | Named plant + integrator journal | Lab marketplace path Done. Playbook: [mes-field-pilot-playbook.md](mes-field-pilot-playbook.md). Removed from Post-S33 active plan 2026-11-24. |
| P-ERP | Live ERP connector (BL-169) | **Parked** | Named 1C/SAP integration | Sandbox / simulate catalog may exist; **live** round-trip is not claimed. |
| P-BPMN | BPMN/DMN depth beyond ADR-0047 | **Parked** | Named customer blocker + ADR | Freeze holds. |
| P-WEBAUTHN | WebAuthn / passkeys (BL-194) | **Parked** | Tender / customer MFA requirement | TOTP MFA GA remains the shipped path. ADR [0056](decisions/0056-webauthn-idp-mfa.md) explicitly **Parked** (2026-11-15). |
| P-MYSQL-SKU-RERUN | MySQL B1 and B2 checklist re-runs per named non-Community cell (Enterprise, HeatWave, RDS / Aurora MySQL, Azure Database for MySQL) | **Parked** | Named customer cell + host to run the [B1](mysql-sku-promotion-checklist.md) / [B2](mysql-b2-sku-promotion-checklist.md) checklists on | MySQL is **Prod Yes (B1 + opt-in B2)** only on self-managed Community Server 8.4.x with MariaDB Connector/J (owner extension, [ADR-0079](decisions/0079-mysql-metadata-certification.md)); this debt covers every other cell. MariaDB is never covered. |
| P-MYSQL-TLS | MySQL B1 gate re-run over TLS on the `ISPF_DB` connection (`sslMode=verify-full`, no `allowPublicKeyRetrieval`) | **Parked** (Prod Yes B1 signed without it) | First named MySQL host with a server certificate (the recommended named-host re-run) | TLS is operator-required and not certified by the B1 signature; the evidence cell ran without TLS, and its `allowPublicKeyRetrieval=true` is lab-only ([checklist](mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)). |
| P-MYSQL-PG-SQL | Review the remaining JDBC SQL for PostgreSQL-only constructs on MySQL; port or fence what the review finds | **Parked** (known non-claim) | Named MySQL deployment that needs an unreviewed path, or any wider MySQL claim | The constructs found at signing are fixed through `RelationalDialect` and run on MySQL in CI and in the gate: `PlatformJobService` (`(UUID) getObject` on `CHAR(36)`, `NULLS LAST`: queued report runs, `GET /api/v1/platform/metrics`) and `ApplicationSqlBindingStore` (`\|\|` trigger-list concat). No full SQL parity is claimed ([checklist](mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)). |
| P-MYSQL-AA-SOAK | MySQL A-A dual-JVM chaos soak / failover under load | **Parked** (Prod Yes signed; soak residual) | Named host + soak journal row | Dialect fence lifted with single-engine 2026-11-23 ([A-A checklist](mysql-aa-sku-promotion-checklist.md)). NATS still required. |
| P-ORACLE-SKU | Oracle B1 checklist run per named non-Free edition or managed service (Standard Edition 2, Enterprise Edition, Autonomous Database, Amazon RDS for Oracle) | **Parked** | Named customer edition + host to run the [checklist](oracle-sku-promotion-checklist.md) on | Oracle is **Prod Yes (B1)** only on self-managed Oracle Database Free 23.26.x with an operator-supplied `ojdbc17` (owner override, [ADR-0080](decisions/0080-oracle-metadata-certification.md)); this debt covers every other edition and managed service. The JDBC driver stays operator-supplied. |
| P-ORACLE-19C | Oracle B1 gate run on Oracle Database 19c | **Parked** (Prod Yes B1 signed without it) | Named 19c host, or a 19c database for the gate | The DDL and dialect SQL follow 19c rules (no `BOOLEAN`, `FROM DUAL`, no row limit with `FOR UPDATE`) but have run only on 23.26 Free ([checklist](oracle-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)). |
| P-ORACLE-VALIDATE | Hibernate `ddl-auto=validate` on Oracle | **Parked** (waived in writing, not fixed) | Oracle deployment that needs schema validation, or any wider Oracle claim | Not run on Oracle (`NUMBER(1)` columns against Hibernate's 23ai `BOOLEAN` mapping). Every Oracle deployment sets `SPRING_JPA_HIBERNATE_DDL_AUTO=none` ([checklist](oracle-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)). |
| P-ORACLE-EMPTY | Audit `''` writes into `NOT NULL` text columns on Oracle paths the gate does not exercise | **Parked** (known non-claim) | Named Oracle deployment that needs an unaudited path, or any wider Oracle claim | Oracle stores `''` as `NULL`; a `''` write into a `NOT NULL` text column fails with ORA-01400, loudly, not as a silent rewrite ([checklist](oracle-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)). |
| P-ORACLE-PG-SQL | Review the remaining JDBC SQL for PostgreSQL-only constructs on Oracle; port or fence what the review finds | **Parked** (known non-claim) | Named Oracle deployment that needs an unreviewed path, or any wider Oracle claim | Known on paths the gate does not exercise: the bucket aggregate and `CLOB` columns compared with `=` (ORA-22848). The job queue and function-success SQL-binding triggers run on Oracle in the opt-in Docker TCK. No full SQL parity is claimed ([checklist](oracle-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)). |
| P-ORACLE-TLS | Oracle B1 gate re-run over Oracle Net encryption or TLS with a wallet on the `ISPF_DB` connection | **Parked** (Prod Yes B1 signed without it) | First named Oracle host with encryption configured (the recommended named-host re-run) | Operator-required and not certified by the B1 signature; the evidence cell ran without Oracle Net encryption ([checklist](oracle-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)). |
| P-PENTEST | Third-party pen-test report (G-01) | **Parked** (vendor-pack ready) | Hired assessment + dated report + retest | **Send:** [outreach draft](pen-test-vendor-outreach-draft.md) · [vendor pack](pen-test-vendor-pack.md). SOW/RoE/RFP/prep/cases linked from pack; lab pin [2026-09-09 preflight](../evidence/security-pentest/2026-09-09-lab-192.168.100.10-preflight.md). Dogfood hold: [web-console-dogfood-checklist.md](web-console-dogfood-checklist.md). Docs ≠ cert. |
| P-MSSQL-AA-SOAK | MSSQL A-A AG failover + dual-JVM chaos journal | **Parked** (Prod Yes signed; soak residual) | Named AG stand + soak journal row | Dialect fence lifted 2026-11-22 ([A-A checklist](mssql-aa-sku-promotion-checklist.md)). `ispf-lab` SSH unreachable from signer host that day; Edge ≠ AG. |
| P-ORACLE-AA-SOAK | Oracle A-A dual-JVM chaos soak + failover under load on Free | **Parked** (Prod Yes signed; soak residual) | Named host soak journal + Free resource headroom | Dialect fence lifted 2026-11-23 ([A-A checklist](oracle-aa-sku-promotion-checklist.md)). Free 12 GB / CPU caps remain a non-preferred soak cell. |
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
