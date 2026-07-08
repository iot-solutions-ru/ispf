> **Язык:** русская версия (вычитка). Канонический английский: [en/driver-promotion.md](../en/driver-promotion.md).

# Процесс продвижения драйверов

Как перевести драйверы из **заглушки**/**беты** в **производственную** (этап 3.2).

## Метки

| `maturity` | Значение |
|------------|----------|
| `PRODUCTION` | Типовые сценарии, документированный конфиг, тесты |
| `BETA` | Рабочий протокол с ограничениями (платформа, auth, partial stack) |
| `STUB` | Connectivity shell — не для production telemetry |

Метка задаётся в `DriverMaturityRegistry` (server) и отдаётся в `GET /api/v1/drivers`.

## Продвижение Чеклиста

1. Реализовать опрос/чтение (или запись, если заявлено) в модуле `ispf-driver-*`.
2. Добавьте модульные/интеграционные тесты для парсеров и счастливого пути.
3. Обновить описание в `DriverMetadata` и раздел в [DRIVERS.md](drivers.md).
4. Изменить запись в `DriverMaturityRegistry`.
5. При необходимости — демо-устройство/модель в бутстрапе.

## Статус (июль 2026, 25 этап БЛ-140)

| идентификатор драйвера | Было | Стало | Примечание |
|----------|------|-------|------------|
| `iec104` | BETA | **PRODUCTION** | Loopback vs `iec104-server`; write commands |
| `dnp3` | BETA | **PRODUCTION** | Class 0/1/2/3 poll via `io.stepfunc:dnp3` |
| `dlms` | BETA | **PRODUCTION** | Gurux read/write; auth NONE |
| `ethernet-ip` | BETA | **PRODUCTION** | CIP session registration + tag path loopback |
| `opc-da` | BETA | **PRODUCTION** | Connectivity shell + parser tests |
| `opc-bridge` | BETA | **PRODUCTION** | Bridge point mapping + parser tests |

## Статус (июнь 2026)

| идентификатор драйвера | Было | Стало | Примечание |
|----------|------|-------|------------|
| `dnp3` | STUB | **BETA** | Class 0/1/2/3 poll via `io.stepfunc:dnp3`; write not implemented |
| `cwmp` | STUB | **PRODUCTION** | Inform + ACS `GetParameterValues`; TR-069 acceptance tests |
| `flexible` | BETA | **PRODUCTION** | TCP/UDP request/response |
| `gps-tracker` | BETA | **PRODUCTION** | GPS/M2M TCP server |
| `corba` | STUB | **BETA** | IIOP TCP reachability + point parser tests |
| `ethernet-ip` | STUB | **BETA** | CIP session registration + tag path mapping |
| `opc-da` | STUB | **BETA** | DCOM/TCP connectivity shell + parser tests |
| `opc-bridge` | ЗАКУСОК | **БЕТА** | Отображение мостовых точек + тесты парсера; полный стек OPC через внешний мост |
| `vmware` | STUB | **BETA** | vSphere API point parser + connectivity shell |
| `smi-s` | STUB | **BETA** | SMI-S CIM point parser + connectivity shell |

Для остальных стаб-драйверов требуется нативный стек или коммерческий пак — продвижение только по конкретному запросу ([LICENSED_DRIVER_PACKS.md](licensed-driver-packs.md)).
