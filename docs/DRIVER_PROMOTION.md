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

## Статус (июнь 2026)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `cwmp` | STUB | **BETA** | Inform + ответ на ACS `GetParameterValues`, configurable `informParameters` |
| `flexible` | BETA | **PRODUCTION** | TCP/UDP request/response |
| `gps-tracker` | BETA | **PRODUCTION** | GPS/M2M TCP server |

Stub-драйверы (`dnp3`, `ethernet-ip`, `opc-da`, …) требуют native/bridge — promotion только по конкретному запросу.
