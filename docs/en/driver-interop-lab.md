> **Language:** Canonical English. Russian edition: [ru/driver-interop-lab.md](../ru/driver-interop-lab.md).

# Driver interop lab (BL-141)

ISPF OT driver compatibility lab: loopback tests in CI, latency report, and write round-trip for top-20 industrial drivers.

## Goal

- Confirm **PRODUCTION** maturity for industrial protocols ([ADR-0022](decisions/0022-driver-production-matrix.md), BL-140).
- Run the **same** Gradle test set locally, in CI, and on a lab host.
- Record run duration and status per driver pack.

## Top-20 industrial matrix

Source of truth: `DriverProductionMatrix.TOP_20_INDUSTRIAL` in `packages/ispf-server`.

| `driverId` | Gradle module | Loopback test |
| ---------- | ------------- | ------------- |
| `virtual` | `ispf-driver-virtual` | `VirtualUnifiedProfileTest` |
| `mqtt` | `ispf-driver-mqtt` | `MqttDeviceDriverTest` |
| `modbus-tcp` | `ispf-driver-modbus` | `ModbusTcpDeviceDriverTest` |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | `ModbusRtuDeviceDriverTest` |
| `modbus-udp` | `ispf-driver-modbus-udp` | `ModbusPointTest` |
| `opcua` | `ispf-driver-opcua` | `OpcUaDeviceDriverTest` |
| `opcua-server` | `ispf-driver-opcua-server` | `OpcUaServerPointTest`, `OpcUaServerSubscriptionWriteBackIntegrationTest` |
| `snmp` | `ispf-driver-snmp` | `SnmpDeviceDriverTest` |
| `bacnet` | `ispf-driver-bacnet` | `BacnetDeviceDriverNetworkTest` |
| `s7` | `ispf-driver-s7` | `S7DeviceDriverTest` |
| `http` | `ispf-driver-http` | `HttpDeviceDriverTest` |
| `flexible` | `ispf-driver-flexible` | `FlexiblePointTest` |
| `iec104` | `ispf-driver-iec104` | `Iec104DeviceDriverTest` |
| `iec104-server` | `ispf-driver-iec104-server` | `Iec104ServerPointTest` (interop partner, BETA) |
| `dnp3` | `ispf-driver-dnp3` | `Dnp3DeviceDriverTest` |
| `dlms` | `ispf-driver-dlms` | `DlmsDeviceDriverTest` |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | `EthernetIpDeviceDriverTest` |
| `opc-da` | `ispf-driver-opc-da` | `OpcDaDeviceDriverTest` |
| `opc-bridge` | `ispf-driver-opc-bridge` | `OpcBridgeDeviceDriverTest` |
| `gps-tracker` | `ispf-driver-gps-tracker` | `GpsTrackerPointTest` |

`iec104-server` remains **BETA** as outstation for `iec104` loopback but is included in the interop matrix.

## CI workflow

GitHub Actions: [`.github/workflows/driver-interop.yml`](../.github/workflows/driver-interop.yml)

1. **docker-fixtures-smoke** — `docker compose -f deploy/driver-interop/docker-compose.yml up -d --wait`, then `deploy/tools/driver-interop-smoke.sh` (MQTT / Modbus / OPC UA), `down -v`.
2. **matrix** — `./gradlew :packages:<module>:test` for each module from top-20.
3. **summary** — `deploy/tools/driver-interop-report.sh --ci-summary` → `build/driver-interop/interop-summary.md`.
4. **production-gate** — `DriverProductionMatrixTest` + `DriverInteropWorkflowGateTest`.

Triggers: PR in `packages/ispf-driver-*`, `DriverProductionMatrix`, workflow file.

## Local run

```bash
# single module
./gradlew :packages:ispf-driver-iec104:test

# full top-20 report
bash deploy/tools/driver-interop-report.sh
```

Artifacts:

- `build/driver-interop/interop-report.tsv` — TSV: module, result, duration_ms, tests_run
- `build/driver-interop/interop-summary.md` — markdown table for CI / release notes
- `build/driver-interop/<module>.log` — full Gradle log

## Write round-trip

Drivers with `WRITE` in the matrix cover write path in loopback tests:

| Driver | Write coverage in loopback |
| ------ | -------------------------- |
| `modbus-tcp/rtu/udp` | FC5/FC6 write |
| `opcua` | Milo `writeValue` |
| `s7` | DB area write |
| `bacnet` | present-value write |
| `iec104` | single/set commands |
| `dlms` | Gurux SET |
| `mqtt` | publish path |

Read-only production drivers (`dnp3`, `ethernet-ip`, `opc-da`, `opc-bridge`, `http`, `snmp`, `gps-tracker`) — integrity poll / session check without write round-trip; promotion write path — separate BL per [driver-promotion.md](driver-promotion.md).

## Docker fixtures (BL-141)

Hardware-emulator containers for manual interop on a lab host. CI loopback tests remain in-process (no Docker required).

### Start fixtures

From repo root:

```bash
docker compose -f deploy/driver-interop/docker-compose.yml up -d
```

Check health:

```bash
docker compose -f deploy/driver-interop/docker-compose.yml ps
```

Stop:

```bash
docker compose -f deploy/driver-interop/docker-compose.yml down
```

### Endpoints (loopback)

| Service | Image | Host endpoint | ISPF driver |
| ------- | ----- | ------------- | ----------- |
| MQTT | `eclipse-mosquitto:2.0` | `tcp://127.0.0.1:1883` | `mqtt` — `brokerUrl` |
| Modbus TCP | `oitc/modbus-server` | `127.0.0.1:502` | `modbus-tcp` — `host` / `port` |
| OPC UA | `mcr.microsoft.com/iotedge/opc-plc` | `opc.tcp://127.0.0.1:4840` | `opcua` — `endpointUrl` |

OPC UA simulator uses **SecurityPolicy.None** and anonymous auth (lab only). For an ISPF-native server endpoint, use the embedded `opcua-server` driver on the same port only when the container is stopped.

Mosquitto config: `deploy/driver-interop/mosquitto/mosquitto.conf` (anonymous, no persistence).

### Smoke against fixtures

After `docker compose up -d`:

```bash
bash deploy/tools/driver-interop-smoke.sh
```

Script waits for TCP on `1883` / `502` / `4840`, performs MQTT round-trip (via `docker exec` in `ispf-interop-mosquitto` or host `mosquitto_pub`), writes `build/driver-interop/fixture-smoke-summary.md`.

Manual check:

```bash
mosquitto_pub -h 127.0.0.1 -t ispf/lab/ping -m ok
./gradlew :packages:ispf-driver-mqtt:test :packages:ispf-driver-modbus:test :packages:ispf-driver-opcua:test
```

Full top-20 interop sweep:

```bash
bash deploy/tools/driver-interop-report.sh
```

Extended hardware-emulator fixtures (OpenDNP3 outstation, Gurux DLMS simulator) — BL-142+.

## Related documents

- [drivers.md](drivers.md) — maturity and configs
- [driver-promotion.md](driver-promotion.md) — promotion checklist
- [roadmap-phase-25.md](roadmap-phase-25.md) — BL-140…145
