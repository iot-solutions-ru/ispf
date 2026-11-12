> **Язык:** русская версия. Канон: [../../en/decisions/0049-ot-automation-excellence.md](../../en/decisions/0049-ot-automation-excellence.md).

# ADR-0049: OT Automation Excellence

## Статус

**Accepted** (2026-07-19) — программный ADR. Волны 1 и 3 **Done**; волна 2 **Partial** (`LLM_COMPLETE` Done; полноценный tool-loop `INVOKE_AGENT` — Gap); волна 4 **Partial**; волна 5 **Gap** (MCP).

## Суть

Пять волн над freeze BPMN subset ([0047](../../en/decisions/0047-custom-bpmn-subset-engine.md)): journal → AI serviceTask → triggers/DLQ → vault → MCP. Инварианты и non-goals — в EN. Практика: [ot-automation-excellence-tutorials](../ot-automation-excellence-tutorials.md).

## Связанное

[readme](readme.md) · [en/0049](../../en/decisions/0049-ot-automation-excellence.md) · [0065](0065-secrets-key-rotation.md)
