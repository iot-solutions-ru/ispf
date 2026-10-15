#!/usr/bin/env bash
# BL Cosign lab smoke — keyful sign + verify (blob + optional local-registry image).
#
# Proves Cosign tooling in-repo without GitHub OIDC or a customer air-gap host.
# Release tags use keyless OIDC on GHCR (see .github/workflows/release.yml).
#
# Usage:
#   bash deploy/tools/air-gap-cosign-smoke.sh
#
# Env:
#   ISPF_COSIGN_SMOKE_DIR  — work dir (default build/cosign-smoke)
#   ISPF_COSIGN_SKIP_IMAGE — set to 1 to skip local-registry image sign (blob only)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/../.." && pwd)"
WORK="${ISPF_COSIGN_SMOKE_DIR:-$ROOT/build/cosign-smoke}"
SKIP_IMAGE="${ISPF_COSIGN_SKIP_IMAGE:-0}"
export COSIGN_PASSWORD="${COSIGN_PASSWORD:-lab-cosign-smoke}"
export COSIGN_YES=true

if ! command -v cosign >/dev/null 2>&1; then
  echo "cosign not on PATH — install from https://github.com/sigstore/cosign/releases" >&2
  exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK"
cd "$WORK"

echo "==> Generate lab Cosign keypair (ephemeral, not for production)"
# Non-interactive: empty passphrase via env for lab only.
COSIGN_PASSWORD="$COSIGN_PASSWORD" cosign generate-key-pair

PAYLOAD="$WORK/lab-payload.bin"
printf 'ispf-cosign-lab-smoke\n' >"$PAYLOAD"

echo "==> sign-blob / verify-blob (offline TUF upload disabled)"
SIG="$WORK/lab-payload.sig"
cosign sign-blob --yes --key cosign.key --tlog-upload=false --output-signature "$SIG" "$PAYLOAD"
cosign verify-blob --key cosign.pub --signature "$SIG" --insecure-ignore-tlog=true "$PAYLOAD"
echo "OK: blob sign+verify"

if [[ "$SKIP_IMAGE" == "1" ]]; then
  echo "OK: Cosign lab smoke (blob only; ISPF_COSIGN_SKIP_IMAGE=1)"
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "WARN: docker missing — blob gate passed; skip image sign" >&2
  echo "OK: Cosign lab smoke (blob only)"
  exit 0
fi

REG_NAME="ispf-cosign-registry-$$"
REG_PORT="${ISPF_COSIGN_REG_PORT:-5001}"
IMAGE="127.0.0.1:${REG_PORT}/ispf-cosign-smoke:lab"

cleanup() {
  docker rm -f "$REG_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Local registry on 127.0.0.1:${REG_PORT}"
docker rm -f "$REG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$REG_NAME" -p "127.0.0.1:${REG_PORT}:5000" registry:2 >/dev/null

echo "==> Push alpine stand-in as ${IMAGE}"
docker pull alpine:3.20 >/dev/null
docker tag alpine:3.20 "$IMAGE"
docker push "$IMAGE" >/dev/null

echo "==> cosign sign / verify image (keyful, no Rekor)"
cosign sign --yes --key cosign.key --tlog-upload=false "$IMAGE"
cosign verify --key cosign.pub --insecure-ignore-tlog=true "$IMAGE" >/dev/null
echo "OK: image sign+verify"

DIGEST="$(docker inspect --format='{{index .RepoDigests 0}}' "$IMAGE" 2>/dev/null || true)"
{
  echo "date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "cosign=$(cosign version 2>&1 | head -3 | tr '\n' ' ')"
  echo "blob=ok"
  echo "image=${IMAGE}"
  echo "digest=${DIGEST}"
  echo "mode=keyful-lab"
  echo "claim=tooling-lab-done; customer-host-transcript=still-gap"
} >"$WORK/cosign-verify.txt"

echo "OK: Cosign lab smoke (blob + local-registry image)"
echo "Transcript: $WORK/cosign-verify.txt"
