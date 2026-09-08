# Lab mid-check — 2026-09-08 ~18:00 MSK

Read-only via jump `iot-solutions@84.42.21.226:5031` → `iot-solutions@192.168.100.10`.

| Check | Result |
|-------|--------|
| ISPF `/api/v1/info` | **0.9.207** · `lab-enterprise-l` · profile `unified` |
| Pilot #1 soak-latest | `pass: true` · Modbus `RUNNING` · `hrCount=50` (utc `2026-09-08T03:00:04Z`) |
| Pilot #2 soak-latest | `pass: true` · MQTT `RUNNING` · `tagCount=10` (utc `2026-09-08T05:37:02Z`) |
| Pilot #3 soak-latest | `pass: true` · OPC UA client+server `RUNNING` (utc `2026-09-08T05:36:28Z`) |
| Timer P1 | `ispf-pilot1-soak-check.timer` next **2026-09-09 06:00 MSK** |
| Timer P2 | `ispf-pilot2-soak-check.timer` next **2026-09-09 06:05 MSK** |
| Timer P3 | `ispf-pilot3-soak-check.timer` next **2026-09-09 06:10 MSK** |

No OT score claim. Next evidence pull after timers fire: `tools/ot-trust/pull-pilot-soak-evidence.sh`.
