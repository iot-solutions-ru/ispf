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
| Poka-yoke vs WARNING | **Closed** (Wave 1A) | [0060](decisions/0060-solution-authoring-constraints.md) × [0051](decisions/0051-poka-yoke-constraints-over-guards.md) | 1A | `ispf validate --strict` + catalog gate treat `LOGIC_HOST_DEVICE` as ERROR; authoring default stays WARNING |
| Knobs vs profiles | **Closed** (Wave 1B) | [0033](decisions/0033-prod-idle-demostand-tuning.md) × [0026](decisions/0026-elastic-telemetry-ingress.md) | 1B | Effective-config API exposes resolved profile; demostand docs prefer profiles |
| Silent cluster degradation | **Planned → Wave 1C** | [0069](decisions/0069-cluster-consistency.md) × [0029](decisions/0029-cluster-live-variable-replica-sync.md)/[0030](decisions/0030-cluster-config-structure-replica-sync.md) | 1C | Cluster start fail-closed without JetStream (structure) / Redis when live mirror required |
| Ui-pack on console origin | **Planned → Wave 2A** | [0073](decisions/0073-hosted-ui-pack-trust.md) × [0054](decisions/0054-hosted-ui-packs.md) | 2A | CSP on `/apps/`; partner pack cannot mint full-privilege console token |
| Five time-series stores | **Planned → Wave 2B** | [0074](decisions/0074-historian-quality-persistence.md) × [0025b](decisions/0025-cassandra-scylla-timeseries-store.md)/[0035](decisions/0035-historian-dual-write.md) | 2B | Quality TCK green on jdbc + ClickHouse; Scylla stays experimental; dual-write secondary failure metric |
| Breadth vs depth (OT) | **Planned → Wave 3A** | [0067](decisions/0067-field-certified-maturity.md) × [0022](decisions/0022-driver-production-matrix.md) | 3A | First FIELD row + C1 named-plant evidence; OT score moves only then |
| External KMS / GOST | **Planned → Wave 3B** | [0076](decisions/0076-external-secrets-providers.md) | 3B | SPI only under named tender; until then explicit non-claim |

## Wave summary

| Wave | Kind | Deliverables |
|------|------|--------------|
| **0** | Docs | This page, canvas RESOLUTION, `AGENTS.md` / principles align with 0060 modes |
| **1A** | Code | `--strict` validate, catalog gate, create_function host poka-yoke |
| **1B** | Code | Effective configuration surface + 0033 docs (**shipped**) |
| **1C** | Code | Cluster fail-closed JetStream/Redis |
| **2A** | Code | Ui-pack CSP + scoped token |
| **2B** | Code | Historian quality TCK + dual-write metric |
| **3A** | Ops | C1 plant FIELD evidence |
| **3B** | Policy | No KMS SPI without tender |

## Logic-host enforcement modes (Wave 0 align)

| Mode | Behaviour | Where |
|------|-----------|--------|
| **Canon (hard rule)** | Logic hosts are SINGLETON or INSTANCE, never DEVICE | `AGENTS.md`, application-principles |
| **Authoring validate/deploy** | `LOGIC_HOST_DEVICE` = **WARNING** (does not alone set `status: ERROR`) | Default bundle validate — [0060](decisions/0060-solution-authoring-constraints.md) |
| **Ship / examples / catalog** | Same codes = **ERROR** | `ExamplesBundleValidationTest`; `ispf validate --strict`; marketplace catalog gate (Wave 1A) |

## Wave 3A — C1 FIELD checklist (ops)

Do not promote a driver to FIELD until all of:

1. Named plant / site + protocol + integrator ticket ([field-pilot-playbook](field-pilot-playbook.md)).
2. Driver already PRODUCTION for claimed capabilities.
3. ≥7 day soak journal under `docs/evidence/`.
4. WRITE round-trip if WRITE claimed (or explicit POLL-only).
5. OT / site-owner sign-off artifact.
6. Matrix row + resolvable `FIELD_EVIDENCE_PATHS` entry; CI green.

Marketplace REAL installs never substitute for this checklist ([0067](decisions/0067-field-certified-maturity.md) §1b).

## Related

- [decisions/readme.md](decisions/readme.md)
- [competitive-scorecard](competitive-scorecard.md)
- [0060](decisions/0060-solution-authoring-constraints.md), [0067](decisions/0067-field-certified-maturity.md), [0069](decisions/0069-cluster-consistency.md), [0073](decisions/0073-hosted-ui-pack-trust.md)
