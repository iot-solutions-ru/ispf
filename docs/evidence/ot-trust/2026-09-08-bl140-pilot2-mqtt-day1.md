# BL-140 Pilot #2 MQTT fleet — lab day 1

Date: 2026-09-08  
Site: `lab-ot-vlan-192.168.100`  
Device: `root.platform.devices.pilot2-mqtt.fleet`

## What ran

| Step | Result |
|------|--------|
| Mosquitto (`ispf-loadgen-mqtt-1`) | Started on `192.168.100.10:1883` |
| Broker URL from ISPF container | `tcp://192.168.100.10:1883` (not `172.17.0.1` — host bind) |
| Configure `mqtt` + 10 topic maps | `dev00`…`dev09` → `ispf/pilot2/fleet/devNN/telemetry` |
| Restart after configure | Required so ActiveDriver loads mappings |
| Ingress | 10/10 tags updated |
| Write | `dev00` publish HTTP 200 |
| Burst | 100 pubs across 10 topics |
| Historian | top 5 (`dev00`…`dev04`) `historyEnabled=true` |
| C5 | Broker stop → `ERROR` / `Not connected`; mosquitto up + driver restart → `RUNNING` |

Evidence: [`pilot2-lab/day1-2026-09-08.json`](pilot2-lab/day1-2026-09-08.json) · [`c5-2026-09-08.json`](pilot2-lab/c5-2026-09-08.json)

## Jump SSH

Password auth used **once** to install `~/.ssh/lab_ed25519` pubkey on jump; subsequent access is key-based. Password **not** committed.

## Honesty

Internal OT lab VLAN. Not customer plant. Not OT 10/10. Pilot #2 soak days 2–7 open. Parallel to Pilot #1 Modbus soak.
