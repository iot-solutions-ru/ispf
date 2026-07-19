# ADR-0049: OT Automation Excellence

Канонический текст: [../../en/decisions/0049-ot-automation-excellence.md](../../en/decisions/0049-ot-automation-excellence.md).

**Кратко (Accepted 2026-07-19):** программа OT Automation Excellence — journal шагов BPMN, typed workflow tools, AI service tasks (`LLM_COMPLETE` / `INVOKE_AGENT`), analytics analysis functions + AI tools, webhook/cron + retry/DLQ, credentials vault, template gallery, MCP publish. Wave 1 — foundation. Не меняет freeze element types ADR-0047; расширяет `serviceTask` actions. Forecast/ML inference — вне scope (BL-175+).

**Прогресс волн (2026-07-19):** Wave 1–2 landed; Wave 3 **landed** — webhook + cron `every:1m` + async retry (`workflow_retry_schedule` / `retryMaxAttempts` / `retryBackoffSeconds`) + DLQ/`errorWorkflowPath` после исчерпания + REST list/resolve dead letters. Wave 4–5 не начаты. Канон: [en/0049](../../en/decisions/0049-ot-automation-excellence.md).

**Практика:** [OT Automation туториалы](../ot-automation-excellence-tutorials.md) (EN канон: [en/…](../../en/ot-automation-excellence-tutorials.md)).
