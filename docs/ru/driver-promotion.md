> **Язык:** русская версия (вычитка). Канонический английский: [en/driver-promotion.md](../en/driver-promotion.md).

# Процесс продвижения драйверов

> **Статус:** Stable — PRODUCTION + ready-for-field. Хаб: [doc-status.md](doc-status.md).

Как перевести драйверы из **stub** / **beta** в **production** (Phase 3.2).

## Метки

| `maturity` | Значение |
|------------|----------|
| `PRODUCTION` | Типовые сценарии, документированный конфиг, тесты |
| `BETA` | Рабочий протокол с ограничениями (платформа, auth, partial stack) |
| `STUB` | Connectivity shell — не для production-телеметрии |

Метка задаётся в `DriverMaturityRegistry` (server) и отдаётся в `GET /api/v1/drivers`.

## Чеклист продвижения

1. Реализовать poll/read (или write, если заявлено) в модуле `ispf-driver-*`.
2. Добавить unit/integration-тесты для парсера точек и happy path.
3. Обновить описание в `DriverMetadata` и раздел в [drivers](drivers.md).
4. Изменить запись в `DriverMaturityRegistry`.
5. При необходимости — демо-устройство / модель в bootstrap.

## Статус (июль 2026, Phase 25 BL-140)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `iec104` | BETA | **PRODUCTION** | Loopback vs `iec104-server`; write commands |
| `dnp3` | BETA | **PRODUCTION** (только poll) | Class 0/1/2/3 poll; **write не реализован** — field write = не готов |
| `dlms` | BETA | **PRODUCTION** | Gurux read/write; auth NONE |
| `ethernet-ip` | BETA | **PRODUCTION** | CIP session registration + tag path loopback |
| `opc-da` | BETA | **PRODUCTION** (shell) | Connectivity shell + parser tests — **не** ready-for-field DA |
| `opc-bridge` | BETA | **PRODUCTION** (shell) | Bridge point mapping + parser tests — полный OPC через внешний bridge |

**Политика:** connectivity shell и read-only master нельзя продавать как field-ready PRODUCTION. См. [Ready-for-field](#ready-for-field-полевые-пилоты) ниже; scorecard OT отслеживает эти пробелы.

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
| `opc-bridge` | STUB | **BETA** | Bridge point mapping + parser tests; полный OPC-стек через внешний bridge |
| `vmware` | STUB | **BETA** | vSphere API point parser + connectivity shell |
| `smi-s` | STUB | **BETA** | SMI-S CIM point parser + connectivity shell |

Остальные stub-драйверы требуют нативный стек или коммерческий pack — продвижение только по конкретному запросу ([licensed-driver-packs](licensed-driver-packs.md)).

## Ready-for-field (полевые пилоты)

**Не автоматически**, когда `maturity: PRODUCTION` или lab interop зелёный. Драйвер/сценарий **ready for field** только после:

1. **Именованной полевой задачи на реализацию** — площадка, протокол, тикет интегратора, scope на доработку или hardening драйвера под этот деплой.
2. Зелёного lab dry-run для сценария ([field-pilot-playbook](field-pilot-playbook.md)).
3. **7-дневного soak** + OT sign-off заказчика.

До пункта (1) статус только **playbook-ready**. См. BL-140 (Partial) и [quality path Wave 1](roadmap.md#quality-path-to-done).
