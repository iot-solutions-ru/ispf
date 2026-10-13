> **Язык:** русская версия. Канонический английский: [en/decisions/0061-high-rate-telemetry-history-defaults.md](../../en/decisions/0061-high-rate-telemetry-history-defaults.md).

# ADR-0061: Defaults истории для high-rate телеметрии

## Статус

**Accepted** (2026-10-08)

## Контекст

Унаследованные конфигурации MQTT-устройств часто по умолчанию сохраняли каждое входящее событие `message`. Полевые команды добавляли правила retention уже после инцидентов в production. Для путей с высоким темпом ISPF должен выбирать **безопасные** значения по умолчанию.

## Решение

1. Default драйвера MQTT: `eventToVariable=false` (переменные last-value / coalesce ingress).
2. У новых объектов `eventJournalEnabled=false`, пока автор явно его не включит.
3. `Variable.includePreviousValueInEvent` остаётся **opt-in** (размер payload / потребность автоматизации).
4. Аналитика по временным корзинам использует rollup historian (`kind: historian`, `rollupBuckets`), а не гранулы событий на каждое сообщение.
5. Документация бандлов и авторства указывает на [anti-patterns.md](../anti-patterns.md) § MQTT и § Hand-rolled time buckets.

## Последствия

- Демо с высоким темпом остаются отзывчивыми, история не растёт незаметно.
- Аудиторы включают журнал и previous-value только там, где это требуется.
- Агенты не включают `eventJournalEnabled` на MQTT-устройствах catch-all без retention.

## Связанные

- [anti-patterns](../anti-patterns.md)
- [variable-history](../variable-history.md)
- [lab-mqtt-historian-stress](../lab-mqtt-historian-stress.md)
