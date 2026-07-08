#!/usr/bin/env bash
# Bearer login for ISPF deploy/maintenance scripts (replaces X-ISPF-Role).
ispf_auth_token() {
  local api="${API:-http://127.0.0.1:8080}"
  local user="${ISPF_USER:-admin}"
  local pass="${ISPF_PASS:-admin}"
  curl -sf -X POST "${api}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${user}\",\"password\":\"${pass}\"}" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("token") or "")' 2>/dev/null
}

ispf_auth_header() {
  local token
  token="$(ispf_auth_token)"
  if [ -z "$token" ]; then
    echo "ispf-auth: login failed for ${ISPF_USER:-admin} at ${API:-http://127.0.0.1:8080}" >&2
    return 1
  fi
  printf 'Authorization: Bearer %s' "$token"
}
