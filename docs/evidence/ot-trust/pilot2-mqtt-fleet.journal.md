# Pilot #2 soak journal — MQTT fleet

Phase 25 / BL-140. Instance of [pilot-soak-journal.template.md](pilot-soak-journal.template.md).  
Checklist: [pilot2-mqtt-fleet.checklist.md](pilot2-mqtt-fleet.checklist.md).  
Kickoff: [2026-09-07-bl140-pilot2-mqtt-kickoff.md](2026-09-07-bl140-pilot2-mqtt-kickoff.md).  
Day-1: [2026-09-08-bl140-pilot2-mqtt-day1.md](2026-09-08-bl140-pilot2-mqtt-day1.md).

> **Honesty:** Internal OT lab VLAN. Not customer plant. Not OT 10/10.

## Header

| Field | Value |
|-------|-------|
| Pilot id | `pilot2-mqtt-fleet` |
| Site / VLAN | `lab-ot-vlan-192.168.100` (`192.168.100.10`) |
| Protocol / driverId | `mqtt` |
| ISPF version / image | `0.9.207` (`ispf-enterprise-l`) |
| Pilot lead | Cursor cloud agent (OT Trust) |
| OT contact | Lab ops (`iot-solutions`) |
| Integrator ticket | P-OT / BL-140 Pilot #2 2026-09-08 |
| Start date (UTC) | `2026-09-08` |
| Target end (T+7) | `2026-09-15` |
| Environment label | ☐ Customer plant ☑ Internal OT lab VLAN ☐ Lab docker only (not field) |

## Validation §2 (first connect)

| Check | Result | Evidence |
|-------|--------|----------|
| Connect / RUNNING | ☑ | day1 JSON |
| Ingress ≥10 topics | ☑ | 10 tags `dev00`…`dev09` |
| Burst / no queue drops | ☑ | 100 pubs (lab) |
| Write / command ack | ☑ | write `dev00` HTTP 200 |
| Historian on top tags | ☑ | `dev00`…`dev04` |
| Stale after publish stop | ☑ | C5 broker stop → ERROR then recover |

## Daily log

| Day | Date (UTC) | Topics online | Incidents (P0/P1) | Historian OK | Write OK | Notes |
| --- | ---------- | ------------- | ----------------- | ------------ | -------- | ----- |
| 1 | 2026-09-08 | 10 | none | ☑ | ☑ | Mosquitto + device green; C5 pass — [day1](2026-09-08-bl140-pilot2-mqtt-day1.md) · [JSON](pilot2-lab/day1-2026-09-08.json) |
| 2 | 2026-09-09 | 10 | none | ☑ | ☑ | Auto soak-check 06:05 MSK pass — [day2](2026-09-09-bl140-pilot2-mqtt-day2.md) · [JSON](pilot2-lab/soak-day2-2026-09-09.json) |
| 3 | | | | ☐ | ☐ | |
| 4 | | | | ☐ | ☐ | |
| 5 | | | | ☐ | ☐ | |
| 6 | | | | ☐ | ☐ | |
| 7 | | | | ☐ | ☐ | Sign-off ready? |

## Close-out links

| Artifact | Path / ticket |
|----------|---------------|
| Sign-off form | [playbook template](../../en/field-pilot-playbook.md#pilot-sign-off-template-bl-140) |
| Soak check | [`tools/ot-trust/pilot2-mqtt-soak-check.py`](../../tools/ot-trust/pilot2-mqtt-soak-check.py) |
| Bootstrap | [`tools/ot-trust/pilot2-mqtt-lab-bootstrap.py`](../../tools/ot-trust/pilot2-mqtt-lab-bootstrap.py) |
| C5 | [pilot2-lab/c5-2026-09-08.json](pilot2-lab/c5-2026-09-08.json) |
