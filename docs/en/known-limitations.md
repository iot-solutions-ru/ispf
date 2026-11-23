> **Language:** Canonical English. Russian edition: [ru/known-limitations.md](../ru/known-limitations.md).

# Known limitations and non-claims

> **Status:** Stable — Honesty hub. Tags: [doc-status.md](doc-status.md).

Single page for what ISPF does not claim yet, what is parked, and what stays deferred on purpose. If the server will not start, sign-in fails, or the demo sensor is silent, use [Troubleshooting](troubleshooting.md) first. Use this page with [competitive-scorecard](competitive-scorecard.md) and [parked-backlog](parked-backlog.md).

Policy: [roadmap — quality over features](roadmap.md#execution-policy--quality-over-features).

The short IDs in the parked table (`P-OT`, `P-WEBAUTHN`, and the rest) are the labels on that board, so a later task finds the same row.

## On an evaluation laptop

| You get this | You do not get this |
|--------------|---------------------|
| Portable zip, container, and `bootRun` load the small driver set (`virtual`, `modbus`, `mqtt`, `http`, `jdbc`, and the rest of that set) | OPC UA, SNMP, and the rest of the 160+ catalog |
| The in-product AI agent sees **loaded** drivers (and says so when the pack catalog is larger) | An answer that invents the full 160+ list on a laptop without those packs |
| Local profile: embedded H2 and the accounts `admin` / `admin`, `developer` / `developer`, `operator` / `operator` | Those passwords on a `prod` or OIDC server. Change them before the machine is reachable on a network |
| History on the laptop is that local database | A ClickHouse cluster created by ISPF, or a second copy of the historian buffer on another ISPF node |
| Relational core on PostgreSQL, H2 local, or MSSQL **B1 + B2 + A-A** (Prod Yes on Evaluation + owner-extended licensed Standard / Enterprise / Azure SQL DB\|MI; [B2](mssql-b2-sku-promotion-checklist.md), [A-A](mssql-aa-sku-promotion-checklist.md)). External JDBC `DATA_SOURCE` for reports | A perpetual license *from* the Evaluation trial itself, a 10-year MSSQL raw archive, multi-year ≤20 s scans, product/scheduled `SWITCH` retention, completed AG failover soak (tech-debt **P-MSSQL-AA-SOAK**), Oracle metadata ([0078](decisions/0078-mssql-metadata-certification.md)) |
| MySQL Community Server 8.4.x **B1 metadata — Prod Yes** under the B1 fences; opt-in **B2 single-engine — Prod Yes** (`ISPF_MYSQL_SINGLE_ENGINE=true`: JDBC history and journal in the same database, retention `DELETE` capped at 90 days by default); **A-A — Prod Yes** with single-engine + cluster (NATS required; soak **P-MYSQL-AA-SOAK**); client MariaDB Connector/J only, `jdbc:mysql://…?permitMysqlScheme=true` ([B1](mysql-sku-promotion-checklist.md), [B2](mysql-b2-sku-promotion-checklist.md), [A-A](mysql-aa-sku-promotion-checklist.md)) | Prod Yes on MySQL Enterprise, HeatWave, RDS / Aurora, Azure Database for MySQL, or any other non-Community cell (**P-MYSQL-SKU-RERUN**); cluster without single-engine; completed multi-hour A-A soak; row isolation or dual-write on MySQL; JDBC history/journal without the single-engine flag; rollups, partitions or a file buffer for the MySQL historian; MariaDB as `ISPF_DB`; any other MySQL JDBC client; TLS on the MySQL `ISPF_DB` connection (operator-required, not gated — **P-MYSQL-TLS**); full SQL parity with PostgreSQL outside the paths the gates and the MySQL TCKs exercise (**P-MYSQL-PG-SQL**) ([0079](decisions/0079-mysql-metadata-certification.md)) |
| Oracle Database Free 23.26.x **B1 metadata — Prod Yes** under the B1 fences, with an Oracle JDBC driver you add yourself (`ojdbc17` on `loader.path`) and `SPRING_JPA_HIBERNATE_DDL_AUTO=none` ([checklist](oracle-sku-promotion-checklist.md)) | The Oracle JDBC driver inside ISPF; Prod Yes on Standard Edition 2, Enterprise Edition, Autonomous Database, Amazon RDS for Oracle, or any other non-Free edition or managed service (**P-ORACLE-SKU**); 19c (**P-ORACLE-19C**); Hibernate `ddl-auto=validate` on Oracle (**P-ORACLE-VALIDATE**); load past Free's resource caps; `''` stored as text (Oracle stores `NULL`; paths outside the gate not audited — **P-ORACLE-EMPTY**); full SQL parity with PostgreSQL outside the paths the B1 gate and the Oracle TCKs exercise (**P-ORACLE-PG-SQL**); TLS on the Oracle `ISPF_DB` connection (operator-required, not gated — **P-ORACLE-TLS**); JDBC history/journal, cluster, or row isolation on Oracle; PostgreSQL → Oracle data migration ([0080](decisions/0080-oracle-metadata-certification.md)) |
| App SQL schemas / `search_path` on PostgreSQL | Per-app schema switch on MSSQL, MySQL, or Oracle (`ApplicationSchemaSession`) — **unsupported**, fail-closed ([0078](decisions/0078-mssql-metadata-certification.md), [0079](decisions/0079-mysql-metadata-certification.md), [0080](decisions/0080-oracle-metadata-certification.md)) |
| PG-flavored solution `exec` / `ON CONFLICT` scripts against `ISPF_DB` | Same scripts on MSSQL, MySQL, or Oracle metadata — **unsupported**; use external `DATA_SOURCE` or dialect-safe SQL only |
| Disk spool in front of sole-store ClickHouse exists and is off | The ClickHouse spool turning itself on, applying to JDBC/dual-write, or a second copy of that buffer on another node. An opt-in MSSQL file buffer (`ISPF_MSSQL_JDBC_BUFFER`) is a different switch, off by default, and is not a backup |
| Opt-in MSSQL single-engine JDBC history (**Prod Yes B2**) with a 90-day retention ceiling; Gate C1 monthly partitions on empty DBs; opt-in rollups; ADR-0081 lab SWITCH + `V101.5` staging; **A-A** dialect fence lifted (NATS still required) | Unbounded multi-year raw scans, product/scheduled monthly `SWITCH`, default-on chart cutover, automatic buffer replay, JPA history on MSSQL. AG dual-JVM chaos journal still tech-debt. Nonempty DBs stay `SKIPPED_NONEMPTY` + DELETE cap |
| `ispf validate` warns when logic sits on a `DEVICE` | `--strict` or the marketplace catalog accepting that bundle |
| TOTP can be turned on for login | WebAuthn or passkeys as the login method |

---

## Defaults and operator hygiene

| Topic | Reality |
|-------|---------|
| Default credentials | Demo / getting-started paths may use `admin` / `admin`. **Change before any network exposure** — [security](security.md), [deployment](deployment.md). |
| Unsigned bundles / RBAC off / WS origins `*` | `dev`/default **warn** by default; `application-prod.yml` sets `ispf.security.strict-startup=true` (refuse boot on RBAC off / role header / WS `*`). `local`/`test` skip `StartupSecurityGuard` entirely (no warn; env flag ignored) — Docker/Helm/`local` VPS paths must leave `local` (e.g. `prod`, or non-`local` + `ISPF_SECURITY_STRICT_STARTUP=true`) for strict to fire. Unsigned bundles still warn unless `require-signed-bundles=true`. Entrypoint table: [deployment](deployment.md#shipped-entrypoints-and-profiles). |
| Java functions | Keep `ISPF_FUNCTION_JAVA_ENABLED=false` unless you accept sandbox gaps ([ADR-0045](decisions/0045-java-function-sandbox.md)). |

---

## Historian

| Claim | Status |
|-------|--------|
| JDBC + dual-write catch-up + sole-store ClickHouse | **Shipped** — [variable-history](variable-history.md), [ADR-0035](decisions/0035-historian-dual-write.md) |
| Local disk spool in front of sole-store CH (`ispf.variable-history.spool`) | **Shipped**, **off by default** — [variable-history](variable-history.md) § Disk spool |
| Dual-write catch-up gauges + lab Prometheus alert rules | **Shipped** — `…catchup.ranges` / `.oldest_age_ms` (sample-clock age of open range) / `.wal_bytes`; not a CH-vs-PG lag series ([ADR-0035](decisions/0035-historian-dual-write.md)) |
| True ClickHouse-behind-primary lag metric | **Open** — do not treat `oldest_age_ms` as CH lag |
| Second copy of historian buffer on another ISPF node before accept | **Deferred** — do not build without an explicit ask |
| ISPF creating / managing a ClickHouse cluster | **Deferred** — operator owns CH replication |
| Parquet / Cassandra-Scylla `quality` parity with flagship stores | **Gap / experimental** — [ADR-0074](decisions/0074-historian-quality-persistence.md) |

---

## Security and trust

| Claim | Status |
|-------|--------|
| TOTP one-time codes for login | **Done** |
| WebAuthn / passkeys / identity-provider OTP | **Parked** — [ADR-0056](decisions/0056-webauthn-idp-mfa.md), [parked-backlog](parked-backlog.md) **P-WEBAUTHN** |
| External KMS / HSM / Vault / GOST providers | **Not claimed** until a named tender — [ADR-0076](decisions/0076-external-secrets-providers.md) |
| A third-party penetration-test report | **Parked** — the vendor pack is ready; [parked-backlog](parked-backlog.md) **P-PENTEST** |
| Cosign on customer host / separate ui-pack origin | Tooling lab ≠ customer-host Done — [adr-tension-resolution](adr-tension-resolution.md), [ADR-0073](decisions/0073-hosted-ui-pack-trust.md) |

---

## Field and integrations (parked)

Resume only with a **named** plant, customer, or vendor task:

| ID | Item |
|----|------|
| **P-OT** | OT Trust field pilots (lab soak stopped; not field Done) |
| **P-HMI-FIELD** | On-site tablet / offline soak |
| **P-ERP** | Live ERP connector — a simulate catalog may exist |
| **P-BPMN** | BPMN/DMN depth beyond [ADR-0047](decisions/0047-custom-bpmn-subset-engine.md) freeze |
| **P-WEBAUTHN** | WebAuthn / IdP MFA |
| **P-PENTEST** | Hired pen-test + retest |
| **P-MYSQL-SKU-RERUN** | MySQL checklist re-runs (B1 and B2) per named Enterprise / managed / non-Community cell |
| **P-MYSQL-TLS** | MySQL gate re-runs (B1 and B2) over TLS; TLS is operator-required, not certified |
| **P-MYSQL-PG-SQL** | Review the remaining JDBC SQL for PostgreSQL-only constructs on MySQL; the ones found at signing (queued report runs, platform metrics, SQL-binding triggers) are fixed |
| **P-MYSQL-AA-SOAK** | MySQL A-A dual-JVM chaos soak and failover under load; Prod Yes (A-A) is signed and NATS is required |
| **P-ORACLE-SKU** | Oracle checklist on a named non-Free edition (19c or 23ai/26ai) before any Prod Yes |

Board: [parked-backlog](parked-backlog.md).

---

## OT / drivers

| Claim | Status |
|-------|--------|
| DNP3 | **PRODUCTION** `POLL` + `WRITE` — CROB latch `g12v1` + analog float `g41v3` (direct operate default; optional `commandMode=sbo`). Pulse / trip-close / full control profile **not** implemented; `BINARY_INPUT` / `ANALOG_INPUT` / `COUNTER` stay read-only — [ADR-0057](decisions/0057-ot-trust-wave1-dnp3-poll-only.md) update 2026-11-11 |
| `opc-da` / `opc-bridge` | **BETA** shells |

---

## Authoring and ship

| Topic | Reality |
|-------|---------|
| Logic on `ObjectType.DEVICE` | Authoring default **WARNING**; ship / `--strict` / marketplace **ERROR** — [anti-patterns](anti-patterns.md), [ADR-0060](decisions/0060-solution-authoring-constraints.md) |
| Heavy widget expressions | Bundle warn `HEAVY_WIDGET_EXPRESSION` |
| Product operator UI in this repo | Industry apps ship as **ui-pack** + BFF — not pages inside `apps/web-console` — [external-ide](external-ide.md) |

---

## DR and cluster evidence

| Topic | Reality |
|-------|---------|
| Backup / DR tiers | Documented in [ADR-0068](decisions/0068-backup-and-dr.md). Lab T2 dump/restore drill exists (`deploy/tools/dr-restore-drill.sh`, [evidence](../evidence/dr-restore/README.md)); site/customer DR + dated field transcripts are still what remain missing — do not oversell |
| Cluster dual-owner / federation load | JDBC lease exclusivity CI shipped (`DriverOwnershipServiceTest`); multi-node dual-I/O and federation 10+ peer load remain open — [ADR-0069](decisions/0069-cluster-consistency.md) · lab ≠ field |

---

## Related

- [Troubleshooting](troubleshooting.md) — first session will not start
- [competitive-scorecard](competitive-scorecard.md) — scored honesty
- [parked-backlog](parked-backlog.md) — unpark criteria
- [adr-tension-resolution](adr-tension-resolution.md) — ADR vs claim conflicts
- [anti-patterns](anti-patterns.md) — field footguns
