# BL-140 Pilot #2 — MQTT fleet checklist (C1–C6)

> **Status:** Kickoff pack ready (2026-09-07). **Day-1 blocked** on lab jump SSH credential refresh.  
> Playbook: [field-pilot-playbook §2](../../en/field-pilot-playbook.md#2--mqtt-fleet-pilot).  
> Journal: [pilot2-mqtt-fleet.journal.md](pilot2-mqtt-fleet.journal.md).

## Honesty gate

| Claim | Allowed when |
|-------|----------------|
| Playbook-ready | This pack + playbook §2 exist |
| Lab dry-run green | MQTT Gradle tests + interop mosquitto smoke (CI / local) |
| **ready-for-field** (lab VLAN) | C1 filled + broker reachable from ISPF + device RUNNING |
| BL-140 MQTT pilot Done | 7-day soak + OT sign-off + no open P0 |

Do **not** promote scorecard OT from this checklist alone.

---

## Pre-field gate (B4) — lab before plant

| Check | Result | Evidence / date |
|-------|--------|-----------------|
| Compose mosquitto peer exists | ☑ | `deploy/driver-interop` + `examples/lab-mqtt-historian-stress/compose/lab-loadgen-compose.yml` |
| MQTT Gradle tests green | ☑ | `:packages:ispf-driver-mqtt:test` (2026-09-07 agent) |
| Matrix honesty (`mqtt` PRODUCTION) | ☑ | catalog closed 162/162 |
| Lab Mosquitto reachable from ISPF | ☐ | blocked — jump SSH auth fail 2026-09-07 |

---

## C1 — Site intake

| Field | Value |
|-------|-------|
| Pilot id | `pilot2-mqtt-fleet` |
| Site / plant name | `lab-ot-vlan-192.168.100` |
| Site type | ☑ Internal OT lab VLAN ☐ Customer plant |
| VLAN / subnet | `192.168.100.0/24` |
| MQTT broker | `tcp://192.168.100.10:1883` (from host); from container typically `tcp://172.17.0.1:1883` |
| Topic prefix | `ispf/pilot2/fleet/` |
| Integrator ticket / issue | parallel OT track 2026-09-07 (P-OT / BL-140 Pilot #2) |
| Pilot lead (ISPF) | Cursor cloud agent (OT Trust) |
| Customer / site OT contact | Lab ops (`iot-solutions` on jump `84.42.21.226:5031`) |
| Write / command topics allowed? | ☑ Yes (lab) ☐ Read-only |
| Target start (UTC) | _pending day-1_ |
| Target end T+7 (UTC) | _pending_ |

**C1 Done when:** site + broker + contact filled. ✅ (planned values); live connect still open.

---

## C2 — Device config worksheet

| Setting | Planned | Applied (☑) |
|---------|---------|-------------|
| `driverId` | `mqtt` | ☐ |
| `brokerUrl` | `tcp://172.17.0.1:1883` (or host LAN URL) | ☐ |
| `topicPrefix` | `ispf/pilot2/fleet/` | ☐ |
| Tag / topic map ≥ 10 devices (or ≥10 topics) | `dev00`…`dev09` → `…/devNN/telemetry` | ☐ |
| Historian on top 5 | `dev00`…`dev04` | ☐ |
| Device status `RUNNING` | `root.platform.devices.pilot2-mqtt.fleet` | ☐ |

| Topic | Variable | R/W |
|-------|----------|-----|
| `ispf/pilot2/fleet/dev00/telemetry` … `dev09` | `dev00`…`dev09` | R (+ command write optional) |

Bootstrap: `python3 tools/ot-trust/pilot2-mqtt-lab-bootstrap.py --base-url http://127.0.0.1:8080`

---

## C3 — Validation §2 (first connect)

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Connect / `RUNNING` | ☐ | |
| 2 | Ingress ≥10 topics / devices | ☐ | |
| 3 | Burst 1k msg/min × 10 min (lab) | ☐ | |
| 4 | Command publish / write ack | ☐ | |
| 5 | Historian on top tags | ☐ | |
| 6 | Stale when publish stops (C5) | ☐ | |

---

## C4 — 7-day soak

Use [pilot2-mqtt-fleet.journal.md](pilot2-mqtt-fleet.journal.md) + `pilot2-mqtt-soak-check.py`.

---

## C5 — Stale / disconnect

| Step | Result | Evidence |
|------|--------|----------|
| Stop Mosquitto or halt publisher > 3× poll | ☐ | driver / stale alarm |
| Restart broker / publisher → recover | ☐ | `RUNNING` + fresh samples |

---

## C6 — Sign-off

Playbook sign-off template — only after days 1–7 + no open P0.

## Blocker log

| Date | Blocker | Mitigation |
|------|---------|------------|
| 2026-09-07 | Jump: expected `~/.ssh/lab_ed25519` missing on agent VM; password also rejected | Restore private key or install new pubkey — [blocker](2026-09-07-lab-jump-ssh-blocker.md) |
| 2026-09-07 | Agent VM docker overlay mount failed | Cannot stand local mosquitto peer here; rely on lab / CI |
