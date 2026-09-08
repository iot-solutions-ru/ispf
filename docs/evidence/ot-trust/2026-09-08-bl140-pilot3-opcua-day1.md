# BL-140 Pilot #3 kickoff + lab day 1 (OPC UA line)

Date: 2026-09-08  
Parallel to Pilot #1 Modbus soak and Pilot #2 MQTT.

## Mode (honesty)

| Item | Value |
|------|-------|
| Site | `lab-ot-vlan-192.168.100` |
| Topology | **Same-host loopback** inside `ispf-enterprise-l`: `opcua-server` ↔ `opcua` |
| Endpoint | `opc.tcp://127.0.0.1:4840/ispf` |
| External `opc-plc` | **Not used** — lab DNS could not pull `mcr.microsoft.com` |

This is **not** an external PLC cell and **not** customer plant. Still valid BL-140 §3 lab depth.

## Devices

| Role | Path | driverId |
|------|------|----------|
| Server export | `root.platform.devices.pilot3-opcua.server` | `opcua-server` |
| Line client | `root.platform.devices.pilot3-opcua.line` | `opcua` |

## Pack fix on lab

Lab jars for `ispf-driver-opcua` / `ispf-driver-opcua-server` failed with  
`Invalid signature file digest for Manifest main attributes`.  
Replaced with rebuilt 0.9.207 packs (signatures stripped) and restarted ISPF. Drivers now appear in `/api/v1/drivers` (57 packs).

## Day-1 validation

| Check | Result |
|-------|--------|
| Browse `IspfVariables` | ☑ |
| Subscribe / poll ≥10 tags | ☑ 12 tags |
| Server write → client read | ☑ |
| Client write-back → server | ☑ `lineSpeed=200` |
| Historian on `lineSpeed`/`cellTemp` | ☑ |
| C5 stop server → client ERROR then recover | ☑ |

Evidence: [`pilot3-lab/day1-2026-09-08.json`](pilot3-lab/day1-2026-09-08.json)

## Timers

| Pilot | Timer | When (MSK) |
|-------|-------|------------|
| #1 Modbus | `ispf-pilot1-soak-check.timer` | 06:00 |
| #2 MQTT | `ispf-pilot2-soak-check.timer` | 06:05 |
| #3 OPC UA | `ispf-pilot3-soak-check.timer` | 06:10 |

## Honesty

Not OT **10/10**. Not BL-140 field Done. Days 2–7 open for Pilot #3.
