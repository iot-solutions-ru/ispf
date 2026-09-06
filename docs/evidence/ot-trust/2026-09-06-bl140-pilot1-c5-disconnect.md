# BL-140 Pilot #1 — C5 disconnect / stale exercise

Date: 2026-09-06 (UTC)  
Site: `lab-ot-vlan-192.168.100`  
Device: `root.platform.devices.pilot1-modbus.plant`  
Raw JSON: [pilot1-lab/c5-2026-09-06.json](pilot1-lab/c5-2026-09-06.json)

## Procedure

1. Baseline: driver `RUNNING` / `connected=true`
2. Stop Modbus peer: `systemctl --user stop ispf-pilot1-modbus-peer.service`
3. Observe ~16 s of poll/status samples
4. Start peer again; `drivers/runtime/start` + poll
5. Confirm `RUNNING` / `connected=true`

## Results

| Check | Result |
|-------|--------|
| Disconnect observed (`ERROR`, `connected=false`, `lastError=Not connected`) | ✅ |
| Reconnect restores `RUNNING` | ✅ |
| C5 pass | ✅ |

First disconnected sample (t=0 after peer stop):

```json
{"status":"ERROR","connected":false,"lastError":"Not connected"}
```

After reconnect: `RUNNING` / `connected=true`.

## Honesty

- Lab OT VLAN exercise, not customer plant HMI badge screenshot.
- Operator “stale badge on mimic” still optional polish; driver-level stale/error path is evidenced here.
- Does **not** close BL-140 field Done (soak days 2–7 + sign-off remain).

## Daily soak helper

```bash
ISPF_BASE_URL=http://192.168.100.10:8080 \
ISPF_USER=admin ISPF_PASSWORD=admin \
python3 tools/ot-trust/pilot1-modbus-soak-check.py --day N \
  --out docs/evidence/ot-trust/pilot1-lab
```
