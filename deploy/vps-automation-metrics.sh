#!/usr/bin/env bash
TOKEN=$(curl -sf -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")
curl -sf http://127.0.0.1:8080/api/v1/platform/metrics -H "Authorization: Bearer ${TOKEN}" | python3 <<'PY'
import json, sys
for s in json.load(sys.stdin).get("sections", []):
    if s.get("id") == "automation":
        v = s.get("values", {})
        for k in ("eventsFiredTotal", "eventJournalFlushedTotal", "eventJournalSyncFallbackTotal", "eventJournalQueueSize"):
            print(k, v.get(k))
        break
PY
