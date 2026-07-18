> **Language:** Canonical English. Russian edition: [ru/tutorial-ot-analytics-ai.md](../ru/tutorial-ot-analytics-ai.md).

# Tutorial: Analytics AI (deterministic first, LLM second)

> **Status:** Beta — ADR-0049 Wave 2. Hub: [OT Automation tutorials](ot-automation-excellence-tutorials.md).

## Goal

Use analysis helpers and agent tools on historian series, then narrate with **Ask AI** in the tag inspector.

## Prerequisites

- [Hub](ot-automation-excellence-tutorials.md#prerequisites)
- At least one analytics tag / variable with history samples
- Optional AI for narration (noop still returns a stub narrative when AI disabled)

Reference: [analytics-formulas-and-packs § Analysis functions](analytics-formulas-and-packs.md#analysis-functions-adr-0049).

## A. Deterministic analysis (agent tools)

In AI Studio (admin), or via agent tool execute API, try:

| Tool | Purpose |
|------|---------|
| `list_analytics_catalog` | Discover analysis / historian functions |
| `get_analytics_tag` | Tag metadata by path |
| `query_analytics_tags` | Query tag catalog |
| `evaluate_analytics_expression` | Evaluate expression against object path |
| `detect_anomalies` | z-score / anomaly helpers on a series |
| `compare_periods` | Period-over-period |
| `summarize_trend` | Build summary then (optionally) LLM narrative |

Example agent intent:

> Use `summarize_trend` on `root.platform.devices.demo-sensor-01` variable `temperature` for the last 4 hours.

Confirm the tool returns a **structured summary** (stats) before any prose.

## B. Ask AI in Web Console

1. Open Object tree → analytics tag (or Computations / tag inspector).
2. Click **Ask AI** (readonly inspector and CEL editor actions).
3. Read the narrative under the button; underlying summary is deterministic.

API equivalent:

```bash
curl -s -X POST "$BASE/api/v1/platform/analytics/tags/ask?objectPath=root.platform.devices.demo-sensor-01&variable=temperature&hours=4" \
  -H "Authorization: Bearer $TOKEN" | jq '{status, summary, narrative}'
```

## C. From BPMN (optional)

Use `invoke_agent` with allowlist including `summarize_trend,detect_anomalies,get_variable_history` — see [AI in BPMN](tutorial-ot-ai-bpmn.md). Do not put CONTROL writes in that allowlist.

## Verify

- [ ] `list_analytics_catalog` shows analysis kinds
- [ ] `summarize_trend` / Ask AI return `summary` + `narrative`
- [ ] With AI disabled, narrative is a noop stub but summary still present

## Out of scope

Forecast / streaming ML inference — BL-175+, not ADR-0049.

## Next

[Hub](ot-automation-excellence-tutorials.md) · [AI in BPMN](tutorial-ot-ai-bpmn.md)
