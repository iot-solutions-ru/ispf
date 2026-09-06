# BL-140 Pilot #1 kickoff pack (Modbus plant)

Date: 2026-09-06  
Branch intent: field-prep after BL-141 compose peers **14/20**

## What this delivers

| Artifact | Path | Role |
|----------|------|------|
| Working checklist C1–C6 | [pilot1-modbus-plant.checklist.md](pilot1-modbus-plant.checklist.md) | Site intake, device worksheet, validation, stale exercise, status board |
| Soak journal instance | [pilot1-modbus-plant.journal.md](pilot1-modbus-plant.journal.md) | Empty daily rows until named site starts |
| Generic journal template | [pilot-soak-journal.template.md](pilot-soak-journal.template.md) | Copy for pilots #2+ |
| Playbook | [field-pilot-playbook.md](../../en/field-pilot-playbook.md) | §1 Modbus + sign-off template |

## What this does **not** claim

- No named customer/plant yet → **C1 open** → not `ready-for-field`
- No 7-day soak logged → **not** BL-140 field Done
- No competitive OT **10/10**
- Docker / compose lab fixtures remain BL-141 depth only

## Workstream mapping (S31 Wave 1 C)

| Task | Pack status |
|------|-------------|
| C1 Select site | **Open** — intake form ready |
| C2 Device config | Worksheet ready; blocked on C1 |
| C3 Validation §1 | Checklist ready; blocked on C1 |
| C4 7-day soak | Journal shell ready; days empty |
| C5 Stale alarm | Steps in checklist; blocked on C1 |
| C6 Sign-off template | Already in playbook |

Lab honesty / interop (A/B) and catalog waves are already closed on `main`; this pack starts the **field** track without inventing a site name.

## Next operator action

1. Fill **C1** on the checklist (site name, OT contact, ticket, PLC endpoint).  
2. Run **B4** dry-run; paste evidence into the checklist.  
3. Apply device config (C2), pass §1 (C3), start daily journal (C4).  
4. Only then update P-OT / BL-140 toward field Done.

Until step 1, P-OT stays **In progress** (lab closed; field open).
