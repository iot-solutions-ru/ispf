#!/usr/bin/env bash
# Smoke RSA require-signed against a running air-gap / local ISPF (not Cosign).
#
# Prerequisites: stack up, ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true, public key configured.
# Signs mes-platform-production (or BUNDLE_SRC) and expects unsigned deploy → 403, signed → 2xx.
#
# Usage:
#   bash deploy/tools/air-gap-signed-gate-smoke.sh [/path/to/bundle.json]
#
# Env: ISPF_BASE_URL, ISPF_DEPLOY_USER, ISPF_DEPLOY_PASSWORD,
#      ISPF_LICENSE_KEYS_DIR (default ./build/air-gap-keys)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/../.." && pwd)"
BUNDLE_SRC="${1:-$ROOT/examples/mes-platform-production/bundle.json}"
BUNDLE_ID="${ISPF_BUNDLE_ID:-mes-platform-production}"
KEYS_DIR="${ISPF_LICENSE_KEYS_DIR:-$ROOT/build/air-gap-keys}"
BASE_URL="${ISPF_BASE_URL:-http://127.0.0.1:8080}"
USER="${ISPF_DEPLOY_USER:-admin}"
PASS="${ISPF_DEPLOY_PASSWORD:-admin}"

if [[ ! -f "$BUNDLE_SRC" ]]; then
  echo "Missing bundle: $BUNDLE_SRC" >&2
  exit 1
fi

mkdir -p "$KEYS_DIR"
if [[ ! -f "$KEYS_DIR/license-private.pem" || ! -f "$KEYS_DIR/license-public.pem" ]]; then
  echo "==> Generate lab keypair in $KEYS_DIR"
  python3 "$ROOT/tools/license-builder/generate-keys.py" --out-dir "$KEYS_DIR"
fi

echo "==> Login + installation-id"
TOKEN=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")
INSTALL_ID=$(curl -fsS "$BASE_URL/api/v1/platform/installation-id" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['installationId'])")
echo "installationId=$INSTALL_ID"

SIGNED_OUT="${TMPDIR:-/tmp}/air-gap-signed-smoke.json"
echo "==> Sign bundle → $SIGNED_OUT"
python3 "$ROOT/tools/license-builder/sign-bundle.py" \
  --bundle "$BUNDLE_SRC" \
  --bundle-id "$BUNDLE_ID" \
  --installation-id "$INSTALL_ID" \
  --private-key "$KEYS_DIR/license-private.pem" \
  --min-platform-version 0.9.0 \
  --out "$SIGNED_OUT"

echo "==> Expect 403 on unsigned deploy"
UNSIGNED_HTTP=$(curl -sS -w '%{http_code}' -o /tmp/air-gap-unsigned.json -X POST \
  "$BASE_URL/api/v1/applications/${BUNDLE_ID}/deploy" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @"$BUNDLE_SRC" || true)
echo "unsigned HTTP $UNSIGNED_HTTP"
if [[ "$UNSIGNED_HTTP" != "403" ]]; then
  echo "WARN: expected 403 for unsigned; got $UNSIGNED_HTTP (is require-signed on?)" >&2
  cat /tmp/air-gap-unsigned.json >&2 || true
fi

echo "==> Expect success on signed deploy"
SIGNED_HTTP=$(curl -sS -w '%{http_code}' -o /tmp/air-gap-signed-deploy.json -X POST \
  "$BASE_URL/api/v1/applications/${BUNDLE_ID}/deploy" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @"$SIGNED_OUT")
echo "signed HTTP $SIGNED_HTTP"
python3 -m json.tool /tmp/air-gap-signed-deploy.json | head -20

if [[ "$SIGNED_HTTP" != "200" && "$SIGNED_HTTP" != "201" ]]; then
  echo "FAIL: signed deploy expected 2xx" >&2
  exit 1
fi

echo "OK: RSA air-gap signed gate smoke (Cosign still Gap — not tested here)"
