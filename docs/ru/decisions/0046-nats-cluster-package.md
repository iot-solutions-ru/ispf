# ADR-0046: NATS в пакете `cluster` + skip persist для TRANSIENT

Канонический текст: [../../en/decisions/0046-nats-cluster-package.md](../../en/decisions/0046-nats-cluster-package.md).

**Кратко:** `Nats*` перенесены в `com.ispf.server.cluster`; `persistBindingRuleTarget` не пишет TRANSIENT в БД.
