# ADR-0067: FIELD and CERTIFIED maturity (above PRODUCTION)

## Status

**Accepted** (2026-10-12) — defines evidence tiers and gates; **no driver is labeled FIELD or CERTIFIED today**. `DriverMaturity.FIELD` / `.CERTIFIED` enum values + empty evidence maps + CI gates landed as the Wave 1 follow-up.

## Context

[ADR-0022](0022-driver-production-matrix.md) made `PRODUCTION` mean **lab CI** (loopback / integration test + docs + honest capabilities). That bar is necessary and insufficient for plant trust. The field-pilot playbook already distinguishes **playbook-ready** vs **ready-for-field**, but the driver matrix enum only had `PRODUCTION` / `BETA` / `STUB`. Scorecard OT / HMI residuals (`P-OT`, `P-HMI-FIELD`) need named tiers so agents and tenders cannot oversell lab PRODUCTION as field-proven.

Related: [0057](0057-ot-trust-wave1-dnp3-poll-only.md) (capability honesty), [field-pilot-playbook](../field-pilot-playbook.md), [driver-promotion](../driver-promotion.md).

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

These are **different products of “pilot”**:

| Lane | What it is | Status today |
|------|------------|--------------|
| **Marketplace / solution pilots** | Installable industry bundles under `examples/marketplace-catalog/` (HVAC, tank-farm, oil-control, MES refs, twins, …) + demostand — [marketplace-pilots](../marketplace-pilots.md) | **Working / REAL** — CI catalog validate, dogfood, operator UIs |
| **Lab OT Trust driver pilots** (BL-140) | Modbus / MQTT / OPC UA lab VLAN soaks + journals | Lab evidence **Consumed**; not customer-plant FIELD |
| **Driver `FIELD` / `CERTIFIED`** (this ADR) | Enum + evidence map for a **protocol driver** at a **named plant** | **Zero rows** — C1 named plant still open |

A working marketplace solution does **not** auto-promote its drivers to `FIELD`. Conversely, `FIELD count = 0` does **not** mean “ISPF has no working pilots.”

### 2. FIELD gate (driver or pilot scenario)

All of the following are required; missing any → remain **playbook-ready** / lab PRODUCTION only:

1. **Named field task** — customer or internal site, protocol, integrator ticket, and scope to harden the path for that site ([ready-for-field policy](../field-pilot-playbook.md#ready-for-field-gate-policy)).
2. **Prerequisite maturity** — driver(s) already **PRODUCTION** for every capability claimed in the pilot (`POLL` / `WRITE` / …); no stub shells.
3. **Soak** — ≥ **7 days** continuous or scheduled poll with a soak journal under `docs/evidence/` (incidents logged; stop note if aborted).
4. **Round-trip** — if `WRITE` is claimed, documented successful write (or explicit POLL-only scope like DNP3 per [0057](0057-ot-trust-wave1-dnp3-poll-only.md)).
5. **Sign-off** — completed OT / site-owner sign-off using the playbook template (or equivalent dated artifact linked from the journal).

Lab VLAN soaks **without** a named plant / C1 site do **not** satisfy FIELD (honest lab evidence only).

### 3. CERTIFIED gate

1. **FIELD** satisfied for the same driver id and capability set, **or** a written waiver that CERTIFIED is protocol-lab-only (rare; must say so on the scorecard).
2. **Conformance evidence** — vendor suite, ISPF-owned conformance pack, or third-party cert report covering the claimed capabilities, linked from `docs/evidence/` (or pack README).
3. **Honesty** — capabilities in `DriverProductionMatrix` must not exceed what the conformance artifact covers.

### 4. Parallel surfaces (same vocabulary, separate evidence)

| Surface | FIELD-equivalent today | Notes |
|---------|------------------------|-------|
| Drivers | This ADR | Primary; matrix enum |
| HMI / PWA offline | **P-HMI-FIELD** (on-site tablet / airplane) | Lab 2h/8h CDP ≠ FIELD |
| Solutions / marketplace | Working reference pilots (catalog) | Real installs; not driver `FIELD` |
| Solutions / MES plant soak | MES field soak journals | Named MES site ≠ driver enum |

### 5. Honesty snapshot (2026-10-12)

| Claim | Status |
|-------|--------|
| Marketplace / solution pilots (catalog + demostand) | **Many — working** |
| Drivers with `DriverMaturity.PRODUCTION` | Many (lab CI) |
| Drivers with **FIELD** matrix evidence | **Zero** (named plant C1 still open) |
| Drivers with **CERTIFIED** matrix evidence | **Zero** |
| Enum exposes FIELD/CERTIFIED | **Yes** (no matrix rows use them yet) |

## Consequences

- Amend [0022](0022-driver-production-matrix.md): FIELD/CERTIFIED are defined here, not “future undefined”.
- [driver-promotion](../driver-promotion.md) and [field-pilot-playbook](../field-pilot-playbook.md) point at these gates.
- Scorecard OT / HMI must not claim 9+ / 9.5 without FIELD (and CERTIFIED where conformance is advertised).
- Follow-up: fill `FIELD_EVIDENCE_PATHS` / promote a row only after a named-site gate; optional console filter by FIELD/CERTIFIED.

### Risks

- Pressure to stamp driver FIELD after lab soak or marketplace demo alone — mitigated by C1 named-**plant** requirement and §1b.
- Readers equate “no FIELD drivers” with “no working pilots” — mitigated by the marketplace lane in §1b / §5.

## Related

- [0022-driver-production-matrix](0022-driver-production-matrix.md)
- [0057-ot-trust-wave1-dnp3-poll-only](0057-ot-trust-wave1-dnp3-poll-only.md)
- [field-pilot-playbook](../field-pilot-playbook.md), [driver-promotion](../driver-promotion.md)
- [marketplace](../marketplace.md), [marketplace-pilots](../marketplace-pilots.md), [competitive-scorecard](../competitive-scorecard.md)
