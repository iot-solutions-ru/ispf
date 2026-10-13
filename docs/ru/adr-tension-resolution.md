> **Язык:** русская версия. Канон: [en/adr-tension-resolution.md](../en/adr-tension-resolution.md).

# Снятие напряжений между ADR

> **Статус:** Charter — программа claim boundaries.

Цель: одна claim-ячейка → один ответ. Таблица волн, режимы LOGIC_HOST и чеклист C1 FIELD — в EN-каноне.

**Статус программы (2026-10-13):** волны **1A–2B** Closed на `main` ([#499](https://github.com/iot-solutions-ru/ispf/pull/499), CH opt-in [#503](https://github.com/iot-solutions-ru/ispf/pull/503), cookie-gate [#505](https://github.com/iot-solutions-ru/ispf/pull/505)). Открыто: 3A FIELD (customer plant), HTTP deploy без `--strict`, Cosign / signed air-gap, separate ui-pack origin не заявлен.

| Напряжение | Статус | Волна |
|------------|--------|-------|
| Secrets envelope | Closed (0065) | — |
| FIELD vs marketplace | Closed (0067 §1b) | — |
| Poka-yoke vs WARNING | Closed (Wave 1A) | 1A |
| Ручки vs профили | Closed (Wave 1B, #493) | 1B |
| Тихая деградация кластера | Closed (Wave 1C, #494) | 1C |
| Ui-pack origin | Closed (Wave 2A, #505 cookie) | 2A |
| Пять TS stores | Closed Partial (Wave 2B, #503 CH opt-in) | 2B |
| Широта vs глубина | Planned (C1 customer plant + matrix) | 3A |
| KMS / ГОСТ | Closed (0076 non-claim, SPI по тендеру) | 3B |

## Связанное

[en/adr-tension-resolution](../en/adr-tension-resolution.md) · [decisions/readme](decisions/readme.md)
