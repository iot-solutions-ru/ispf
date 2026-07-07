# Driver promotion process

Как переводить драйверы из **stub** / **beta** в **production** (Phase 3.2).

## Метки

| `maturity` | Значение |
|------------|----------|
| `PRODUCTION` | Типовые сценарии, документированный конфиг, тесты |
| `BETA` | Рабочий протокол с ограничениями (платформа, auth, partial stack) |
| `STUB` | Connectivity shell — не для production telemetry |

Метка задаётся в `DriverMaturityRegistry` (server) и отдаётся в `GET /api/v1/drivers`.

## Чеклист promotion

1. Реализовать poll/read (или write, если заявлено) в модуле `ispf-driver-*`.
2. Добавить unit/integration тесты на парсер точек и happy-path.
3. Обновить описание в `DriverMetadata` и секцию в [DRIVERS.md](DRIVERS.md).
4. Изменить запись в `DriverMaturityRegistry`.
5. При необходимости — demo device / model в bootstrap.

## Статус (июль 2026, Phase 25 BL-140)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `iec104` | BETA | **PRODUCTION** | Loopback vs `iec104-server`; write commands |
| `dnp3` | BETA | **PRODUCTION** | Class 0/1/2/3 poll via `io.stepfunc:dnp3` |
| `dlms` | BETA | **PRODUCTION** | Gurux read/write; auth NONE |
| `ethernet-ip` | BETA | **PRODUCTION** | CIP session registration + tag path loopback |
| `opc-da` | BETA | **PRODUCTION** | Connectivity shell + parser tests |
| `opc-bridge` | BETA | **PRODUCTION** | Bridge point mapping + parser tests |

## Статус (июнь 2026)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `dnp3` | STUB | **BETA** | Class 0/1/2/3 poll via `io.stepfunc:dnp3`; write not implemented |
| `cwmp` | STUB | **PRODUCTION** | Inform + ACS `GetParameterValues`; TR-069 acceptance tests |
| `flexible` | BETA | **PRODUCTION** | TCP/UDP request/response |
| `gps-tracker` | BETA | **PRODUCTION** | GPS/M2M TCP server |
| `corba` | STUB | **BETA** | IIOP TCP reachability + point parser tests |
| `ethernet-ip` | STUB | **BETA** | CIP session registration + tag path mapping |
| `opc-da` | STUB | **BETA** | DCOM/TCP connectivity shell + parser tests |
| `opc-bridge` | STUB | **BETA** | Bridge point mapping + parser tests; full OPC stack via external bridge |
| `vmware` | STUB | **BETA** | vSphere API point parser + connectivity shell |
| `smi-s` | STUB | **BETA** | SMI-S CIM point parser + connectivity shell |

Остальные stub-драйверы требуют native stack или commercial pack — promotion только по конкретному запросу ([LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md)).
