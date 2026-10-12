# ADR-0067: Зрелость FIELD и CERTIFIED (выше PRODUCTION)

## Статус

**Accepted** (2026-10-12) — задаёт evidence-тиры и гейты; **ни один драйвер сегодня не помечен FIELD/CERTIFIED**. Enum `FIELD` / `CERTIFIED` + пустые evidence maps + CI — landed как follow-up волны 1.

Канон: [../../en/decisions/0067-field-certified-maturity.md](../../en/decisions/0067-field-certified-maturity.md).

## Контекст

[ADR-0022](0022-driver-production-matrix.md): `PRODUCTION` = **lab CI**. Для полевого доверия этого мало. Playbook уже делит **playbook-ready** и **ready-for-field**; enum драйвера теперь включает FIELD/CERTIFIED (строк в матрице пока нет).

## Решение

### Тиры драйвера

| Тир | Смысл |
|-----|--------|
| STUB / BETA / PRODUCTION | Как в 0022 (lab CI для PRODUCTION) |
| **FIELD** | Именованная **заводская** площадка + soak ≥7д + OT sign-off **по драйверу** |
| **CERTIFIED** | FIELD (или waiver) + conformance / сертификат |

Enum `FIELD` / `CERTIFIED` есть; строк в матрице с ними пока нет.

### Не путать с marketplace-пилотами

| Полоса | Что это | Сегодня |
|--------|---------|---------|
| **Marketplace / solution pilots** | Бандлы в `examples/marketplace-catalog/` (HVAC, tank-farm, oil-control, MES, twins, …) | **Рабочие / REAL** |
| **Lab OT Trust** (BL-140) | Lab VLAN soak Modbus/MQTT/OPC UA | Lab evidence; не customer-plant FIELD |
| **Driver FIELD/CERTIFIED** | Enum + evidence map протокола на named plant | **0 строк** |

Рабочий marketplace-пилот **не** повышает драйвер до `FIELD`. `FIELD count = 0` **не** значит «нет рабочих пилотов».

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

Marketplace / solution pilots — **много, рабочие**. Driver FIELD = **0**; CERTIFIED = **0**; enum есть, строк в матрице нет.

## Последствия

Правки 0022, driver-promotion, field-pilot-playbook, scorecard. Follow-up: заполнить evidence maps после named-plant gate.

## Связанное

- [0022](0022-driver-production-matrix.md), [0057](0057-ot-trust-wave1-dnp3-poll-only.md), [field-pilot-playbook](../field-pilot-playbook.md), [marketplace](../marketplace.md)
