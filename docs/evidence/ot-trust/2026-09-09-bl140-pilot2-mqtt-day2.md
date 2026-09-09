# BL-140 Pilot #2 MQTT soak day 2

Date: 2026-09-09  
Site: `lab-ot-vlan-192.168.100`  
Device: `root.platform.devices.pilot2-mqtt.fleet`

## Result

| Check | Value |
|-------|-------|
| Timer | `ispf-pilot2-soak-check.timer` 06:05 MSK |
| UTC | `2026-09-09T03:05:04Z` |
| Status | `RUNNING` / connected |
| Tags | 10 (`dev00`…`dev09`) |
| Samples | dev00=`ok-00`, dev01=`ok-01`, dev09=`ok-09` |
| Pass | **true** |
| Incidents | none |

JSON: [`pilot2-lab/soak-day2-2026-09-09.json`](pilot2-lab/soak-day2-2026-09-09.json)

## Honesty

Days **1–2** green. Days 3–7 + OT sign-off remain. Internal OT lab VLAN — not customer plant. Not OT **10/10**.
