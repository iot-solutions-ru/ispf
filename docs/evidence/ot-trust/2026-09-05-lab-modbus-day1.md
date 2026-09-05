# OT Trust Wave 1 — lab dry-run journal (day 1)

| Field | Value |
|-------|-------|
| Pilot id | `lab-modbus-wave1-2026-09-05` |
| Environment | **Lab fixtures only** (`deploy/driver-interop`) — not a customer plant |
| Protocol / driverId | `modbus-tcp` (+ MQTT / OPC UA fixture smoke) |
| ISPF branch / PR | `cursor/ot-trust-wave1-start` / #144 |
| Evidence date (UTC) | 2026-09-05 |
| Honesty | Dry-run against docker fixtures ≠ on-site BL-140 Done |

## Validation §1 (lab)

| Check | Result | Evidence |
|-------|--------|----------|
| Compose fixtures up | ☐ CI / local | `deploy/driver-interop/docker-compose.yml` |
| MQTT TCP + pub/sub | ☐ | smoke `mqtt-roundtrip` |
| Modbus TCP + **FC6/FC16 write** + FC3 read-back | ☐ | writable `deploy/driver-interop/modbus/server.py` + smoke |
| OPC UA TCP + optional write | ☐ | `asyncua` FastUInt1 when installed |
| Matrix honesty gates | ☑ | `DriverProductionMatrixTest` (ADR-0057 + writePoint source match) |
| Modbus fixture self-test | ☑ | `driver-interop-smoke.sh --self-test-modbus` → ok |

## Notes

- Prior CI failure: `oitc/modbus-server` reset TCP on FC6 → replaced with ISPF writable fixture.
- Customer plant soak still requires a named site (C1) before claiming field Done.

## Daily log

| Day | Date (UTC) | Tags / fixtures | Incidents | Write OK | Notes |
| --- | ---------- | --------------- | --------- | -------- | ----- |
| 1 | 2026-09-05 | docker fixtures | Modbus write fixture swap | ☐ pending CI green | Wave 1 hardening |
