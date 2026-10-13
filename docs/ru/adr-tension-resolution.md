> **Язык:** русская версия. Канон: [en/adr-tension-resolution.md](../en/adr-tension-resolution.md).

# Снятие напряжений между ADR

> **Статус:** Charter — программа claim boundaries.

Цель: одна claim-ячейка → один ответ. Таблица волн, режимы LOGIC_HOST и чеклист C1 FIELD — в EN-каноне.

**Статус программы (2026-10-13):** волны **1A–2B** Closed; ship deploy [#513](https://github.com/iot-solutions-ru/ispf/pull/513). Air-gap RSA require-signed default **Done** ([air-gap-signing](../evidence/air-gap-signing/README.md)); Cosign image signing — **Gap**. Открыто: 3A FIELD, Cosign verify evidence, separate ui-pack origin.

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
