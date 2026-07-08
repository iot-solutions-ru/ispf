> **Язык:** русская версия (вычитка). Канонический английский: [en/driver-interop-lab.md](../en/driver-interop-lab.md).

# Лаборатория взаимодействия драйверов (BL-141)

Лаборатория совместимости OT-драйверов ISPF: шлейф-тесты в CI, отчёт задержки и запись туда и обратно для топ-20 промышленных драйверов.

## Цель

- Подтвердить зрелость **PRODUCTION** для промышленных протоколов ([ADR-0022](decisions/0022-driver-production-matrix.md), BL-140).
- Запуск **одинакового** набора Gradle-тестов локально, в CI и на лабораторном хосте.
- Фиксировать длительность прогона и статус для каждого пакета драйверов.

## Топ-20 промышленных матриц

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

`iec104-server` остается **BETA** как outstation для кольцевой проверки `iec104`, но входит в матрицу взаимодействия.

## Рабочий процесс CI

GitHub Actions: [`.github/workflows/driver-interop.yml`](../.github/workflows/driver-interop.yml)

1. **docker-fixtures-smoke** — `docker compose -f deploy/driver-interop/docker-compose.yml up -d --wait`, затем `deploy/tools/driver-interop-smoke.sh` (MQTT/Modbus/OPC UA), `down -v`.
2. **matrix** — `./gradlew :packages:<module>:test` для каждого модуля из топ-20.
3. **сводка** — `deploy/tools/driver-interop-report.sh --ci-summary` → `build/driver-interop/interop-summary.md`.
4. **production gates** — `DriverProductionMatrixTest` + `DriverInteropWorkflowGateTest`.

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

## Запись туда и обратно

Драйверы с `WRITE` в матрице покрывают write path в loopback-тестах:

| Водитель | Запись покрытия в режиме обратной связи |
| ------ | -------------------------- |
| `modbus-tcp/rtu/udp` | FC5/FC6 write |
| `opcua` | Milo `writeValue` |
| `s7` | DB area write |
| `bacnet` | present-value write |
| `iec104` | single/set commands |
| `dlms` | Gurux SET |
| `mqtt` | publish path |

Производственные драйверы только для чтения (`dnp3`, `ethernet-ip`, `opc-da`, `opc-bridge`, `http`, `snmp`, `gps-tracker`) — опрос целостности/проверка сеанса без двусторонней записи; Путь записи продвижения — отдельный BL по [DRIVER_PROMOTION.md](driver-promotion.md).

## Докерные приспособления (BL-141)

Контейнеры аппаратного эмулятора для ручного взаимодействия на хосте лаборатории. Тесты обратной связи CI остаются в процессе (Docker не требуется).

### Стартовые матчи

Из корня репо:

```bash
docker compose -f deploy/driver-interop/docker-compose.yml up -d
```

Проверьте здоровье:

```bash
docker compose -f deploy/driver-interop/docker-compose.yml ps
```

Останавливаться:

```bash
docker compose -f deploy/driver-interop/docker-compose.yml down
```

### Конечные точки (петлевая связь)

| Сервис | Изображение | Конечная точка хоста | Драйвер ISPF |
| ------- | ----- | ------------- | ----------- |
| MQTT | `eclipse-mosquitto:2.0` | `tcp://127.0.0.1:1883` | `mqtt` — `brokerUrl` |
| Modbus TCP | `oitc/modbus-server` | `127.0.0.1:502` | `modbus-tcp` — `host` / `port` |
| OPC UA | `mcr.microsoft.com/iotedge/opc-plc` | `opc.tcp://127.0.0.1:4840` | `opcua` — `endpointUrl` |

Симулятор OPC UA использует **SecurityPolicy.None** и анонимную аутентификацию (только в лабораторных условиях). Для конечной точки сервера, поддерживающего собственный ISPF, используйте встроенный драйвер `opcua-server` на том же порту только тогда, когда контейнер остановлен.

Mosquitto config: `deploy/driver-interop/mosquitto/mosquitto.conf` (anonymous, no persistence).

### Дым от светильников

After `docker compose up -d`:

```bash
bash deploy/tools/driver-interop-smoke.sh
```

Скрипт ждёт TCP на `1883`/`502`/`4840`, делая MQTT туда и обратно (через `docker exec` в `ispf-interop-mosquitto` или хост `mosquitto_pub`), пишет `build/driver-interop/fixture-smoke-summary.md`.

Ручная проверка:

```bash
mosquitto_pub -h 127.0.0.1 -t ispf/lab/ping -m ok
./gradlew :packages:ispf-driver-mqtt:test :packages:ispf-driver-modbus:test :packages:ispf-driver-opcua:test
```

Полная проверка взаимодействия топ-20:

```bash
bash deploy/tools/driver-interop-report.sh
```

Расширенные аппаратные средства-эмуляторы (выносная станция OpenDNP3, симулятор Gurux DLMS) — BL-142+.

## Связанные документы

- [DRIVERS.md](drivers.md) — зрелость и конфиги
- [DRIVER_PROMOTION.md](driver-promotion.md) — чеклист продвижения
- [ROADMAP_PHASE25.md](roadmap-phase-25.md) — BL-140…145
