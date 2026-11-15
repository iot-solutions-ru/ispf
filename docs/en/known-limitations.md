> **Language:** Canonical English. Russian edition: [ru/known-limitations.md](../ru/known-limitations.md).

# Known limitations and non-claims

> **Status:** Stable — Honesty hub. Tags: [doc-status.md](doc-status.md).

Single page for what ISPF **does not claim** yet, what is **parked**, and what stays **deferred** on purpose. Use this with [competitive-scorecard](competitive-scorecard.md) and [parked-backlog](parked-backlog.md) — not instead of them.

Policy: [roadmap — quality over features](roadmap.md#execution-policy--quality-over-features).

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
| TOTP MFA (BL-153) | **Done** |
| WebAuthn / passkeys / IdP OTP (BL-194) | **Parked** — [ADR-0056](decisions/0056-webauthn-idp-mfa.md), [parked-backlog](parked-backlog.md) **P-WEBAUTHN** |
| External KMS / HSM / Vault / GOST providers | **Not claimed** until tender — [ADR-0076](decisions/0076-external-secrets-providers.md) |
| Third-party pen-test report (G-01) | **Parked** — vendor pack ready; [parked-backlog](parked-backlog.md) **P-PENTEST** |
| Cosign on customer host / separate ui-pack origin | Tooling lab ≠ customer-host Done — [adr-tension-resolution](adr-tension-resolution.md), [ADR-0073](decisions/0073-hosted-ui-pack-trust.md) |

---

## Field and integrations (parked)

Resume only with a **named** plant, customer, or vendor task:

| ID | Item |
|----|------|
| **P-OT** | OT Trust field pilots (lab soak stopped; not field Done) |
| **P-HMI-FIELD** | On-site tablet / offline soak |
| **P-ERP** | Live ERP connector (BL-169) — simulate catalog may exist |
| **P-BPMN** | BPMN/DMN depth beyond [ADR-0047](decisions/0047-custom-bpmn-subset-engine.md) freeze |
| **P-WEBAUTHN** | WebAuthn / IdP MFA |
| **P-PENTEST** | Hired pen-test + retest |

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

- [competitive-scorecard](competitive-scorecard.md) — scored honesty
- [parked-backlog](parked-backlog.md) — unpark criteria
- [adr-tension-resolution](adr-tension-resolution.md) — ADR vs claim conflicts
- [anti-patterns](anti-patterns.md) — field footguns
