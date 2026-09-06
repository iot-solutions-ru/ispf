# Pilot soak journal (copy per site)

Phase 25 / BL-140. One table per pilot. See [field-pilot-playbook](../../en/field-pilot-playbook.md).

## Header

| Field | Value |
|-------|-------|
| Pilot id | e.g. `lab-modbus-2026-09` |
| Site / VLAN | lab / customer name |
| Protocol / driverId | `modbus-tcp` |
| ISPF version / image | |
| Pilot lead | |
| OT contact | |
| Start date (UTC) | |
| Target end (T+7) | |

## Daily log

| Day | Date (UTC) | Tags online | Incidents (P0/P1) | Historian OK | Write OK | Notes |
| --- | ---------- | ----------- | ----------------- | ------------ | -------- | ----- |
| 1 | | | | ☐ | ☐ | |
| 2 | | | | ☐ | ☐ | |
| 3 | | | | ☐ | ☐ | |
| 4 | | | | ☐ | ☐ | |
| 5 | | | | ☐ | ☐ | |
| 6 | | | | ☐ | ☐ | |
| 7 | | | | ☐ | ☐ | Sign-off ready? |

## Validation §1 (first connect)

| Check | Result | Evidence |
|-------|--------|----------|
| Connect / RUNNING | ☐ | |
| Read ≥50 tags (or lab minimum) | ☐ | |
| Write acknowledged | ☐ | |
| Historian on top tags | ☐ | |
| HMI / mimic updates | ☐ | |
| Stale after disconnect | ☐ | |

**Lab CDP / docker fixtures ≠ on-site plant.** Label environment honestly.
