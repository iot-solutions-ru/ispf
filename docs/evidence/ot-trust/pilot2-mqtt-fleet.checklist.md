# BL-140 Pilot #2 — MQTT fleet checklist (C1–C6)

> **Status:** Day 1 green on internal OT lab VLAN (2026-09-08). Soak days 2–7 open.  
> Playbook: [field-pilot-playbook §2](../../en/field-pilot-playbook.md#2--mqtt-fleet-pilot).  
> Journal: [pilot2-mqtt-fleet.journal.md](pilot2-mqtt-fleet.journal.md).  
> Day-1: [2026-09-08-bl140-pilot2-mqtt-day1.md](2026-09-08-bl140-pilot2-mqtt-day1.md).

## Honesty gate

| Claim | Allowed when |
|-------|----------------|
| Playbook-ready | This pack + playbook §2 exist |
| Lab dry-run green | MQTT Gradle tests + Mosquitto peer |
| **ready-for-field** (lab VLAN) | C1 filled + broker reachable + device RUNNING |
| BL-140 MQTT pilot Done | 7-day soak + OT sign-off + no open P0 |

Do **not** promote scorecard OT from this checklist alone.

---

## Pre-field gate (B4)

| Check | Result | Evidence / date |
|-------|--------|-----------------|
| Compose mosquitto peer exists | ☑ | `lab-loadgen-compose.yml` / `ispf-loadgen-mqtt-1` |
| MQTT Gradle tests green | ☑ | `:packages:ispf-driver-mqtt:test` |
| Matrix honesty (`mqtt` PRODUCTION) | ☑ | catalog closed 162/162 |
| Lab Mosquitto reachable from ISPF | ☑ | `tcp://192.168.100.10:1883` from container |

---

## C1 — Site intake

| Field | Value |
|-------|-------|
| Pilot id | `pilot2-mqtt-fleet` |
| Site / plant name | `lab-ot-vlan-192.168.100` |
| Site type | ☑ Internal OT lab VLAN ☐ Customer plant |
| VLAN / subnet | `192.168.100.0/24` |
| MQTT broker | `tcp://192.168.100.10:1883` |
| Topic map | `ispf/pilot2/fleet/devNN/telemetry` |
| Integrator ticket / issue | P-OT / BL-140 Pilot #2 2026-09-08 |
| Pilot lead (ISPF) | Cursor cloud agent (OT Trust) |
| Customer / site OT contact | Lab ops (`iot-solutions` on jump `84.42.21.226:5031`) |
| Write / command topics allowed? | ☑ Yes (lab) ☐ Read-only |
| Target start (UTC) | `2026-09-08` |
| Target end T+7 (UTC) | `2026-09-15` |

**C1 Done** ✅

---

## C2 — Device config worksheet

| Setting | Planned | Applied (☑) |
|---------|---------|-------------|
| `driverId` | `mqtt` | ☑ |
| `brokerUrl` | `tcp://192.168.100.10:1883` | ☑ |
| `topicPrefix` | _(empty; full topics in mappings)_ | ☑ |
| Tag / topic map ≥ 10 | `dev00`…`dev09` | ☑ |
| Historian on top 5 | `dev00`…`dev04` | ☑ |
| Device status `RUNNING` | `root.platform.devices.pilot2-mqtt.fleet` | ☑ |

---

## C3 — Validation §2

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Connect / `RUNNING` | ☑ | day1 JSON |
| 2 | Ingress ≥10 topics | ☑ | 10 tags |
| 3 | Burst 100 msgs | ☑ | day1 |
| 4 | Command publish / write ack | ☑ | write HTTP 200 |
| 5 | Historian on top tags | ☑ | 5 enabled |
| 6 | Stale when broker stops (C5) | ☑ | ERROR then recover |

---

## C4 — 7-day soak

Use [pilot2-mqtt-fleet.journal.md](pilot2-mqtt-fleet.journal.md) + `pilot2-mqtt-soak-check.py`. Day 1 logged.

---

## C5 — Stale / disconnect

| Step | Result | Evidence |
|------|--------|----------|
| Stop Mosquitto | ☑ `ERROR` / `Not connected` | [c5 JSON](pilot2-lab/c5-2026-09-08.json) |
| Restart broker + driver → recover | ☑ `RUNNING` | day1 |

---

## C6 — Sign-off

Playbook sign-off — only after days 1–7 + no open P0.
