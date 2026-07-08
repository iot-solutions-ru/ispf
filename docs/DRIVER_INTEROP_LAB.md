# Driver interop lab (BL-141)

Лаборатория совместимости OT-драйверов ISPF: loopback-тесты в CI, отчёт latency и write round-trip для top-20 industrial drivers.

## Цель

- Подтвердить **PRODUCTION** maturity для промышленных протоколов ([ADR-0022](decisions/0022-driver-production-matrix.md), BL-140).
- Запускать **одинаковый** набор Gradle-тестов локально, в CI и на lab-хосте.
- Фиксировать длительность прогона и статус по каждому driver pack.

## Top-20 industrial matrix

Источник истины: `DriverProductionMatrix.TOP_20_INDUSTRIAL` в `packages/ispf-server`.

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

`iec104-server` остаётся **BETA** как outstation для loopback `iec104`, но входит в interop matrix.

## CI workflow

GitHub Actions: [`.github/workflows/driver-interop.yml`](../.github/workflows/driver-interop.yml)

1. **docker-fixtures-smoke** — `docker compose -f deploy/driver-interop/docker-compose.yml up -d --wait`, затем `deploy/tools/driver-interop-smoke.sh` (MQTT / Modbus / OPC UA), `down -v`.
2. **matrix** — `./gradlew :packages:<module>:test` для каждого модуля из top-20.
3. **summary** — `deploy/tools/driver-interop-report.sh --ci-summary` → `build/driver-interop/interop-summary.md`.
4. **production-gate** — `DriverProductionMatrixTest` + `DriverInteropWorkflowGateTest`.

Триггеры: PR в `packages/ispf-driver-*`, `DriverProductionMatrix`, workflow file.

## Локальный прогон

```bash
# один модуль
./gradlew :packages:ispf-driver-iec104:test

# полный отчёт top-20
bash deploy/tools/driver-interop-report.sh
```

Артефакты:

- `build/driver-interop/interop-report.tsv` — TSV: module, result, duration_ms, tests_run
- `build/driver-interop/interop-summary.md` — markdown-таблица для CI / release notes
- `build/driver-interop/<module>.log` — полный Gradle log

## Write round-trip

Драйверы с `WRITE` в матрице покрывают write path в loopback-тестах:

| Driver | Write coverage in loopback |
| ------ | -------------------------- |
| `modbus-tcp/rtu/udp` | FC5/FC6 write |
| `opcua` | Milo `writeValue` |
| `s7` | DB area write |
| `bacnet` | present-value write |
| `iec104` | single/set commands |
| `dlms` | Gurux SET |
| `mqtt` | publish path |

Read-only production drivers (`dnp3`, `ethernet-ip`, `opc-da`, `opc-bridge`, `http`, `snmp`, `gps-tracker`) — integrity poll / session check без write round-trip; promotion write path — отдельный BL по [DRIVER_PROMOTION.md](DRIVER_PROMOTION.md).

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

Скрипт ждёт TCP на `1883` / `502` / `4840`, делает MQTT round-trip (через `docker exec` в `ispf-interop-mosquitto` или host `mosquitto_pub`), пишет `build/driver-interop/fixture-smoke-summary.md`.

Ручная проверка:

```bash
mosquitto_pub -h 127.0.0.1 -t ispf/lab/ping -m ok
./gradlew :packages:ispf-driver-mqtt:test :packages:ispf-driver-modbus:test :packages:ispf-driver-opcua:test
```

Full top-20 interop sweep:

```bash
bash deploy/tools/driver-interop-report.sh
```

Extended hardware-emulator fixtures (OpenDNP3 outstation, Gurux DLMS simulator) — BL-142+.

## Связанные документы

- [DRIVERS.md](DRIVERS.md) — maturity и конфиги
- [DRIVER_PROMOTION.md](DRIVER_PROMOTION.md) — чеклист promotion
- [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md) — BL-140…145
