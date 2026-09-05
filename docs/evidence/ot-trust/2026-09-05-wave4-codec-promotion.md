# OT Trust Wave 4 — clean-room codec promotion

Date: 2026-09-05

## Result

| Metric | Before Wave 4 | After Wave 4 |
|--------|---------------|--------------|
| Catalog | 162 | 162 |
| Matrix ENTRIES | 89 | **103** |
| Stub list | 73 | **59** |
| READY_LAB | 86 | **100** |
| FAIL / WARN | 0 / 0 | **0 / 0** |

## Promoted packs (14)

| driverId | dialect (honest lab) | caps |
|----------|----------------------|------|
| barcode-scanner | TCP newline scans + TRIGGER/BEEP | RW |
| weighbridge | ASCII `W` / ZERO/TARE | RW |
| weather-station | `GET FIELD` / `GET ALL` text | R |
| delta-dvp | Modbus-TCP FC3/FC6 (not proprietary Delta) | RW |
| ls-xgt | XGT-lab binary (`LSIS-XGT` 10-byte magic subset) | RW |
| keyence-hostlink | Host Link ASCII | RW |
| panasonic-mewto | MEWTOCOL-COM ASCII lab | RW |
| fatek | FACON ASCII lab | RW |
| azure-iot-hub | MQTT 3.1.1 + Azure topic conventions (no Azure SDK) | RW |
| aws-iot-core | MQTT 3.1.1 + AWS topic conventions (no AWS SDK) | RW |
| iec101 | IEC 60870-5-101 TCP lab subset | RW |
| ansi-c12 | ANSI C12 table-read/write lab subset | RW |
| rtsp | RTSP OPTIONS/DESCRIBE/TEARDOWN lab | RW |
| amqp | AMQP 0-9-1 lab subset (not full broker) | RW |

## Policy

- Apache-2.0 clean-room / JDK sockets (cloud packs: clean-room MQTT 3.1.1, not vendor SDKs).
- Deferred high-risk stacks remain stubbed (`profinet`, `ethercat`, `iec61850*`, proprietary CNC/radio, etc.).
- Lab PRODUCTION / READY_LAB ≠ field certification.

## Tests

Gradle `:test` green for each promoted pack (in-process fake device/broker loopbacks).
