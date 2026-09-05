#!/usr/bin/env bash
# Read-only pen-test preflight: pin version / auth mode for G-01 kickoff.
# Usage: bash tools/security/pen-test-preflight.sh https://host [--out file.json]
set -euo pipefail

BASE="${1:-}"
OUT=""
if [[ "${2:-}" == "--out" ]]; then
  OUT="${3:?--out requires path}"
fi
if [[ -z "$BASE" || "$BASE" == "--help" || "$BASE" == "-h" ]]; then
  echo "Usage: $0 https://ispf-host [--out preflight.json]" >&2
  exit 2
fi
BASE="${BASE%/}"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

curl_code() {
  local url="$1" dest="$2"
  curl -sS -o "$dest" -w '%{http_code}' --connect-timeout 10 --max-time 30 "$url" || echo "000"
}

code_info="$(curl_code "${BASE}/api/v1/info" "$tmpdir/info.json")"
code_health="$(curl_code "${BASE}/actuator/health" "$tmpdir/health.json")"
code_auth="$(curl_code "${BASE}/api/v1/auth/config" "$tmpdir/auth.json")"

JSON_DOC="$(
  INFO_JSON="$tmpdir/info.json" HEALTH_JSON="$tmpdir/health.json" AUTH_JSON="$tmpdir/auth.json" \
  BASE_URL="$BASE" CODE_INFO="$code_info" CODE_HEALTH="$code_health" CODE_AUTH="$code_auth" \
  python3 - <<'PY'
import json, os, datetime
from pathlib import Path

def load(path):
    try:
        return json.loads(Path(path).read_text())
    except Exception:
        return None

doc = {
    "capturedAt": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    "baseUrl": os.environ["BASE_URL"],
    "http": {
        "info": int(os.environ["CODE_INFO"]),
        "health": int(os.environ["CODE_HEALTH"]),
        "authConfig": int(os.environ["CODE_AUTH"]),
    },
    "info": load(os.environ["INFO_JSON"]),
    "health": load(os.environ["HEALTH_JSON"]),
    "authConfig": load(os.environ["AUTH_JSON"]),
    "honesty": "read-only preflight; no credentials used; not a pen-test result",
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
