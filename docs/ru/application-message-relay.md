# Реле сообщений приложений

Транспорты HTTP, NATS JetStream и Kafka для `application_outbox`.

| Утверждение | Статус |
|-------------|--------|
| Доставка outbox по HTTP | **Done** — `HttpOutboxDelivery` / `ApplicationOutboxPump` |
| Доставка outbox в NATS JetStream | **Done** — `NatsOutboxDelivery` / `NatsOutboxPump` (`ispf.application.outbox.nats-connectors`) |
| Доставка outbox в Kafka | **Done** — `KafkaOutboxDelivery` / `KafkaOutboxPump` (`ispf.application.outbox.kafka-bootstrap` + `kafka-topics`) |
| Пересчёт dirty rollup при опоздавшей точке | **Done** — в памяти `HistorianRollupMaterializerService.noteLateSample` (теряется при рестарте; тогда rebuild) |
| Путь заводского артефакта переживает обновление базы | **Done** — apply бандла аддитивен; пути вне нового манифеста не удаляются |

Один connector настраивайте только на одном транспорте. Насосы HTTP, NATS и Kafka забирают непересекающиеся наборы connector.
