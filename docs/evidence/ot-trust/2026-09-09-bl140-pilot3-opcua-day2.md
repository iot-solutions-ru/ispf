# BL-140 Pilot #3 OPC UA soak day 2

Date: 2026-09-09  
Site: `lab-ot-vlan-192.168.100`  
Topology: same-host `opcua-server` ↔ `opcua` loopback

## Result

| Check | Value |
|-------|-------|
| Timer | `ispf-pilot3-soak-check.timer` 06:10 MSK |
| UTC | `2026-09-09T03:10:06Z` |
| Client | `pilot3-opcua.line` — `RUNNING` / connected |
| Server | `pilot3-opcua.server` — `RUNNING` / connected |
| Tags | 12 |
| Samples | tag00=97 GOOD, tag09=73 GOOD, lineSpeed=63, cellTemp=70 |
| Pass | **true** |
| Incidents | none |

JSON: [`pilot3-lab/soak-day2-2026-09-09.json`](pilot3-lab/soak-day2-2026-09-09.json)

## Honesty

Days **1–2** green on **lab loopback** (not external PLC / not customer plant). Days 3–7 + sign-off remain. Not OT **10/10**.
