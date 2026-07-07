#!/bin/bash
# Configure marketplace URL and license public key in /opt/ispf/ispf-server.env
set -euo pipefail

ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
PEM_FILE="${1:-/opt/ispf/keys/marketplace-license-public.pem}"
MARKETPLACE_URL="${ISPF_MARKETPLACE_DEFAULT_URL:-https://ispf-marketplace.iot-solutions.ru}"

if [[ ! -f "$ENV_FILE" ]]; then
  touch "$ENV_FILE"
  chmod 600 "$ENV_FILE"
fi

if [[ ! -f "$PEM_FILE" ]]; then
  echo "Missing PEM file: $PEM_FILE" >&2
  exit 1
fi

upsert() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    echo "${key}=${value}" >> "$ENV_FILE"
  fi
}

upsert ISPF_MARKETPLACE_ENABLED true
upsert ISPF_MARKETPLACE_DEFAULT_URL "$MARKETPLACE_URL"

python3 - "$ENV_FILE" "$PEM_FILE" <<'PY'
import sys

env_file, pem_file = sys.argv[1:3]
pem = open(pem_file, encoding="utf-8").read().strip()
escaped = pem.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
line = f'ISPF_LICENSE_PUBLIC_KEY_PEM="{escaped}"'
lines = open(env_file, encoding="utf-8").read().splitlines()
key = "ISPF_LICENSE_PUBLIC_KEY_PEM="
out = [ln for ln in lines if not ln.startswith(key)]
out.append(line)
open(env_file, "w", encoding="utf-8").write("\n".join(out) + "\n")
PY

chmod 600 "$ENV_FILE"
echo "=== Marketplace env configured in $ENV_FILE ==="
grep '^ISPF_MARKETPLACE_' "$ENV_FILE"
echo "ISPF_LICENSE_PUBLIC_KEY_PEM=(set, $(wc -c < "$PEM_FILE") bytes from $PEM_FILE)"
