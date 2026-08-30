#!/usr/bin/env bash
# Enable require-signed-bundles on a demostand/VPS and smoke-test MES deploy.
#
# Creates a demostand license keypair under /opt/ispf/keys (NOT for production
# vendor keys), signs mes-platform-production, toggles env, restarts ispf-server.
#
# Usage (on VPS as root, repo checked out or bundle path given):
#   bash deploy/tools/vps-enable-signed-bundles.sh [/path/to/mes-platform-production/bundle.json]
#
# Or from laptop with sshpass (agent):
#   ISPF_SSH=root@92.63.104.121 bash deploy/tools/vps-enable-signed-bundles.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/../.." && pwd)"
BUNDLE_SRC="${1:-$ROOT/examples/mes-platform-production/bundle.json}"
KEYS_DIR="${ISPF_LICENSE_KEYS_DIR:-/opt/ispf/keys}"
ENV_FILE="${ISPF_SERVER_ENV:-/opt/ispf/ispf-server.env}"
BASE_URL="${ISPF_BASE_URL:-http://127.0.0.1:8080}"
USER="${ISPF_DEPLOY_USER:-admin}"
PASS="${ISPF_DEPLOY_PASSWORD:-admin}"

if [ ! -f "$BUNDLE_SRC" ]; then
  echo "Missing bundle: $BUNDLE_SRC" >&2
  exit 1
fi

mkdir -p "$KEYS_DIR"
chmod 700 "$KEYS_DIR"

if [ ! -f "$KEYS_DIR/license-private.pem" ] || [ ! -f "$KEYS_DIR/license-public.pem" ]; then
  echo "==> Generate demostand license keypair in $KEYS_DIR"
  python3 "$ROOT/tools/license-builder/generate-keys.py" --out-dir "$KEYS_DIR"
fi
chmod 600 "$KEYS_DIR/license-private.pem"
chmod 644 "$KEYS_DIR/license-public.pem"

echo "==> Login + installation-id"
TOKEN=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")
INSTALL_ID=$(curl -fsS "$BASE_URL/api/v1/platform/installation-id" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['installationId'])")
echo "installationId=$INSTALL_ID"

SIGNED_OUT="/tmp/mes-platform-production-signed.json"
echo "==> Sign bundle → $SIGNED_OUT"
# 0.9.186 hashes BundleManifest DTO — use jar helper. After deploy JsonNode fix ships, prefer sign-bundle.py.
if [ -f "${ISPF_SERVER_JAR:-/opt/ispf/ispf-server.jar}" ]; then
  bash "$ROOT/tools/license-builder/sign-bundle-via-jar.sh" \
    --bundle "$BUNDLE_SRC" \
    --bundle-id mes-platform-production \
    --installation-id "$INSTALL_ID" \
    --private-key "$KEYS_DIR/license-private.pem" \
    --min-platform-version 0.9.0 \
    --out "$SIGNED_OUT"
else
  python3 "$ROOT/tools/license-builder/sign-bundle.py" \
    --bundle "$BUNDLE_SRC" \
    --bundle-id mes-platform-production \
    --installation-id "$INSTALL_ID" \
    --private-key "$KEYS_DIR/license-private.pem" \
    --min-platform-version 0.9.0 \
    --out "$SIGNED_OUT"
fi

echo "==> Patch $ENV_FILE (public key + signing private + require signed)"
python3 - "$ENV_FILE" "$KEYS_DIR/license-public.pem" "$KEYS_DIR/license-private.pem" <<'PY'
import re
import sys
from pathlib import Path

env_path, pub_path, priv_path = map(Path, sys.argv[1:4])
def oneline_pem(pem: str) -> str:
    # systemd EnvironmentFile does not unescape \n inside quotes; keep a single
    # line with spaces so Java Base64 decode (whitespace-stripped) still works.
    lines = [ln.strip() for ln in pem.strip().splitlines() if ln.strip()]
    if len(lines) < 3:
        raise SystemExit("invalid PEM (need header/body/footer)")
    return f"{lines[0]} {''.join(lines[1:-1])} {lines[-1]}"

pub = oneline_pem(pub_path.read_text())
priv = oneline_pem(priv_path.read_text())
text = env_path.read_text() if env_path.exists() else ""

def upsert(text: str, key: str, value: str) -> str:
    line = f'{key}="{value}"'
    pat = re.compile(rf'^{re.escape(key)}=.*$', re.M)
    if pat.search(text):
        return pat.sub(lambda _m: line, text)
    if text and not text.endswith("\n"):
        text += "\n"
    return text + line + "\n"

text = upsert(text, "ISPF_LICENSE_PUBLIC_KEY_PEM", pub)
text = upsert(text, "ISPF_LICENSE_SIGNING_PRIVATE_KEY_PEM", priv)
text = upsert(text, "ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES", "true")
env_path.write_text(text)
print(f"Updated {env_path}")
PY

echo "==> Restart ispf-server"
systemctl restart ispf-server
# wait health
for i in $(seq 1 60); do
  if curl -fsS "$BASE_URL/actuator/health" >/dev/null 2>&1; then
    echo "health OK ($i)"
    break
  fi
  sleep 2
done

TOKEN=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

echo "==> Expect 403 on unsigned deploy"
UNSIGNED_HTTP=$(curl -sS -w '%{http_code}' -o /tmp/mes-unsigned.json -X POST \
  "$BASE_URL/api/v1/applications/mes-platform-production/deploy" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @"$BUNDLE_SRC")
echo "unsigned HTTP $UNSIGNED_HTTP"
if [ "$UNSIGNED_HTTP" != "403" ]; then
  echo "WARN: expected 403 for unsigned; got $UNSIGNED_HTTP" >&2
  cat /tmp/mes-unsigned.json >&2 || true
fi

echo "==> Signed deploy"
SIGNED_HTTP=$(curl -sS -w '%{http_code}' -o /tmp/mes-signed-deploy.json -X POST \
  "$BASE_URL/api/v1/applications/mes-platform-production/deploy" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @"$SIGNED_OUT")
echo "signed HTTP $SIGNED_HTTP"
python3 -m json.tool /tmp/mes-signed-deploy.json | head -15
if [ "$SIGNED_HTTP" != "200" ]; then
  echo "Signed deploy failed" >&2
  exit 1
fi

echo
echo "OK: ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true"
echo "Keys: $KEYS_DIR (private 0600 — do not commit / do not share in chat)"
echo "Signed artifact: $SIGNED_OUT"
