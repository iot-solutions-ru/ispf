# BL-140 Pilot #2 kickoff pack (MQTT fleet)

Date: 2026-09-07  
Parallel track while Pilot #1 Modbus soak days 3–7 continue.

## What this delivers

| Artifact | Path | Role |
|----------|------|------|
| Working checklist C1–C6 | [pilot2-mqtt-fleet.checklist.md](pilot2-mqtt-fleet.checklist.md) | Site intake, device worksheet, §2 validation, soak, stale |
| Soak journal instance | [pilot2-mqtt-fleet.journal.md](pilot2-mqtt-fleet.journal.md) | Daily rows empty until day 1 |
| Soak check | [`tools/ot-trust/pilot2-mqtt-soak-check.py`](../../tools/ot-trust/pilot2-mqtt-soak-check.py) | Runtime + sample tag health |
| Lab bootstrap | [`tools/ot-trust/pilot2-mqtt-lab-bootstrap.py`](../../tools/ot-trust/pilot2-mqtt-lab-bootstrap.py) | Create/configure fleet device on lab ISPF |
| Playbook | [field-pilot-playbook §2](../../en/field-pilot-playbook.md#2--mqtt-fleet-pilot) | MQTT fleet scope |

## Planned site (same OT lab VLAN as Pilot #1)

| Field | Value |
|-------|-------|
| Site | `lab-ot-vlan-192.168.100` |
| Broker | Lab Mosquitto (`192.168.100.10:1883` / compose `lab-loadgen`) |
| ISPF | `ispf-enterprise-l` on `:8080` |
| Device path | `root.platform.devices.pilot2-mqtt.fleet` |
| Driver | `mqtt` |

## What this does **not** claim

- Lab jump key auth restored 2026-09-08; day-1 completed — see [day1](2026-09-08-bl140-pilot2-mqtt-day1.md)
- Not BL-140 field Done / not OT **10/10**
- Public-internet MQTT brokers ≠ named OT lab VLAN

## Parallelism note

Pilot #1 timer (`ispf-pilot1-soak-check.timer`, 06:00 MSK) keeps running. Pilot #2 advanced **without waiting** for Modbus day 7.

## Next operator action

1. ~~Restore jump key~~ done.
2. ~~Mosquitto + bootstrap~~ done (day 1).
3. Arm daily Pilot #2 soak check (optional timer); continue journal days 2–7.
4. Keep Pilot #1 days 4–7 on track.
