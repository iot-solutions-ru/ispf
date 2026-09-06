# OT Trust Wave 2 — codec promotion — 2026-09-05

| Field | Value |
|-------|-------|
| Named task | Promote 4 STUB drivers to PRODUCTION with real codecs + loopback tests |
| Lead track | Protocol catalog honesty (Wave 2 after Wave 1 matrix/lab) |
| Plant | **Lab only** (in-process fake servers / Moquette) — not field certification |

## Promoted drivers

| driverId | Codec | Loopback test | Caps |
|----------|-------|---------------|------|
| `redis` | RESP GET/SET over TCP | `RedisDeviceDriverTest` (fake RESP) | POLL + WRITE |
| `mitsubishi-slmp` | SLMP 3E binary device-read/write (D registers) | `MitsubishiSlmpDeviceDriverTest` | POLL + WRITE |
| `yaskawa-memobus` | Modbus TCP FC3/FC6 holding registers | `YaskawaMemobusDeviceDriverTest` | POLL + WRITE |
| `sparkplug-b` | MQTT (Paho) + minimal Sparkplug B protobuf Payload/Metric | `SparkplugBDeviceDriverTest` (Moquette) | POLL + WRITE |

## Honesty checklist

- [x] Class javadoc does **not** say stub/placeholder
- [x] Removed from `protocol-stub-ids.json` and `tools/driver-stubs/protocol-stubs.yaml`
- [x] Entries added to `DriverProductionMatrix` with loopback test paths
- [x] `docs/en/driver-promotion.md` Wave 2 table + `docs/en/drivers.md` maturity lines
- [x] Gradle tests for the four packs + `DriverProductionMatrixTest`
- [x] Re-ran `tools/driver-readiness-audit.py --fail-on-findings`

## Point formats

| driverId | Mapping |
|----------|---------|
| `redis` | Redis key (point id if blank) |
| `mitsubishi-slmp` | `D100` / `D:100` / `D:100:1` |
| `yaskawa-memobus` | `HR:100` / `100` / `HR:100:1` |
| `sparkplug-b` | Metric name; host topics `spBv1.0/{group}/{NBIRTH\|DBIRTH\|DDATA}/#`; write → `DCMD` |

## Not claimed

Wave 2 ≠ field Done. Full Sparkplug birth certificates, multi-device codes beyond D, and Memobus beyond holding registers remain out of scope for v0.1.
