# OT Alarm Classify (ADR-0049)

Reference workflow for [OT Automation Excellence tutorials](../../docs/en/ot-automation-excellence-tutorials.md).

Deploys `root.platform.workflows.ot-alarm-classify`:

- ACTIVE + typed tool contract (`inputSchemaJson` / `outputSchemaJson` / `toolDescription`)
- BPMN `llm_complete` → instance var `llmClassification`
- Webhook slug `ot-alarm-classify`
- MCP publish as `wf_ot_alarm_classify` when MCP is enabled

## Deploy

**Marketplace / free-download** path accepts this listing (`artifactKind=workflow-template`).

**Direct** `POST /api/v1/applications/.../deploy` on hardened demostands requires a signed `license` block (`ispf.license.require-signed-bundles=true`). For local lab with signing off:

```bash
export BASE=http://localhost:8080
export TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)

curl -s -X POST "$BASE/api/v1/applications/ot-alarm-classify/deploy" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary @examples/ot-alarm-classify/bundle.json | jq .
```

On signed-only hosts, create the WORKFLOW via Object API + `PUT .../bpmn` + tool-contract variables (same fields as in `bundle.json`), then `status=ACTIVE`.

## Invoke

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/invoke-tool?path=root.platform.workflows.ot-alarm-classify" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{"alarmId":"A-42","alarmMessage":"Tank T-01 high level"}}' | jq .

curl -s -X POST "$BASE/api/v1/webhooks/workflows/ot-alarm-classify" \
  -H 'Content-Type: application/json' \
  -d '{"alarmId":"A-9","alarmMessage":"Pump trip"}' | jq .
```

Marketplace listing: [../marketplace-catalog/ot-alarm-classify/](../marketplace-catalog/ot-alarm-classify/).
