# ADR-0020: Time & timezones

## Status

Accepted (2026-06-30)

## Context

ISPF serves operators, sites, and devices that may live in different IANA timezones. Today the platform **stores timestamps in UTC** (`Instant`, ClickHouse `DateTime64(3, 'UTC')`), but:

- Events use server `Instant.now()` at fire time; devices cannot supply observation time.
- Historian `sampledAt` is ingestion time, not device measurement time.
- Users have no timezone preference; Web Console uses `toLocaleString(undefined, …)` (browser default).
- Device objects have no `timeZone` metadata; drivers parse local device clocks without a declared zone.
- Calendar-boundary queries («за сегодня», shift 08:00–20:00, daily reports) are ambiguous without an explicit timezone contract.

Industry practice (SCADA historians, observability, financial systems): **store UTC, display local, never persist naked local time without a zone**.

## Decision

### 1. Canonical storage — UTC only

- Server-side: `java.time.Instant` everywhere in domain, persistence, and messaging.
- API and WebSocket: ISO-8601 with `Z` (e.g. `2026-06-30T12:00:00.000Z`).
- ClickHouse / PostgreSQL columns remain UTC; no migration to local storage.

### 2. Zone identifiers — IANA only

- Use TZ database IDs (`Europe/Moscow`, `Asia/Yekaterinburg`), not fixed offsets (`UTC+3`).
- Invalid or unknown IDs fall back to `UTC` with a logged warning.

### 3. User timezone (display layer)

- Each platform user has a `timeZone` preference (IANA), default `UTC`.
- Stored in `platform_users` and mirrored on the user object in the object tree.
- Web Console persists `ispf.ui.timeZone` in `localStorage`; optional initial guess from `Intl.DateTimeFormat().resolvedOptions().timeZone`.
- All UI time labels and **calendar-relative ranges** («today», «yesterday», shift windows) use the **user** timezone.
- Central helper: `formatDateTime(iso, { timeZone, locale })` — replace ad-hoc `toLocaleString` calls.

### 4. Device timezone (edge / normalization)

- Optional `timeZone` on `DEVICE` (RELATIVE model mixin or device-driver blueprint extension).
- **Inheritance:** device → parent folder/site → platform default `UTC`.
- Drivers that receive **local** device timestamps must normalize to UTC using the resolved device timezone.
- Devices that send UTC epoch or ISO with offset do not require device TZ.

### 5. Dual timestamps for telemetry (when device supplies time)

| Field | Semantics |
|-------|-----------|
| `observedAt` | Measurement time on device, normalized to UTC — **primary for charts and operator view** |
| `ingestedAt` | When platform received/wrote the sample (today's `sampledAt`) — latency, ordering, ops |

If `observedAt` is absent, `observedAt := ingestedAt` (backward compatible).

### 6. Events

- `POST /api/v1/events/fire` may include optional `occurredAt` (ISO-8601 UTC).
- Default: server `Instant.now()` (current behaviour).
- Validation: reject if too far in the future (configurable skew, e.g. 5 min) or beyond retention.

### 7. API query contract

- `from` / `to` query params remain **absolute UTC** instants.
- Calendar-boundary aggregation (local midnight buckets, shifts) accepts optional `timeZone` query param (user TZ or explicit IANA).
- Rolling windows (`1h`, `24h`, `7d`) remain duration-based UTC unless labelled as calendar ranges.

### 8. Platform internals

- Spring `@Scheduled` retention/cleanup jobs stay on server clock / UTC — not user TZ.
- Bundle `schedules[]` with `intervalMs` unchanged in v1; future cron schedules require explicit `timeZone`.

## In scope (phased — see BL-66…71)

- ADR + inventory spike
- User TZ preference + UI formatting
- Device TZ metadata + inheritance
- Historian `observedAt` / driver SPI extension
- Calendar-boundary history queries and reports
- Event `occurredAt` override

## Out of scope (v1)

- Storing local time in the database without zone
- Auto-detecting device TZ from GPS/IP without explicit configuration
- Translating server `error.message` or AI agent replies by timezone
- User-authored object `displayName` and bundle content
- Keycloak `timezone` claim sync (optional follow-up to BL-67)

## DST and ambiguous local times

- IANA zones handle DST transitions for display and normalization.
- **Ambiguous local time** (fall-back hour): prefer the **earlier** offset (first occurrence) when parsing device-local strings; document in driver integration guide.
- **Gap hour** (spring forward): invalid local times are rejected with a clear validation error.

## Consequences

- Positive: consistent operator experience across sites; correct shift/day reports; device timestamps preserved when edge provides them.
- Migration: additive schema (`time_zone` column, optional `observed_at`); existing data treated as `observedAt = ingestedAt`.
- UI: one formatting utility reduces inconsistent browser behaviour.
- Drivers: optional `observedAt` in write path; no breaking change for drivers that only poll current values.

## Implementation waves

| Wave | BL | Deliverable |
|------|-----|-------------|
| 1 | BL-66 | This ADR accepted + hot-path inventory |
| 2 | BL-67, BL-68 | User + device TZ metadata |
| 3 | BL-69, BL-71 | Historian dual timestamps, event `occurredAt` |
| 4 | BL-70 | Calendar-boundary queries and reports |

## Appendix — known hot paths (inventory)

| Area | Current behaviour | File(s) |
|------|-------------------|---------|
| Event fire timestamp | `Instant.now()` | `ObjectEvent.of()` |
| Historian write | `Instant.now()` ingestion | `VariableHistoryService` |
| CH / PG storage | UTC | `ClickHouseVariableHistoryStore`, `VariableSampleEntity` |
| UI charts/history | `toLocaleString()` browser default | `useVariableHistory.ts`, chart widgets |
| Journals | `toLocaleString()` | `ObjectChangeHistoryPanel`, journal export |
| Metrics | `toLocaleString()` | `SystemMetricsView` |
| User profile | no TZ field | `platform_users`, `PlatformUserStore` |
| Device blueprint | no TZ field | device-driver structure, `DRIVERS.md` |
| Shell preferences | locale + theme only | `ShellPreferences.tsx` |

## Related

- [ROADMAP.md § Phase 21](../ROADMAP.md#phase-21--time--timezones)
- [ROADMAP.md § Phase 21 (BL-66…71)](../ROADMAP.md#phase-21--time--timezones)
- [FW-60](../ROADMAP.md#часть-b--req-fw-framework-закрыт)
- [ADR-0013](0013-web-console-i18n.md) — locale vs timezone (orthogonal concerns)
