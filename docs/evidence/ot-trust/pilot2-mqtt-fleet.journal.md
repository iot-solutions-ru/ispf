# Pilot #2 soak journal — MQTT fleet

Phase 25 / BL-140. Instance of [pilot-soak-journal.template.md](pilot-soak-journal.template.md).  
Checklist: [pilot2-mqtt-fleet.checklist.md](pilot2-mqtt-fleet.checklist.md).  
Kickoff: [2026-09-07-bl140-pilot2-mqtt-kickoff.md](2026-09-07-bl140-pilot2-mqtt-kickoff.md).

> **Honesty:** Internal OT lab VLAN (planned). Not customer plant. Not OT 10/10. Day-1 not started until broker + device green.

## Header

| Field | Value |
|-------|-------|
| Pilot id | `pilot2-mqtt-fleet` |
| Site / VLAN | `lab-ot-vlan-192.168.100` (`192.168.100.10`) |
| Protocol / driverId | `mqtt` |
| ISPF version / image | `0.9.207` (`ispf-enterprise-l`) — confirm on day 1 |
| Pilot lead | Cursor cloud agent (OT Trust) |
| OT contact | Lab ops (`iot-solutions`) |
| Integrator ticket | P-OT / BL-140 Pilot #2 kickoff 2026-09-07 |
| Start date (UTC) | _pending_ |
| Target end (T+7) | _pending_ |
| Environment label | ☐ Customer plant ☑ Internal OT lab VLAN ☐ Lab docker only (not field) |

## Validation §2 (first connect)

| Check | Result | Evidence |
|-------|--------|----------|
| Connect / RUNNING | ☐ | |
| Ingress ≥10 topics | ☐ | |
| Burst / no queue drops | ☐ | |
| Write / command ack | ☐ | |
| Historian on top tags | ☐ | |
| Stale after publish stop | ☐ | |

## Daily log

| Day | Date (UTC) | Topics online | Incidents (P0/P1) | Historian OK | Write OK | Notes |
| --- | ---------- | ------------- | ----------------- | ------------ | -------- | ----- |
| 1 | | | | ☐ | ☐ | |
| 2 | | | | ☐ | ☐ | |
| 3 | | | | ☐ | ☐ | |
| 4 | | | | ☐ | ☐ | |
| 5 | | | | ☐ | ☐ | |
| 6 | | | | ☐ | ☐ | |
| 7 | | | | ☐ | ☐ | Sign-off ready? |

**P0** = driver crash, lost ingress >1 h, wrong-topic write — paste log excerpt in Notes.

## Close-out links

| Artifact | Path / ticket |
|----------|---------------|
| Sign-off form | [playbook template](../../en/field-pilot-playbook.md#pilot-sign-off-template-bl-140) |
| Soak check | [`tools/ot-trust/pilot2-mqtt-soak-check.py`](../../tools/ot-trust/pilot2-mqtt-soak-check.py) |
| Bootstrap | [`tools/ot-trust/pilot2-mqtt-lab-bootstrap.py`](../../tools/ot-trust/pilot2-mqtt-lab-bootstrap.py) |
