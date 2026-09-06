# Pilot #1 soak journal — Modbus plant

Phase 25 / BL-140. Instance of [pilot-soak-journal.template.md](pilot-soak-journal.template.md).  
Checklist: [pilot1-modbus-plant.checklist.md](pilot1-modbus-plant.checklist.md).

> **Honesty:** Header fields marked TBD mean the pilot has **not** started on a named plant.  
> Do not treat empty daily rows as a failed soak — soak has not begun.

## Header

| Field | Value |
|-------|-------|
| Pilot id | `pilot1-modbus-plant` |
| Site / VLAN | _TBD — fill from checklist C1_ |
| Protocol / driverId | `modbus-tcp` |
| ISPF version / image | |
| Pilot lead | |
| OT contact | |
| Integrator ticket | |
| Start date (UTC) | _TBD_ |
| Target end (T+7) | _TBD_ |
| Environment label | ☐ Customer plant ☐ Internal OT lab VLAN ☐ Lab docker only (not field) |

## Validation §1 (first connect)

| Check | Result | Evidence |
|-------|--------|----------|
| Connect / RUNNING | ☐ | |
| Read ≥50 tags (or agreed min) | ☐ | |
| Write acknowledged | ☐ / N/A | |
| Historian on top tags | ☐ | |
| HMI / mimic updates | ☐ | |
| Stale after disconnect | ☐ | |

## Daily log

| Day | Date (UTC) | Tags online | Incidents (P0/P1) | Historian OK | Write OK | HMI live | Notes |
| --- | ---------- | ----------- | ----------------- | ------------ | -------- | -------- | ----- |
| 1 | | | | ☐ | ☐ | ☐ | |
| 2 | | | | ☐ | ☐ | ☐ | |
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
| Lab pre-gate smoke | |
| Customer ticket | |
