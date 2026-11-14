# Application message relay

HTTP, NATS JetStream, and Kafka transports for `application_outbox`.

| Claim | Status |
|-------|--------|
| Outbox HTTP delivery | **Done** — `HttpOutboxDelivery` / `ApplicationOutboxPump` |
| Outbox NATS JetStream delivery | **Done** — `NatsOutboxDelivery` / `NatsOutboxPump` (`ispf.application.outbox.nats-connectors`) |
| Outbox Kafka delivery | **Done** — `KafkaOutboxDelivery` / `KafkaOutboxPump` (`ispf.application.outbox.kafka-bootstrap` + `kafka-topics`) |
| Late historian dirty rollup rewrite | **Done** — in-memory `HistorianRollupMaterializerService.noteLateSample` (lost on restart; use rebuild) |
| Plant artifact path survives base upgrade | **Done** — bundle apply is additive; paths absent from the new manifest are not deleted |

Configure one connector on at most one transport. The HTTP, NATS, and Kafka pumps claim disjoint connector sets.
