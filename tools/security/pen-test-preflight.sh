#!/usr/bin/env bash
# Read-only pen-test preflight: pin version / auth mode / public surface for G-01 kickoff.
# Does NOT authenticate, fuzz, or probe private APIs.
# Usage: bash tools/security/pen-test-preflight.sh https://host [--out file.json]
set -euo pipefail

BASE="${1:-}"
OUT=""
if [[ "${2:-}" == "--out" ]]; then
  OUT="${3:?--out requires path}"
fi
if [[ -z "$BASE" || "$BASE" == "--help" || "$BASE" == "-h" ]]; then
  echo "Usage: $0 https://ispf-host [--out preflight.json]" >&2
  echo "Read-only: /api/v1/info, /actuator/health, /api/v1/auth/config, optional OpenAPI paths, response headers." >&2
  exit 2
fi
BASE="${BASE%/}"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

curl_code() {
  local url="$1" dest="$2"
  curl -sS -o "$dest" -w '%{http_code}' --connect-timeout 10 --max-time 30 "$url" || echo "000"
}

curl_headers() {
  local url="$1" dest="$2"
  curl -sS -D "$dest" -o /dev/null --connect-timeout 10 --max-time 30 "$url" || true
}

code_info="$(curl_code "${BASE}/api/v1/info" "$tmpdir/info.json")"
code_health="$(curl_code "${BASE}/actuator/health" "$tmpdir/health.json")"
code_auth="$(curl_code "${BASE}/api/v1/auth/config" "$tmpdir/auth.json")"

# Optional published API docs (status only — not a crawl)
code_openapi_v3="$(curl_code "${BASE}/v3/api-docs" "$tmpdir/openapi_v3.json")"
code_swagger_ui="$(curl_code "${BASE}/swagger-ui.html" "$tmpdir/swagger_ui.html")"
code_swagger_ui_index="$(curl_code "${BASE}/swagger-ui/index.html" "$tmpdir/swagger_ui_index.html")"

curl_headers "${BASE}/api/v1/info" "$tmpdir/info.headers"
curl_headers "${BASE}/" "$tmpdir/root.headers"

JSON_DOC="$(
  INFO_JSON="$tmpdir/info.json" HEALTH_JSON="$tmpdir/health.json" AUTH_JSON="$tmpdir/auth.json" \
  INFO_HDR="$tmpdir/info.headers" ROOT_HDR="$tmpdir/root.headers" \
  BASE_URL="$BASE" CODE_INFO="$code_info" CODE_HEALTH="$code_health" CODE_AUTH="$code_auth" \
  CODE_OA="$code_openapi_v3" CODE_SW="$code_swagger_ui" CODE_SWI="$code_swagger_ui_index" \
  python3 - <<'PY'
import json, os, datetime
from pathlib import Path

def load(path):
    try:
        return json.loads(Path(path).read_text())
    except Exception:
        return None

INTEREST = (
    "strict-transport-security",
    "content-security-policy",
    "x-frame-options",
    "x-content-type-options",
    "referrer-policy",
    "permissions-policy",
    "www-authenticate",
    "server",
)

def header_map(path):
    text = Path(path).read_text(errors="replace")
    out = {}
    for line in text.splitlines():
        if ":" not in line:
            continue
        k, _, v = line.partition(":")
        key = k.strip().lower()
        if key in INTEREST:
            out[key] = v.strip()
    return out

doc = {
    "capturedAt": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    "baseUrl": os.environ["BASE_URL"],
    "http": {
        "info": int(os.environ["CODE_INFO"]),
        "health": int(os.environ["CODE_HEALTH"]),
        "authConfig": int(os.environ["CODE_AUTH"]),
        "openApiV3": int(os.environ["CODE_OA"]),
        "swaggerUiHtml": int(os.environ["CODE_SW"]),
        "swaggerUiIndex": int(os.environ["CODE_SWI"]),
    },
    "info": load(os.environ["INFO_JSON"]),
    "health": load(os.environ["HEALTH_JSON"]),
    "authConfig": load(os.environ["AUTH_JSON"]),
    "headers": {
        "info": header_map(os.environ["INFO_HDR"]),
        "root": header_map(os.environ["ROOT_HDR"]),
    },
    "honesty": "read-only preflight; no credentials used; not a pen-test result; OpenAPI paths are status probes only",
}
print(json.dumps(doc, indent=2, ensure_ascii=False))
PY
)"

echo "$JSON_DOC"
if [[ -n "$OUT" ]]; then
  mkdir -p "$(dirname "$OUT")"
  printf '%s\n' "$JSON_DOC" >"$OUT"
  echo "Wrote $OUT" >&2
fi

[[ "$code_info" == "200" ]]
