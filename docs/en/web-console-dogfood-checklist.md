# Web Console dogfood checklist (quality hold)

> **Purpose:** Catch HMI/editor bugs that unit tests miss (layout drift, silent buttons, role surprises).  
> **Not** a new product wave. Log findings → fix only named defects.  
> Related: hardening waves #198–#217 · G-01 pack [pen-test-vendor-pack.md](pen-test-vendor-pack.md).

**Environment:** lab or local `0.9.x` with RBAC on. Record build / image id at top of the session log.

| Field | Value |
|-------|-------|
| Date | |
| Build / image | |
| Base URL | |
| Tester | |

## Session A — `developer` (2 h)

| # | Scenario | Pass? | Notes |
|---|----------|-------|-------|
| A1 | Open dashboard builder; move a widget; undo/redo (Ctrl+Z / Ctrl+Y) | ☐ | |
| A2 | Network-graph widget in **edit**: refresh bound data — nodes must not jump | ☐ | |
| A3 | Import SVG over limit (e.g. >512 KB or >2000 elements) — clear error, tab stays alive | ☐ | |
| A4 | Mimic: edit diagram, save; set `diagram` writeRoles to `admin` only; confirm developer save → 403 | ☐ | |
| A5 | Nested dashboard with inheritContext — child events / Show behave per contract | ☐ | |
| A6 | Object tree search finds a node not yet expanded in the tree | ☐ | |
| A7 | Event journal: CEL filter matches a known rule expression | ☐ | |
| A8 | Chart on long range uses historian granules (no manual bucket fight) | ☐ | |
| A9 | Change own roles via admin (or second session); within ~30 s UI reflects without F5 | ☐ | |
| A10 | Driver runtime: configure/start on device with ACL READ-only for developer → 403 | ☐ | |

## Session B — `operator` (1–2 h)

| # | Scenario | Pass? | Notes |
|---|----------|-------|-------|
| B1 | Operator HMI: acknowledge one alarm; Acknowledge all (batch) on a multi-alarm shelf | ☐ | |
| B2 | Cannot open dashboard builder / mimic save (or gets 403) | ☐ | |
| B3 | Cannot install marketplace symbol/UI/bundle (403) | ☐ | |
| B4 | Can read tree, variables, journal (allowed paths) | ☐ | |
| B5 | Logout works (no AuthZ surprise on `/auth/logout`) | ☐ | |

## Session C — soak light (optional, 30 min)

| # | Scenario | Pass? | Notes |
|---|----------|-------|-------|
| C1 | Leave mimic open with live tags 30 min — no blank screen / runaway CPU | ☐ | |
| C2 | Browser console clean of repeating uncaught errors | ☐ | |

## Outcome

- File bugs as issues/PRs **one defect class each**.
- Do **not** open new editor surfaces or protocol features from this list.
- After a clean pass, attach a short note under `docs/evidence/quality/` (optional).
