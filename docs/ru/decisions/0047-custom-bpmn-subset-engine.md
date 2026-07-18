# ADR-0047: Собственный subset-движок BPMN (без Camunda/Flowable)

Канонический текст: [../../en/decisions/0047-custom-bpmn-subset-engine.md](../../en/decisions/0047-custom-bpmn-subset-engine.md).

**Кратко (Accepted, 2026-07-18):** оставляем `ispf-plugin-workflow`; не встраиваем Camunda/Flowable. BL-176 (embedded `subProcess` + message catch/throw) закрыт; freeze — неподдерживаемые элементы отклоняются при parse. Документация: [workflows](../workflows.md).
