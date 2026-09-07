# Pilot #1 soak journal — Modbus plant

Phase 25 / BL-140. Instance of [pilot-soak-journal.template.md](pilot-soak-journal.template.md).  
Checklist: [pilot1-modbus-plant.checklist.md](pilot1-modbus-plant.checklist.md).  
Day-1 note: [2026-09-06-bl140-pilot1-lab-day1.md](2026-09-06-bl140-pilot1-lab-day1.md).

> **Honesty:** Internal OT lab VLAN soak. Not customer plant. Not OT 10/10. Not BL-140 Done until day 7 + sign-off.

## Header

| Field | Value |
|-------|-------|
| Pilot id | `pilot1-modbus-plant` |
| Site / VLAN | `lab-ot-vlan-192.168.100` (`192.168.100.10`) |
| Protocol / driverId | `modbus-tcp` |
| ISPF version / image | `0.9.207` (`ispf-enterprise-l`) |
| Pilot lead | Cursor cloud agent (OT Trust) |
| OT contact | Lab ops (`iot-solutions`) |
| Integrator ticket | P-OT / BL-140 Pilot #1 lab soak 2026-09-06 |
| Start date (UTC) | `2026-09-06` |
| Target end (T+7) | `2026-09-13` |
| Environment label | ☐ Customer plant ☑ Internal OT lab VLAN ☐ Lab docker only (not field) |

## Validation §1 (first connect)

| Check | Result | Evidence |
|-------|--------|----------|
| Connect / RUNNING | ☑ | [day1 JSON](pilot1-lab/day1-2026-09-06.json) |
| Read ≥50 tags (or agreed min) | ☑ | 50 tags |
| Write acknowledged | ☑ | `hr00` → 1234 |
| Historian on top tags | ☑ | hr00–hr04 |
| HMI / mimic updates | ☐ | deferred |
| Stale after disconnect | ☑ | [C5](2026-09-06-bl140-pilot1-c5-disconnect.md) — ERROR then recover |

## Daily log

| Day | Date (UTC) | Tags online | Incidents (P0/P1) | Historian OK | Write OK | HMI live | Notes |
| --- | ---------- | ----------- | ----------------- | ------------ | -------- | -------- | ----- |
| 1 | 2026-09-06 | 50 | none | ☑ | ☑ | ☐ | Peer `:1502` RUNNING; C5 disconnect pass; soak-check timer armed ([day1](pilot1-lab/day1-2026-09-06.json), [c5](pilot1-lab/c5-2026-09-06.json), [soak](pilot1-lab/soak-day1-2026-09-06.json)) |
| 2 | 2026-09-07 | 50 | none | ☑ | ☑ | ☐ | Auto soak-check 06:00 MSK pass — [day2](2026-09-07-bl140-pilot1-day2.md) · [JSON](pilot1-lab/soak-day2-2026-09-07.json) |
| 3 | | | | ☐ | ☐ | ☐ | |
| 4 | | | | ☐ | ☐ | ☐ | |
| 5 | | | | ☐ | ☐ | ☐ | |
| 6 | | | | ☐ | ☐ | ☐ | |
| 7 | | | | ☐ | ☐ | ☐ | Sign-off ready? |

**P0** = driver crash, data loss, write to wrong register, historian gap >1 h — paste log excerpt in Notes.

## Close-out links

| Artifact | Path / ticket |
|----------|---------------|
| Sign-off form | [playbook template](../../en/field-pilot-playbook.md#pilot-sign-off-template-bl-140) |
| Lab pre-gate smoke | BL-141 compose peers / CI |
| Day-1 evidence | [2026-09-06-bl140-pilot1-lab-day1.md](2026-09-06-bl140-pilot1-lab-day1.md) |
| Day-2 soak | [2026-09-07-bl140-pilot1-day2.md](2026-09-07-bl140-pilot1-day2.md) |
| C5 disconnect | [2026-09-06-bl140-pilot1-c5-disconnect.md](2026-09-06-bl140-pilot1-c5-disconnect.md) |
| Customer ticket | n/a (internal lab) |
