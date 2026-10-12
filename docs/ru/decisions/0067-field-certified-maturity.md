# ADR-0067: Зрелость FIELD и CERTIFIED (выше PRODUCTION)

## Статус

**Accepted** (2026-10-12) — задаёт evidence-тиры и гейты; **ни один драйвер сегодня не помечен FIELD/CERTIFIED**. Расширение API enum — follow-up.

Канон: [../../en/decisions/0067-field-certified-maturity.md](../../en/decisions/0067-field-certified-maturity.md).

## Контекст

[ADR-0022](0022-driver-production-matrix.md): `PRODUCTION` = **lab CI**. Для полевого доверия этого мало. Playbook уже делит **playbook-ready** и **ready-for-field**, но enum драйвера — только PRODUCTION/BETA/STUB.

## Решение

### Тиры

| Тир | Где | Смысл |
|-----|-----|--------|
| STUB / BETA / PRODUCTION | `DriverMaturity` | Как в 0022 |
| **FIELD** | Evidence + playbook (волна 1) | Именованная площадка + soak ≥7д + OT sign-off |
| **CERTIFIED** | Evidence (волна 1) | FIELD (или явный waiver) + conformance / сертификат |

В волне 1 enum **не** расширяется.

### Гейт FIELD

1. Именованная полевая задача (площадка, протокол, тикет) — [ready-for-field](../field-pilot-playbook.md#ready-for-field-gate-policy).
2. Драйвер(ы) уже PRODUCTION по заявленным capabilities.
3. Soak ≥ 7 дней + журнал в `docs/evidence/`.
4. Write round-trip, если заявлен WRITE (или явный POLL-only, как DNP3 / 0057).
5. Sign-off OT / владельца площадки.

Lab soak **без** C1 named site ≠ FIELD.

### Гейт CERTIFIED

FIELD (или waiver) + артефакт conformance/сертификации, согласованный с capabilities матрицы.

### Честность (2026-10-12)

FIELD = **0** драйверов; CERTIFIED = **0**; enum FIELD/CERTIFIED — **ещё нет**.

## Последствия

Правки 0022, driver-promotion, field-pilot-playbook, scorecard. Follow-up: enum + CI на evidence paths.

## Связанное

- [0022](0022-driver-production-matrix.md), [0057](0057-ot-trust-wave1-dnp3-poll-only.md), [field-pilot-playbook](../field-pilot-playbook.md)
