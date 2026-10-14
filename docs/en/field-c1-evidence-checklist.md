# C1 FIELD evidence checklist (ops)

> **Status:** Ops — claim boundary for driver `FIELD` promotion. Hub: [adr-tension-resolution](adr-tension-resolution.md).  
> Russian summary: [ru/adr-tension-resolution.md](../ru/adr-tension-resolution.md).

**Honesty:** There are **zero** drivers with `DriverMaturity.FIELD` in the production matrix today. This checklist defines what must exist before the **first** FIELD row — not a retroactive claim.

| Claim | Allowed when |
|-------|----------------|
| Playbook-ready | Templates + [field-pilot-playbook](field-pilot-playbook.md) exist |
| Lab PRODUCTION | [0022](decisions/0022-driver-production-matrix.md) CI green |
| **Driver FIELD** | Every section below ✅ for a **named customer plant** |
| OT scorecard depth | Moves only after first FIELD row + linked evidence |

Lanes must not be mixed: [ADR-0067 §1b](decisions/0067-field-certified-maturity.md) — marketplace REAL ≠ driver FIELD; lab VLAN ≠ customer plant FIELD.

Evidence root template: [docs/evidence/field-c1/README.md](../evidence/field-c1/README.md).

---

## 0 — Named plant gate (blocks everything)

- [ ] **Customer plant** identified (legal site / plant name — not docker-only or anonymous lab VLAN).
- [ ] **Integrator ticket** or internal field task id with scope (protocol, driver id, tag count, write policy).
- [ ] **OT / site contact** named on [site-intake](../evidence/field-c1/README.md) (`site-intake.md` in pilot folder).
- [ ] [ready-for-field gate](field-pilot-playbook.md#ready-for-field-gate-policy) satisfied — not merely playbook-ready.

If any box is open, stop: remain **FIELD=0** and lab/playbook lanes only.

---

## 1 — Driver prerequisite (0022 + 0067)

- [ ] Target `driverId` is **PRODUCTION** in matrix for **every capability** you will claim at FIELD (READ / WRITE / SUBSCRIBE / …).
- [ ] Capability flags on the matrix row match the plant test (no WRITE at FIELD if soak was read-only unless POLL-only is explicit, cf. [0057](decisions/0057-ot-trust-wave1-dnp3-poll-only.md)).
- [ ] No stub/BETA bypass — FIELD is above PRODUCTION, not a marketing label.

---

## 2 — On-site validation (day 0)

Use [field-pilot-playbook](field-pilot-playbook.md) scenario checklist (Modbus / MQTT / OPC UA as applicable).

- [ ] Device status **RUNNING**; no unhandled `DriverException` storm in logs.
- [ ] Read path: declared tag count and poll/subscribe latency within playbook success criteria.
- [ ] **Write round-trip** if WRITE claimed (operator or API + PLC confirmation); otherwise document **POLL-only**.
- [ ] Historian samples for representative tags (`observedAt` / quality visible).
- [ ] Operator HMI or mimic shows live values; stale behavior documented on disconnect.

Record in `validation-day0.md` under `docs/evidence/field-c1/<pilot-id>/`.

---

## 3 — Soak (≥ 7 days)

- [ ] Dated journal under `docs/evidence/field-c1/<pilot-id>/soak.journal.md` (or linked daily notes in `artifacts/`).
- [ ] Incidents (P0/P1) logged with resolution; open P0 blocks promotion.
- [ ] Soak spans **named plant** production or agreed pilot window — not lab fixture only.

---

## 4 — Sign-off

- [ ] OT or site-owner acceptance artifact in `sign-off.md` (name, role, date, scope: driver id + capabilities).
- [ ] ISPF pilot lead countersigned or referenced in PR that adds `FIELD_EVIDENCE_PATHS`.

---

## 5 — Matrix + CI (code follow-up)

Docs-only completion of §0–4 **does not** flip the enum. Required engineering step:

- [ ] PR adds resolvable path in `FIELD_EVIDENCE_PATHS` pointing at the pilot `README.md` or primary journal.
- [ ] `DriverProductionMatrixTest` / CI resolves the file.
- [ ] [competitive-scorecard](competitive-scorecard.md) OT depth updated **only** after merge.

Wave 3A ops deliverable: this checklist + [field-c1 evidence template](../evidence/field-c1/README.md). **Done when (tension):** first customer-plant pack complete per above **and** matrix row merged — see [adr-tension-resolution](adr-tension-resolution.md).

---

## Related

- [0067-field-certified-maturity](decisions/0067-field-certified-maturity.md)
- [0022-driver-production-matrix](decisions/0022-driver-production-matrix.md)
- [driver-promotion](driver-promotion.md)
- [marketplace-pilots](marketplace-pilots.md)
