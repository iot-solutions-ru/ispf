> **Язык:** русская версия. Канонический английский: [en/decisions/0057-ot-trust-wave1-dnp3-poll-only.md](../../en/decisions/0057-ot-trust-wave1-dnp3-poll-only.md).

# ADR-0057: OT Trust Wave 1 — DNP3 остаётся PRODUCTION и только POLL

## Статус

**Accepted** (2026-09-05)

## Контекст

Phase 25: OT Trust снят с парковки для Wave 1. Документы roadmap и promotion ошибочно утверждали, что BL-191 понизил `dnp3` и `ethernet-ip` до **BETA**. Код матрицы этого не делал.

## Решение

1. **`dnp3`** остаётся **`PRODUCTION`** с capabilities **только `POLL`**.
   - Путь read / class poll покрыт loopback-тестами.
   - `writePoint` бросает «not implemented» — это **объявленный пробел capability**, а не оболочка-заглушка.
   - Отсутствие write само по себе не повод ставить метку BETA (тот же паттерн, что у других честных драйверов POLL_ONLY в PRODUCTION).
   - Запись DNP3 — **отдельная именная задача** (чтобы Wave 1 шла дальше, она не требуется).

2. **`ethernet-ip`** остаётся **`PRODUCTION`** с **`POLL` + `WRITE` + `QUALITY`**. Запись CIP реализована; пометку BETA не возвращать.

3. **`opc-da` / `opc-bridge`** остаются **`BETA`** (оболочки) и остаются в `TOP_20_NON_PRODUCTION_EXEMPT`.

4. В матрице у **`http`** capabilities должны включать **`WRITE`** там, где реализован `HttpDeviceDriver.writePoint` (честность: занижение WRITE — по-прежнему баг матрицы).

5. Полевой трек Wave 1 начинается с **лабораторного пилота Modbus TCP** (фикстуры в `deploy/driver-interop/`), не с завода заказчика, пока не появится именная площадка.

## Последствия

- Верная формулировка BL-191 «Done»: оболочки → BETA, CI-gate на javadoc заглушек, у DNP3 нет ложного WRITE.
- Остаток OT Trust: smoke записи interop, журнал soak Modbus, позже — опциональная запись DNP3.
- На доске парковки **P-OT** переходит в **In progress** под этой именной задачей Wave 1.

## Ссылки

- [0022-driver-production-matrix](0022-driver-production-matrix.md)
- [roadmap § Wave 1](../roadmap.md#s31-wave-1-execution-backlog)
- [driver-interop-lab](../driver-interop-lab.md)
- Evidence: `docs/evidence/ot-trust/`
