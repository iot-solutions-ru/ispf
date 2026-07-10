> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0025-telemetry-quality-flags.md](../../en/decisions/0025-telemetry-quality-flags.md).

# ADR-0025: Telemetry quality flags

**Статус:** Принято  
**Дата:** 2026-07-03  
**Контекст:** BL-82 (REQ-EX Wave J). Промышленные протоколы передают status/quality (OPC UA StatusCode, BACnet status-flags). ISPF нужен нормализованный контракт для HMI charts и будущих пробелов в historian.

## Решение

1. **Нормализованные уровни** — `GOOD`, `UNCERTAIN`, `BAD` (`TelemetryQuality` в `ispf-driver-api`).
2. **Хранение** — optional string field `quality` в строках телеметрии `DataRecord` (та же строка, что и `value`), а не отдельный тип platform variable.
3. **Driver mapping**
   - OPC UA: Milo `StatusCode` → GOOD / UNCERTAIN / BAD.
   - Virtual demo: циклически меняет quality на `temperature` для lab HMI.
4. **HMI charts** — trend/chart widgets **пропускают** samples с `BAD` (разрыв линии через `null` value, `connectNulls={false}`). `UNCERTAIN` остаётся на графике (в будущем: пунктирный сегмент).
5. **Historian** — v1 хранит только numeric samples; quality gaps применяются к **live** binding и driver payloads. Follow-up BL может добавить колонку `quality` в history stores.

## Последствия

- `DriverProductionMatrix` добавляет capability `QUALITY` для `virtual` и `opcua`.
- Chart widgets читают `quality` из bound variable row, когда поле присутствует.
- BACnet `status-flags` mapping отложен на follow-up driver change.

## Ссылки

- [OBJECT_MODEL.md § Telemetry quality](../object-model.md#telemetry-quality-bl-82)
- [roadmap](../roadmap.md#часть-e--полный-реестр-bl-01139)
