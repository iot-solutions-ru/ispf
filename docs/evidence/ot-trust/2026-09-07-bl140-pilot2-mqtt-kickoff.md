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

- No day-1 §2 validation yet → **not** Pilot #2 soak started
- Lab jump SSH password auth **failed** from this agent (2026-09-07) → live bootstrap blocked until credentials refreshed out-of-band
- Not BL-140 field Done / not OT **10/10**
- Public-internet MQTT brokers ≠ named OT lab VLAN

## Parallelism note

Pilot #1 timer (`ispf-pilot1-soak-check.timer`, 06:00 MSK) keeps running. This pack advances MQTT fleet **without waiting** for Modbus day 7.

## Next operator action

1. Restore `~/.ssh/lab_ed25519` on the agent **or** append the new agent pubkey from [jump SSH blocker](2026-09-07-lab-jump-ssh-blocker.md) to jump `authorized_keys`.
2. Ensure Mosquitto up (`lab-loadgen-compose` / `~/ispf/lab-loadgen-compose.yml`).
3. Run `pilot2-mqtt-lab-bootstrap.py` against lab ISPF; fill C2/C3.
4. Arm daily soak check; start journal day 1.
