# BL-140 Pilot #1 — Modbus plant checklist (C1–C6)

> **Status:** Kickoff pack published — **C1 site name still open**.  
> This file is the working checklist for the recommended first field pilot.  
> Lab dry-run / docker fixtures ≠ field Done / OT scorecard 10/10.

Canonical playbook: [field-pilot-playbook §1](../../en/field-pilot-playbook.md#1--modbus-plant-pilot).  
Soak journal instance: [pilot1-modbus-plant.journal.md](pilot1-modbus-plant.journal.md).  
Generic template: [pilot-soak-journal.template.md](pilot-soak-journal.template.md).

## Honesty gate

| Claim | Allowed when |
|-------|----------------|
| Playbook-ready | This pack + playbook templates exist |
| Lab dry-run green | `driver-interop-smoke.sh` exit 0 (B4) |
| **ready-for-field** | Named site + integrator ticket + C1 filled |
| BL-140 field Done | 7-day soak + OT sign-off + no open P0 |

Do **not** promote scorecard OT connectivity from this checklist alone.

---

## Pre-field gate (B4) — lab before plant

Run on integrator laptop / CI before touching plant VLAN:

```bash
docker compose -f deploy/driver-interop/docker-compose.yml up -d --wait
bash deploy/tools/driver-interop-smoke.sh
./gradlew :packages:ispf-driver-modbus:test
```

| Check | Result | Evidence / date |
|-------|--------|-----------------|
| Compose peers up | ☐ | |
| Smoke exit 0 (incl. modbus-tcp FC6/FC16→FC3) | ☐ | |
| Modbus Gradle tests green | ☐ | |
| Matrix honesty (no stub PRODUCTION) | ☐ | CI `driver-interop` / matrix tests |

---

## C1 — Site intake (blocks ready-for-field)

Fill **before** claiming field start. Empty = still playbook-ready only.

| Field | Value |
|-------|-------|
| Pilot id | `pilot1-modbus-plant` (keep) |
| Site / plant name | _TBD — customer or internal named VLAN_ |
| Site type | ☐ Customer plant ☐ Internal OT lab VLAN (not docker-only) |
| VLAN / subnet | |
| PLC host:port (Modbus TCP) | e.g. `198.51.100.50:502` |
| Unit id | `1` (default) |
| Integrator ticket / issue | |
| Pilot lead (ISPF) | |
| Customer / site OT contact | |
| Write tests allowed on site? | ☐ Yes (named registers) ☐ Read-only soak |
| Target start (UTC) | |
| Target end T+7 (UTC) | |

**C1 Done when:** site name + OT contact + ticket + PLC endpoint are non-empty.

---

## C2 — Device config worksheet

| Setting | Planned | Applied (☑) |
|---------|---------|-------------|
| `driverId` | `modbus-tcp` | ☐ |
| `host` / `port` | from C1 | ☐ |
| `unitId` | | ☐ |
| `pollIntervalMs` | `1000` (or site SLA) | ☐ |
| Tag count ≥ 50 (or agreed lab min) | | ☐ |
| Historian on top 5 tags | | ☐ |
| Device status `RUNNING` | | ☐ |

Map registers → variables (example):

| Modbus | Variable path | R/W |
|--------|---------------|-----|
| `40001` | `pilot1.tank.level` | R |
| `40002` | `pilot1.pump.speed` | R/W (if allowed) |
| … | … | … |

---

## C3 — Validation §1 (first connect)

| # | Check | Result | Evidence (log / screenshot path) |
|---|-------|--------|----------------------------------|
| 1 | Connect / `RUNNING` | ☐ | |
| 2 | Read ≥50 tags (or agreed min), quality GOOD | ☐ | |
| 3 | Write acknowledged (FC6/FC16 or site-approved) | ☐ / N/A | |
| 4 | Historian samples on top 5 | ☐ | |
| 5 | HMI / mimic live | ☐ | |
| 6 | Stale badge after disconnect (C5) | ☐ | |

---

## C4 — 7-day soak

Use [pilot1-modbus-plant.journal.md](pilot1-modbus-plant.journal.md). One row per UTC day.

| Gate | Rule |
|------|------|
| S31-style partial | Days 1–3 logged, no open P0 |
| BL-140 field Done | Days 1–7 + OT signature on sign-off form |

---

## C5 — Stale / disconnect exercise

| Step | Result | Evidence |
|------|--------|----------|
| Disconnect PLC/path once during soak | ☐ | |
| Stale alarm / operator badge fires | ☐ | |
| Reconnect restores GOOD within SLA | ☐ | |

---

## C6 — Sign-off

Copy [field-pilot-playbook — Pilot sign-off](../../en/field-pilot-playbook.md#pilot-sign-off-template-bl-140). Attach filled form to ticket + journal.

| Artifact | Linked? |
|----------|---------|
| Journal (7 days) | ☐ |
| Sign-off form | ☐ |
| Interop / smoke summary (lab pre-gate) | ☐ |
| Scorecard note (only after field Done) | ☐ — do not update OT 10/10 early |

---

## Workstream status board

| Task | Status | Notes |
|------|--------|-------|
| B4 lab dry-run documented | **Ready** | Commands above; peers 14/20 compose |
| C1 site intake | **Open** | Blocks ready-for-field |
| C2 device config | Blocked on C1 | |
| C3 validation §1 | Blocked on C1 | |
| C4 soak journal shell | **Ready** | Instance file created; days empty |
| C5 stale exercise | Blocked on C1 | |
| C6 sign-off template | **Ready** | In playbook |

---

## Related

- [2026-09-06-bl140-pilot1-kickoff.md](2026-09-06-bl140-pilot1-kickoff.md) — evidence note for this pack
- [2026-09-05-lab-modbus-day1.md](2026-09-05-lab-modbus-day1.md) — lab-only day-1 (not plant)
- [post-merge lab vs field](2026-09-06-post-merge-lab-vs-field.md)
