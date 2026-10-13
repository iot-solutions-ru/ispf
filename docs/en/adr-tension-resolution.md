# ADR tension resolution

> **Status:** Charter — claim-boundary program. Hub: [doc-status.md](doc-status.md).  
> Russian: [ru/adr-tension-resolution.md](../ru/adr-tension-resolution.md).

Program to remove contradictions between Accepted ADRs, `AGENTS.md`, and scorecard claims. Goal: **one claim cell → one answer**, not “all scorecard rows ≥ 9.5”.

Source audit: canvas `adr-review-best-in-class` (TENSIONS → RESOLUTION).

## Resolution table

| Tension | Status | Owner ADR | Wave | Done when |
|---------|--------|-----------|------|-----------|
| Secrets key / envelope | **Closed** | [0065](decisions/0065-secrets-key-rotation.md) | — | Envelope + rotation shipped; KMS → [0076](decisions/0076-external-secrets-providers.md) non-claim |
| FIELD=0 vs marketplace pilots | **Closed** | [0067](decisions/0067-field-certified-maturity.md) §1b | — | REAL catalog ≠ driver FIELD; scorecard must not mix lanes |
| Poka-yoke vs WARNING | **Closed** (Wave 1A) | [0060](decisions/0060-solution-authoring-constraints.md) × [0051](decisions/0051-poka-yoke-constraints-over-guards.md) | 1A | `ispf validate --strict` + catalog + agent ship acceptance treat `LOGIC_HOST_DEVICE` as ERROR / judge REWORK; authoring default stays WARNING. ship-default HTTP deploy without `strict` remains **Partial** ([0060](decisions/0060-solution-authoring-constraints.md)) |
| Knobs vs profiles | **Closed** (Wave 1B) | [0033](decisions/0033-prod-idle-demostand-tuning.md) × [0026](decisions/0026-elastic-telemetry-ingress.md) | 1B | Effective-config API + profile-first docs ([#493](https://github.com/iot-solutions-ru/ispf/pull/493)) |
| Silent cluster degradation | **Closed** (Wave 1C) | [0069](decisions/0069-cluster-consistency.md) × [0029](decisions/0029-cluster-live-variable-replica-sync.md)/[0030](decisions/0030-cluster-config-structure-replica-sync.md) | 1C | Fail-closed JetStream/Redis ([#494](https://github.com/iot-solutions-ru/ispf/pull/494)) |
| Ui-pack on console origin | **Closed** (Wave 2A Partial) | [0073](decisions/0073-hosted-ui-pack-trust.md) × [0054](decisions/0054-hosted-ui-packs.md) | 2A | CSP + scoped deny Partial ([#495](https://github.com/iot-solutions-ru/ispf/pull/495)); full partner token mint may remain Gap per 0073 |
| Five time-series stores | **Closed** (Wave 2B) | [0074](decisions/0074-historian-quality-persistence.md) × [0025b](decisions/0025-cassandra-scylla-timeseries-store.md)/[0035](decisions/0035-historian-dual-write.md) | 2B | jdbc + ClickHouse quality (CH TCK opt-in, not default CI) + dual-write `secondary_failures`; Scylla experimental ([#496](https://github.com/iot-solutions-ru/ispf/pull/496)) |
| Breadth vs depth (OT) | **Planned** | [0067](decisions/0067-field-certified-maturity.md) × [0022](decisions/0022-driver-production-matrix.md) | 3A | **Done when (ops):** first customer-plant C1 pack complete per [field-c1-evidence-checklist](field-c1-evidence-checklist.md) + `FIELD_EVIDENCE_PATHS` matrix row merged (**FIELD=0** until then). Wave 3A ops shipped: [docs/evidence/field-c1/](../evidence/field-c1/README.md) template |
| External KMS / GOST | **Closed** (Wave 3B policy) | [0076](decisions/0076-external-secrets-providers.md) | 3B | Policy non-claim without code: provider SPI **only** under named tender; envelope stays **Closed** on [0065](decisions/0065-secrets-key-rotation.md) |

## Wave summary

| Wave | Kind | Deliverables |
|------|------|--------------|
| **0** | Docs | This page, canvas RESOLUTION, `AGENTS.md` / principles align with 0060 modes |
| **1A** | Code | `--strict` validate, catalog gate, `hostKind` poka-yoke, agent ship-canon acceptance (HTTP deploy without `strict` still WARNING) |
| **1B** | Code | Effective configuration surface + 0033 docs (**shipped**) |
| **1C** | Code | Cluster fail-closed JetStream/Redis |
| **2A** | Code | Ui-pack CSP + scoped token |
| **2B** | Code | Historian quality TCK (jdbc **Done**, ClickHouse container **Done** opt-in) + dual-write secondary failure counter |
| **3A** | Ops | [field-c1-evidence-checklist](field-c1-evidence-checklist.md) + [evidence/field-c1/](../evidence/field-c1/README.md) template (FIELD=0 until named plant pack + matrix PR) |
| **3B** | Policy | [0076](decisions/0076-external-secrets-providers.md) non-claim closed; SPI deferred to named tender |

## Logic-host enforcement modes (Wave 0 align)

| Mode | Behaviour | Where |
|------|-----------|--------|
| **Canon (hard rule)** | Logic hosts are SINGLETON or INSTANCE, never DEVICE | `AGENTS.md`, application-principles |
| **Authoring validate/deploy** | `LOGIC_HOST_DEVICE` = **WARNING** (does not alone set `status: ERROR`) | Default bundle validate — [0060](decisions/0060-solution-authoring-constraints.md) |
| **Ship / examples / catalog / agent acceptance** | Same codes = **ERROR**; judge **REWORK** | `ExamplesBundleValidationTest`; `ispf validate --strict`; marketplace catalog; `deploy_step_validate` / `deploy_step_dry_run` / `import_package` / `deploy_step_import` (ADR-0060 ship-default, **Partial** — raw HTTP deploy without `strict` stays WARNING) |

## Wave 3A — C1 FIELD checklist (ops)

Full checklist: [field-c1-evidence-checklist.md](field-c1-evidence-checklist.md). Evidence folder template: [docs/evidence/field-c1/README.md](../evidence/field-c1/README.md).

Do not promote a driver to FIELD until all checklist sections pass for a **named customer plant** ([field-pilot-playbook](field-pilot-playbook.md), [0067](decisions/0067-field-certified-maturity.md) §1b). Marketplace REAL installs and lab VLAN soaks never substitute.

**Wave 3A docs status:** checklist + template **Done**; matrix FIELD count remains **0** until the first plant pack and code path above land.

## Related

- [decisions/readme.md](decisions/readme.md)
- [competitive-scorecard](competitive-scorecard.md)
- [0060](decisions/0060-solution-authoring-constraints.md), [0067](decisions/0067-field-certified-maturity.md), [0069](decisions/0069-cluster-consistency.md), [0073](decisions/0073-hosted-ui-pack-trust.md)
