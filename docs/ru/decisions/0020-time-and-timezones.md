> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0020-time-and-timezones.md](../../en/decisions/0020-time-and-timezones.md).

# ADR-0020: Time & timezones

## Статус

Принято (30 июня 2026 г.)

## Контекст

ISPF обслуживает операторов, площадки и устройства, которые могут находиться в разных IANA timezone. Сегодня платформа **хранит timestamps в UTC** (`Instant`, ClickHouse `DateTime64(3, 'UTC')`), но:

- Events используют server `Instant.now()` в момент fire; устройства не могут передать observation time.
- Historian `sampledAt` — время ingestion, а не measurement time на устройстве.
- У пользователей нет timezone preference; Web Console использует `toLocaleString(undefined, …)` (browser default).
- У device object'ов нет метаданных `timeZone`; driver'ы парсят локальные часы устройства без объявленной зоны.
- Calendar-boundary запросы («за сегодня», смена 08:00–20:00, daily reports) неоднозначны без явного timezone contract.

Отраслевая практика (SCADA historians, observability, financial systems): **store UTC, display local, never persist naked local time without a zone**.

## Решение

### 1. Canonical storage — UTC only

- Server-side: `java.time.Instant` везде в domain, persistence и messaging.
- API и WebSocket: ISO-8601 с `Z` (например `2026-06-30T12:00:00.000Z`).
- Колонки ClickHouse / PostgreSQL остаются в UTC; миграции на local storage нет.

### 2. Zone identifiers — IANA only

- Использовать TZ database ID (`Europe/Moscow`, `Asia/Yekaterinburg`), не фиксированные offset'ы (`UTC+3`).
- Невалидные или неизвестные ID fallback'ятся на `UTC` с logged warning.

### 3. User timezone (display layer)

- У каждого platform user есть `timeZone` preference (IANA), default `UTC`.
- Хранится в `platform_users` и зеркалируется на user object в дереве объектов.
- Web Console сохраняет `ispf.ui.timeZone` в `localStorage`; optional initial guess из `Intl.DateTimeFormat().resolvedOptions().timeZone`.
- Все UI time labels и **calendar-relative ranges** («today», «yesterday», shift windows) используют **user** timezone.
- Центральный helper: `formatDateTime(iso, { timeZone, locale })` — заменить ad-hoc вызовы `toLocaleString`.

### 4. Device timezone (edge / normalization)

- Optional `timeZone` на `DEVICE` (MIXIN model mixin или device-driver blueprint extension).
- **Inheritance:** device → parent folder/site → platform default `UTC`.
- Driver'ы, получающие **локальные** device timestamps, должны нормализовать в UTC с resolved device timezone.
- Устройства, отправляющие UTC epoch или ISO с offset, не требуют device TZ.

### 5. Dual timestamps для telemetry (когда устройство передаёт время)

| Field | Semantics |
|-------|-----------|
| `observedAt` | Measurement time на устройстве, нормализованный в UTC — **primary для charts и operator view** |
| `ingestedAt` | Когда platform получила/записала sample (сегодняшний `sampledAt`) — latency, ordering, ops |

Если `observedAt` отсутствует, `observedAt := ingestedAt` (backward compatible).

### 6. Events

- `POST /api/v1/events/fire` может включать optional `occurredAt` (ISO-8601 UTC).
- Default: server `Instant.now()` (текущее поведение).
- Validation: reject, если слишком далеко в будущем (configurable skew, например 5 min) или за пределами retention.

### 7. API query contract

- Query params `from` / `to` остаются **absolute UTC** instant'ами.
- Calendar-boundary aggregation (local midnight buckets, shifts) принимает optional query param `timeZone` (user TZ или explicit IANA).
- Rolling windows (`1h`, `24h`, `7d`) остаются duration-based UTC, если не помечены как calendar ranges.

### 8. Platform internals

- Spring `@Scheduled` retention/cleanup jobs остаются на server clock / UTC — не user TZ.
- Bundle `schedules[]` с `intervalMs` без изменений в v1; future cron schedules требуют explicit `timeZone`.

## В объёме (поэтапно — см. BL-66…71)

- ADR + inventory spike
- User TZ preference + UI formatting
- Device TZ metadata + inheritance
- Historian `observedAt` / driver SPI extension
- Calendar-boundary history queries and reports
- Event `occurredAt` override

## Вне объёма (v1)

- Хранение local time в БД без zone
- Auto-detect device TZ по GPS/IP без explicit configuration
- Перевод `error.message` сервера или ответов AI agent по timezone
- User-authored object `displayName` и bundle content
- Keycloak `timezone` claim sync (optional follow-up к BL-67)

## DST и ambiguous local times

- IANA zones обрабатывают DST transitions для display и normalization.
- **Ambiguous local time** (fall-back hour): при парсинге device-local strings предпочитать **earlier** offset (first occurrence); задокументировать в driver integration guide.
- **Gap hour** (spring forward): invalid local times отклоняются с явной validation error.

## Последствия

- consistent operator experience across sites; корректные shift/day reports; device timestamps сохраняются, когда edge их передаёт.
- Migration: additive schema (`time_zone` column, optional `observed_at`); existing data трактуется как `observedAt = ingestedAt`.
- UI: одна formatting utility снижает inconsistent browser behaviour.
- Drivers: optional `observedAt` в write path; без breaking change для driver'ов, опрашивающих только current values.

## Волны реализации

| Wave | BL | Deliverable |
|------|-----|-------------|
| 1 | BL-66 | This ADR accepted + hot-path inventory |
| 2 | BL-67, BL-68 | User + device TZ metadata |
| 3 | BL-69, BL-71 | Historian dual timestamps, event `occurredAt` |
| 4 | BL-70 | Calendar-boundary queries and reports |

## Приложение — known hot paths (inventory)

| Area | Current behaviour | File(s) |
|------|-------------------|---------|
| Event fire timestamp | `Instant.now()` | `ObjectEvent.of()` |
| Historian write | `Instant.now()` ingestion | `VariableHistoryService` |
| CH / PG storage | UTC | `ClickHouseVariableHistoryStore`, `VariableSampleEntity` |
| UI charts/history | `toLocaleString()` browser default | `useVariableHistory.ts`, chart widgets |
| Journals | `toLocaleString()` | `ObjectChangeHistoryPanel`, journal export |
| Metrics | `toLocaleString()` | `SystemMetricsView` |
| User profile | no TZ field | `platform_users`, `PlatformUserStore` |
| Device blueprint | no TZ field | device-driver structure, `drivers.md` |
| Shell preferences | locale + theme only | `ShellPreferences.tsx` |

## Связанные материалы

- [roadmap.md § Phase 21](../roadmap.md#phase-21--time--timezones)
- [roadmap.md § Phase 21 (BL-66…71)](../roadmap.md#phase-21--time--timezones)
- [roadmap](../roadmap.md#часть-b--req-fw-framework-закрыт)
- [0013-web-console-i18n](0013-web-console-i18n.md) — locale vs timezone (orthogonal concerns)
