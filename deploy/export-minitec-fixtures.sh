#!/bin/bash
# Export mini-TEC reference fixtures from a running ISPF (prod/lab).
# Usage on VPS: bash export-minitec-fixtures.sh [output-dir]
set -euo pipefail

API="${API:-http://127.0.0.1:8080}"
OUT="${1:-/tmp/minitec-fixtures-export}"

mkdir -p "$OUT/dashboards" "$OUT/mimics"

log() { echo "==> $*"; }

TOKEN=$(curl -sf -X POST "${API}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("token",""))')

if [ -z "$TOKEN" ]; then
  echo "Login failed" >&2
  exit 1
fi

auth_hdr=(-H "Authorization: Bearer ${TOKEN}")

log "Export application bundle"
curl -sf "${API}/api/v1/applications/mini-tec/export" "${auth_hdr[@]}" \
  | python3 -c '
import json, sys
raw = json.load(sys.stdin)
manifest = raw.get("manifest") or raw
with open(sys.argv[1] + "/bundle-export.json", "w", encoding="utf-8") as f:
    json.dump(raw, f, ensure_ascii=False, indent=2)
with open(sys.argv[1] + "/bundle.json", "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
' "$OUT"

log "Export operator UI"
curl -sf "${API}/api/v1/applications/mini-tec/operator-ui" "${auth_hdr[@]}" \
  > "$OUT/operator-ui.json" || echo '{}' > "$OUT/operator-ui.json"

DASHBOARDS=(
  root.platform.dashboards.mini-tec-hmi
  root.platform.dashboards.mini-tec-overview
  root.platform.dashboards.mini-tec-single-line
  root.platform.dashboards.mini-tec-kpi
  root.platform.dashboards.mini-tec-trends
  root.platform.dashboards.mini-tec-gpu-detail
  root.platform.dashboards.mini-tec-grpb
  root.platform.dashboards.mini-tec-rumb
  root.platform.dashboards.mini-tec-dgu
  root.platform.dashboards.mini-tec-load-module
  root.platform.dashboards.mini-tec-protections
  root.platform.dashboards.mini-tec-exploitation
)

for path in "${DASHBOARDS[@]}"; do
  name="${path##*.}"
  log "Dashboard $name"
  curl -sf "${API}/api/v1/dashboards/by-path?path=${path}" "${auth_hdr[@]}" \
    | python3 -c '
import json, sys
o = json.load(sys.stdin)
layout = o.get("layoutJson") or "{}"
name = sys.argv[1]
out = sys.argv[2]
with open(f"{out}/dashboards/{name}.json", "w", encoding="utf-8") as f:
    if isinstance(layout, str):
        f.write(layout)
    else:
        json.dump(layout, f, ensure_ascii=False, indent=2)
' "$name" "$OUT"
done

MIMICS=(
  root.platform.mimics.mini-tec-single-line
  root.platform.mimics.mini-tec-zone-gas
  root.platform.mimics.mini-tec-zone-electrical
)

for path in "${MIMICS[@]}"; do
  name="${path##*.}"
  log "Mimic $name"
  curl -sf "${API}/api/v1/mimics/by-path?path=${path}" "${auth_hdr[@]}" \
    | python3 -c '
import json, sys
o = json.load(sys.stdin)
diagram = o.get("diagramJson") or "{}"
name = sys.argv[1]
out = sys.argv[2]
path = f"{out}/mimics/{name}.json"
parsed = json.loads(diagram) if isinstance(diagram, str) else diagram
with open(path, "w", encoding="utf-8") as f:
    json.dump(parsed, f, ensure_ascii=False, indent=2)
' "$name" "$OUT"
done

log "Export complete: $OUT"
ls -la "$OUT" "$OUT/dashboards" "$OUT/mimics"
