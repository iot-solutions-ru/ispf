> **Язык:** русская версия. Канонический английский: [en/decisions/0062-correlator-keyed-dispatch.md](../../en/decisions/0062-correlator-keyed-dispatch.md).

# ADR-0062: Keyed dispatch коррелятора и транзакции на каждый коррелятор

## Статус

Accepted — 2026-10-09. Hub: [doc-status.md](../doc-status.md).

## Контекст

`EventCorrelatorListener` — единственный подписчик шины. Совпавшие корреляторы оценивались в одном цикле `@Transactional` на `processEventFired`. Из-за этого:

1. Сбой вложенной записи одного коррелятора мог пометить общую транзакцию rollback-only и откатить соседние.
2. Блокирующие `SEND_WEBHOOK` / email / SMS удерживали полосу автоматизации для всех корреляторов того же события.
3. Параллельные воркеры автоматизации могли оценивать один и тот же коррелятор не по порядку (`Instant.now()` на каждое попадание) и ломать SEQUENCE / cooldown.

## Решение

1. **Одна подписка, keyed fan-out** — `AutomationRuleIndex` по `eventName` сохраняется; каждый коррелятор идёт в однопоточную полосу по хешу `correlatorId` (`CorrelatorKeyedExecutor`).
2. **`REQUIRES_NEW` на каждый коррелятор** — оценка паттерна, попадания окна и `lastTriggeredAt` коммитятся независимо (`TransactionTemplate`).
3. **Асинхронные уведомления** — после commit действия `SEND_*` выполняются на `CorrelatorActionExecutor`; workflow / `SET_VARIABLE` / `FIRE_EVENT` остаются на полосе оценки.
4. **Время события** — окна и cooldown считают по `ObjectChangeEvent.timestamp()`, не по wall clock в момент dequeue.
5. **Синхронный режим для тестов** — `ispf.correlator.async-dispatch=false` и `async-actions=false` в `application-test.yml`, чтобы HTTP acceptance-тесты оставались детерминированными.

## Владение в кластере (отложено)

Владение коррелятором на нескольких репликах (один лидер на id) **вне scope** этого ADR. Хранилища окон Redis/JDBC уже делят попадания; полное последовательное владение между репликами может следовать паттернам driver-lock из ADR-0028, когда это понадобится.

## Последствия

- Сломанный фильтр или разбор payload роняет один коррелятор и не прерывает соседние (#427 и этот ADR).
- Конфигурация: `ispf.correlator.*` (lanes, queues, флаги async).
- Метрики: `ispf.correlator.errors.total`, счётчики drop dispatch/action и gauge очередей.

## Ссылки

- [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md)
- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md)
- [automation.md](../automation.md)
