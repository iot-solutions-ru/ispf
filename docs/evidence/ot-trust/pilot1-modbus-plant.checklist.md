# BL-140 Pilot #1 — Modbus plant checklist (C1–C6)

> **Status:** C1 filled on internal OT lab VLAN; §1 validation green; **soak day 1 started** (2026-09-06).  
> Lab dry-run / docker fixtures ≠ customer-plant Done / OT scorecard 10/10.  
> This soak is **named internal OT lab**, not a customer factory.

Canonical playbook: [field-pilot-playbook §1](../../en/field-pilot-playbook.md#1--modbus-plant-pilot).  
Soak journal instance: [pilot1-modbus-plant.journal.md](pilot1-modbus-plant.journal.md).  
Day-1 evidence: [2026-09-06-bl140-pilot1-lab-day1.md](2026-09-06-bl140-pilot1-lab-day1.md) · [JSON](pilot1-lab/day1-2026-09-06.json).

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

| Check | Result | Evidence / date |
|-------|--------|-----------------|
| Compose peers up | ☑ | prior BL-141 CI / local compose (14/20) |
| Smoke exit 0 (incl. modbus-tcp FC6/FC16→FC3) | ☑ | `driver-interop` workflow on main |
| Modbus Gradle tests green | ☑ | `:packages:ispf-driver-modbus:test` |
| Matrix honesty (no stub PRODUCTION) | ☑ | CI matrix gates |

---

## C1 — Site intake (blocks ready-for-field)

| Field | Value |
|-------|-------|
| Pilot id | `pilot1-modbus-plant` |
| Site / plant name | `lab-ot-vlan-192.168.100` |
| Site type | ☑ Internal OT lab VLAN (not docker-only) ☐ Customer plant |
| VLAN / subnet | `192.168.100.0/24` |
| PLC host:port (Modbus TCP) | `192.168.100.10:1502` (lab peer; from ISPF container via `172.17.0.1:1502`) |
| Unit id | `1` |
| Integrator ticket / issue | lab soak kickoff 2026-09-06 (P-OT / BL-140 Pilot #1) |
| Pilot lead (ISPF) | Cursor cloud agent (OT Trust) |
| Customer / site OT contact | Lab ops (`iot-solutions` on jump `84.42.21.226:5031`) |
| Write tests allowed on site? | ☑ Yes (named holding registers hr00+) ☐ Read-only soak |
| Target start (UTC) | `2026-09-06` |
| Target end T+7 (UTC) | `2026-09-13` |

**C1 Done when:** site name + OT contact + ticket + PLC endpoint are non-empty. ✅

---

## C2 — Device config worksheet

| Setting | Planned | Applied (☑) |
|---------|---------|-------------|
| `driverId` | `modbus-tcp` | ☑ |
| `host` / `port` | `172.17.0.1` / `1502` | ☑ |
| `unitId` | `1` | ☑ |
| `pollIntervalMs` | `1000` | ☑ |
| Tag count ≥ 50 | 50 (`hr00`…`hr49`) | ☑ |
| Historian on top 5 tags | `hr00`…`hr04` | ☑ |
| Device status `RUNNING` | `root.platform.devices.pilot1-modbus.plant` | ☑ |

| Modbus | Variable | R/W |
|--------|----------|-----|
| `1:HOLDING:0`…`49` | `hr00`…`hr49` | R/W |

---

## C3 — Validation §1 (first connect)

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Connect / `RUNNING` | ☑ | day1 JSON `driverStatus.status=RUNNING` |
| 2 | Read ≥50 tags | ☑ | `hrCount=50` |
| 3 | Write acknowledged | ☑ | write `hr00=1234` + read-back |
| 4 | Historian samples on top 5 | ☑ | historyEnabled on hr00–hr04 |
| 5 | HMI / mimic live | ☐ | deferred (API path; optional polish) |
| 6 | Stale / disconnect (C5) | ☑ | driver `ERROR`/`Not connected` then recover — [c5 note](2026-09-06-bl140-pilot1-c5-disconnect.md) |

---

## C4 — 7-day soak

Use [pilot1-modbus-plant.journal.md](pilot1-modbus-plant.journal.md).

| Gate | Rule |
|------|------|
| S31-style partial | Days 1–3 logged, no open P0 |
| BL-140 field Done | Days 1–7 + OT signature on sign-off form |

---

## C5 — Stale / disconnect exercise

| Step | Result | Evidence |
|------|--------|----------|
| Disconnect PLC/path once during soak | ☑ | stopped peer `:1502` |
| Stale / error observed on driver | ☑ | `status=ERROR`, `connected=false`, `lastError=Not connected` |
| Reconnect restores RUNNING | ☑ | peer start + runtime start |
| Operator HMI stale badge | ☐ | optional polish (API/driver path evidenced) |

---

## C6 — Sign-off

| Artifact | Linked? |
|----------|---------|
| Journal (7 days) | ☐ days 1–2 logged |
| Sign-off form | ☐ |
| Interop / smoke summary (lab pre-gate) | ☑ BL-141 |
| Scorecard note (only after field Done) | ☐ — do not update OT 10/10 early |

---

## Workstream status board

| Task | Status | Notes |
|------|--------|-------|
| B4 lab dry-run documented | **Done** | |
| C1 site intake | **Done** | `lab-ot-vlan-192.168.100` |
| C2 device config | **Done** | 50 tags RUNNING |
| C3 validation §1 | **Done** (HMI optional) | C5 closed at driver layer |
| C4 soak journal | **In progress** | Days 1–2 logged; daily timer 06:00 MSK |
| C5 stale exercise | **Done** | [c5 evidence](2026-09-06-bl140-pilot1-c5-disconnect.md) |
| C6 sign-off | Blocked on day 7 | |

---

## Related

- [2026-09-06-bl140-pilot1-kickoff.md](2026-09-06-bl140-pilot1-kickoff.md)
- [2026-09-06-bl140-pilot1-lab-day1.md](2026-09-06-bl140-pilot1-lab-day1.md)
- [post-merge lab vs field](2026-09-06-post-merge-lab-vs-field.md)
