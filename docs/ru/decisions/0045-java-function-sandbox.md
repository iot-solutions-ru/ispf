# ADR-0045: Песочница Java-функций (фаза 1)

Канонический текст: [../../en/decisions/0045-java-function-sandbox.md](../../en/decisions/0045-java-function-sandbox.md).

**Кратко:** `ispf.function.java.enabled` (в `prod` по умолчанию `false`; local/test/dev — `true`); усиленный regex denylist; process isolate — отдельный spike. Код пишут только админы (trusted author).
