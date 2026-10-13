# ADR-0067: FIELD and CERTIFIED maturity (above PRODUCTION)

## Status

**Accepted** (2026-10-12) — evidence tiers and gates.  
**No driver is labeled FIELD or CERTIFIED today** (C1 named plant open).  
`DriverMaturity.FIELD` / `.CERTIFIED` + empty evidence maps + CI gates landed (Wave 1 follow-up on this branch).

## Context

[ADR-0022](0022-driver-production-matrix.md) made `PRODUCTION` mean **lab CI** (loopback / integration test + docs + honest capabilities). That bar is necessary and insufficient for plant trust. Buyers and agents conflate lab green with plant-proven.

**Industry bar:** OT leaders separate protocol lab certification from site acceptance. ISPF’s differentiator is an **explicit FIELD / CERTIFIED ladder above PRODUCTION**, plus a separate **marketplace REAL** lane — so scorecards cannot treat catalog demos as driver field evidence.

Related: [0057](0057-ot-trust-wave1-dnp3-poll-only.md), [field-pilot-playbook](../field-pilot-playbook.md), [driver-promotion](../driver-promotion.md), [marketplace-pilots](../marketplace-pilots.md).

## Decision

### 1. Driver maturity tiers (API)

| Tier | Meaning |
|------|---------|
| **STUB** | Connectivity / placeholder — not for production telemetry |
| **BETA** | Works with declared limits; coverage partial or hardware-dependent |
| **PRODUCTION** | Lab CI gate per [0022](0022-driver-production-matrix.md) |
| **FIELD** | Named **plant/site** soak + OT sign-off for that **driver’s** claimed capabilities |
| **CERTIFIED** | FIELD (or explicit waiver) + protocol conformance / formal cert artifact |

`DriverMaturity` includes `FIELD` and `CERTIFIED`. Matrix rows stay PRODUCTION until evidence maps are filled; `FIELD_EVIDENCE_PATHS` / `CERTIFIED_EVIDENCE_PATHS` in `DriverProductionMatrix` are empty. CI fails if a row is labeled FIELD/CERTIFIED without a resolvable evidence file.

### 1b. Do not confuse with marketplace / solution pilots

| Lane | What it is | Status today |
|------|------------|--------------|
| **Marketplace / solution pilots** | Installable industry bundles under `examples/marketplace-catalog/` + demostand — [marketplace-pilots](../marketplace-pilots.md) | **Working / REAL** |
| **Lab OT Trust driver pilots** (BL-140) | Modbus / MQTT / OPC UA lab VLAN soaks | Lab evidence; **not** customer-plant FIELD |
| **Driver `FIELD` / `CERTIFIED`** (this ADR) | Enum + evidence map for a **protocol driver** at a **named plant** | **Zero rows** — C1 open |

A working marketplace solution does **not** auto-promote its drivers to `FIELD`. Conversely, `FIELD count = 0` does **not** mean “ISPF has no working pilots.”

### 2. FIELD gate

All required; missing any → remain playbook-ready / lab PRODUCTION only:

1. **Named field task** — site, protocol, integrator ticket, scope ([ready-for-field](../field-pilot-playbook.md#ready-for-field-gate-policy)).
2. **Prerequisite maturity** — driver(s) already **PRODUCTION** for every claimed capability.
3. **Soak** — ≥ **7 days** with journal under `docs/evidence/`.
4. **Round-trip** — if `WRITE` claimed, documented write (or explicit POLL-only like [0057](0057-ot-trust-wave1-dnp3-poll-only.md)).
5. **Sign-off** — OT / site-owner artifact linked from the journal.

Lab VLAN soaks **without** a named plant do **not** satisfy FIELD.

### 3. CERTIFIED gate

1. **FIELD** for the same driver id and capability set, **or** written waiver (must say so on the scorecard).
2. **Conformance evidence** — vendor suite, ISPF pack, or third-party report under `docs/evidence/`.
3. **Honesty** — matrix capabilities must not exceed the conformance artifact.

### 4. Parallel surfaces

| Surface | FIELD-equivalent today | Notes |
|---------|------------------------|-------|
| Drivers | This ADR | Primary; matrix enum |
| HMI / PWA offline | **P-HMI-FIELD** (on-site) | Lab CDP ≠ FIELD |
| Marketplace solutions | Working catalog pilots | Real installs; not driver `FIELD` |
| MES plant soak | MES field soak journals | Named MES site ≠ driver enum |

### 5. Honesty snapshot

| Claim | Status |
|-------|--------|
| Marketplace / solution pilots | **Many — working** |
| Drivers with `DriverMaturity.PRODUCTION` | Many (lab CI) |
| Drivers with **FIELD** matrix evidence | **Zero** (C1 open) |
| Drivers with **CERTIFIED** matrix evidence | **Zero** |
| Enum exposes FIELD/CERTIFIED | **Done** on this branch (no matrix rows use them yet) |

## Consequences

- Amend [0022](0022-driver-production-matrix.md): FIELD/CERTIFIED are defined here.
- [driver-promotion](../driver-promotion.md) and [field-pilot-playbook](../field-pilot-playbook.md) point at these gates.
- Scorecard OT / HMI must not claim 9+ without FIELD (and CERTIFIED where conformance is advertised).
- **Leader posture = refuse oversell:** publish the ladder even when FIELD count is zero.
- Follow-up: fill `FIELD_EVIDENCE_PATHS` / promote a row only after a named-site gate.

### Risks

- Stamping FIELD after lab soak or marketplace demo — blocked by C1 named-plant rule and §1b.
- Equating “no FIELD drivers” with “no working pilots” — blocked by marketplace lane.

## Related

- [0022-driver-production-matrix](0022-driver-production-matrix.md)
- [0057-ot-trust-wave1-dnp3-poll-only](0057-ot-trust-wave1-dnp3-poll-only.md)
- [field-pilot-playbook](../field-pilot-playbook.md), [driver-promotion](../driver-promotion.md)
- [marketplace](../marketplace.md), [marketplace-pilots](../marketplace-pilots.md), [competitive-scorecard](../competitive-scorecard.md)
