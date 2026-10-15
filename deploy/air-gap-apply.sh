#!/usr/bin/env bash
# Apply ISPF air-gap bundle on a host without internet (BL-128).
#
# Usage:
#   bash deploy/air-gap-apply.sh /path/to/ispf-airgap-<version>.tar.gz
#   bash deploy/air-gap-apply.sh /path/to/extracted/ispf-airgap-<version>/
#
# Commercial licensing (compose defaults enforce + require-signed = true):
#   ISPF_LICENSE_PUBLIC_KEY_PEM   — PEM text or path content for RSA app-bundle verify
#   ISPF_LICENSE_ENFORCE=true|false
#   ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true|false  (lab override: false)
# Cosign (optional, when signatures ship with the bundle or images are on a registry):
#   ISPF_COSIGN_VERIFY=true       — fail if cosign verify fails (requires cosign on PATH)
#   ISPF_COSIGN_PUBLIC_KEY        — path to cosign.pub for keyful verify (air-gap / offline)
#   ISPF_COSIGN_IMAGE             — image ref to verify (default from air-gap-images.env if set)
# CHECKSUMS.sha256 remains the mandatory corruption gate.
set -euo pipefail

INPUT="${1:-}"
if [[ -z "$INPUT" ]]; then
  echo "Usage: $0 /path/to/ispf-airgap-<version>.tar.gz|/" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required on the target host" >&2
  exit 1
fi

WORK=""
BUNDLE_ROOT=""
CLEANUP_WORK=false

if [[ -d "$INPUT" && -f "$INPUT/MANIFEST.json" ]]; then
  BUNDLE_ROOT="$(cd "$INPUT" && pwd)"
elif [[ -f "$INPUT" ]]; then
  WORK="$(mktemp -d)"
  CLEANUP_WORK=true
  echo "==> Extracting bundle"
  tar -xzf "$INPUT" -C "$WORK" --no-same-owner --no-overwrite-dir
  BUNDLE_ROOT="$(find "$WORK" -mindepth 1 -maxdepth 1 -type d | head -1)"
else
  echo "Bundle not found: $INPUT" >&2
  exit 1
fi

cleanup() {
  if [[ "$CLEANUP_WORK" == true && -n "$WORK" ]]; then
    rm -rf "$WORK"
  fi
}
trap cleanup EXIT

cd "$BUNDLE_ROOT"

if [[ ! -f MANIFEST.json ]]; then
  echo "Missing MANIFEST.json" >&2
  exit 1
fi

if ! grep -q '"kind"[[:space:]]*:[[:space:]]*"ispf-airgap-bundle"' MANIFEST.json; then
  echo "Invalid bundle: MANIFEST.json kind must be ispf-airgap-bundle" >&2
  exit 1
fi

# Checksums are mandatory and verified before anything from the bundle is
# sourced, loaded into Docker or executed.
if [[ ! -f CHECKSUMS.sha256 ]]; then
  echo "Missing CHECKSUMS.sha256; refusing to apply unverified bundle content" >&2
  exit 1
fi

echo "==> Verifying checksums"
sha256sum -c CHECKSUMS.sha256

if [[ ! -f images/prod-stack.tar ]]; then
  echo "Missing images/prod-stack.tar in bundle" >&2
  exit 1
fi

if [[ -f deploy/air-gap-images.env ]]; then
  # shellcheck source=/dev/null
  source deploy/air-gap-images.env
fi

COSIGN_VERIFY="${ISPF_COSIGN_VERIFY:-false}"
if [[ "$COSIGN_VERIFY" == "true" ]]; then
  if ! command -v cosign >/dev/null 2>&1; then
    echo "ISPF_COSIGN_VERIFY=true but cosign is not on PATH" >&2
    exit 1
  fi
  COSIGN_IMAGE="${ISPF_COSIGN_IMAGE:-${ISPF_SERVER_IMAGE:-}}"
  COSIGN_KEY="${ISPF_COSIGN_PUBLIC_KEY:-}"
  if [[ -z "$COSIGN_IMAGE" ]]; then
    echo "ISPF_COSIGN_VERIFY=true requires ISPF_COSIGN_IMAGE (or ISPF_SERVER_IMAGE in air-gap-images.env)" >&2
    exit 1
  fi
  echo "==> Cosign verify ${COSIGN_IMAGE}"
  if [[ -n "$COSIGN_KEY" ]]; then
    cosign verify --key "$COSIGN_KEY" --insecure-ignore-tlog=true "$COSIGN_IMAGE"
  else
    echo "WARN: no ISPF_COSIGN_PUBLIC_KEY — attempting keyless verify (needs network to Rekor/Fulcio)" >&2
    cosign verify "$COSIGN_IMAGE"
  fi
fi

echo "==> Loading Docker images (offline)"
docker load -i images/prod-stack.tar

COMPOSE_FILE="deploy/docker-compose.air-gap.yml"
if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Missing $COMPOSE_FILE" >&2
  exit 1
fi

mkdir -p artifacts/drivers
if [[ -f artifacts/driver-packs.tar.gz ]]; then
  rm -rf artifacts/drivers/*
  tar -xzf artifacts/driver-packs.tar.gz -C artifacts/drivers --no-same-owner --no-overwrite-dir
fi

chmod +x deploy/health-check.sh

COMPOSE_PROFILES=()
if grep -q '"clickhouseIncluded"[[:space:]]*:[[:space:]]*true' MANIFEST.json; then
  COMPOSE_PROFILES=(--profile clickhouse)
fi

REQUIRE_SIGNED="${ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES:-true}"
LICENSE_ENFORCE="${ISPF_LICENSE_ENFORCE:-true}"
PUBLIC_KEY_PEM="${ISPF_LICENSE_PUBLIC_KEY_PEM:-}"

if [[ "$REQUIRE_SIGNED" == "true" || "$LICENSE_ENFORCE" == "true" ]]; then
  if [[ -z "$PUBLIC_KEY_PEM" ]]; then
    echo "WARN: ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES/ENFORCE default on, but ISPF_LICENSE_PUBLIC_KEY_PEM is empty." >&2
    echo "      Export the vendor public PEM before compose up, or set REQUIRE_SIGNED=false for lab-only." >&2
    echo "      Docs: docs/en/air-gap-deployment.md § Commercial licensing / docs/evidence/air-gap-signing/" >&2
  else
    echo "==> License gate: enforce=$LICENSE_ENFORCE require-signed=$REQUIRE_SIGNED (public key set)"
  fi
else
  echo "==> License gate: enforce=$LICENSE_ENFORCE require-signed=$REQUIRE_SIGNED (lab override)"
fi

echo "==> Starting stack"
docker compose -f "$COMPOSE_FILE" "${COMPOSE_PROFILES[@]}" up -d

deploy/health-check.sh http://127.0.0.1:8080

echo
echo "Air-gap deployment ready:"
echo "  API:  http://127.0.0.1:8080/api/v1/info"
echo "  UI:   http://127.0.0.1:8088/"
echo "  Stop: docker compose -f $COMPOSE_FILE down"
echo "  App bundles: RSA require-signed default ON."
echo "  Cosign: set ISPF_COSIGN_VERIFY=true (+ ISPF_COSIGN_PUBLIC_KEY for offline) to fail-closed on image verify."
echo
echo "Updates without internet: transfer a newer ispf-airgap-*.tar.gz and re-run this script"
echo "(docker compose down first; PostgreSQL volume ispf_airgap_pg retains data unless -v)."
