> **Language:** Canonical English. Russian edition: [ru/tutorial-ot-workflow-triggers.md](../ru/tutorial-ot-workflow-triggers.md).

# Tutorial: workflow webhook, cron, and failure recovery

> **Status:** Beta — ADR-0049 Wave 3. Hub: [OT Automation tutorials](ot-automation-excellence-tutorials.md).

## Goal

Start workflows from an HTTP webhook and a simple cron expression; route failures to an error workflow / dead-letter queue.

## Prerequisites

[Hub](ot-automation-excellence-tutorials.md#prerequisites). Workflow must be **ACTIVE**.

## A. Webhook trigger

### 1. Configure

On the WORKFLOW object:

| Variable | Example |
|----------|---------|
| `webhookSlug` | `lab-alarm-hook` |
| `status` | `ACTIVE` |

### 2. Fire

```bash
curl -s -X POST "$BASE/api/v1/webhooks/workflows/lab-alarm-hook" \
  -H 'Content-Type: application/json' \
  -d '{"alarmId":"A-9","severity":"high"}' | jq .
```

Payload fields are available as workflow input / variables (stringified as needed). Confirm a new run in `GET .../by-path/runs`.

## B. Cron trigger

### 1. Configure

| Variable | Example |
|----------|---------|
| `cronExpression` | `every:1m` |
| `status` | `ACTIVE` |

### 2. Wait and verify

After ~1–2 minutes, `GET .../by-path/runs` should show new instances with cron starts. Use a cheap BPMN body (single `log`) on demostands.

> Note: supported expression form on current demostand is the `every:…` shorthand documented in [workflows](workflows.md) — not full crontab syntax unless listed there.

## C. Failure recovery

### 1. Error workflow

| Variable | Example |
|----------|---------|
| `errorWorkflowPath` | `root.platform.workflows.error-handler` |
| `retryMaxAttempts` | `2` |
| `retryBackoffSeconds` | `30` |

Ensure `error-handler` exists and is ACTIVE. On FAILED, the platform records a dead letter and may start the error workflow (see [workflows](workflows.md) / ADR-0049 — avoid recursive sync retries).

### 2. Inspect DLQ

Failed runs that exhaust retry policy land in `workflow_dead_letters` (ops/SQL or platform diagnostics depending on build). Use the execution journal of the failed instance first for MTTR.

## Verify

- [ ] Webhook creates a run with payload fields
- [ ] Cron creates periodic runs while ACTIVE
- [ ] Forced failure (bad action / missing param) surfaces in journal; error path / DLQ behaves as configured

## Next

[Workflow as tool](tutorial-ot-workflow-as-tool.md) · [AI in BPMN](tutorial-ot-ai-bpmn.md)
