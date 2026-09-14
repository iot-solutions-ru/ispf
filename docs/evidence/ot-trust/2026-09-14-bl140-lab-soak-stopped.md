# BL-140 OT Trust lab soak — stopped

Date: 2026-09-14  
Site: `lab-ot-vlan-192.168.100` (`192.168.100.10` / `spark-1f58`)  
Decision: **operator stop** — continuous lab soak timers discontinued; lab evidence is enough for the measurement track.

## Why stop

Daily timer JSON through **2026-09-14** already showed P1/P2/P3 **`pass: true`** / `RUNNING` ([soak pull](2026-09-14-bl140-lab-soak-pull.md)). Continuing the same lab loop does not add field certification.

## Lab cleanup (2026-09-14)

Verified on host:

- No `ispf-pilot{1,2,3}-soak-check.timer` units (disabled/absent)
- No `~/ispf/pilot{1,2,3}-*/evidence` dirs left on host
- ISPF not listening on `:8080` (pilots were already down)
- Removed leftover `/tmp/pilot1-modbus-soak-check.py`

## Honesty

| Claim | Status |
|-------|--------|
| Lab continuous soak evidence (P1/P2/P3) | **Stopped — Consumed** as lab measurement |
| Customer plant / field BL-140 Done | **Not claimed** |
| Competitive OT **10/10** | **Not claimed** |
| Named plant soak + OT sign-off | Remains **parked** until a named site task |

Board: [parked-backlog.md](../../en/parked-backlog.md) **P-OT**.
