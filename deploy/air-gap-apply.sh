#!/usr/bin/env bash
# Apply ISPF air-gap bundle on a host without internet (BL-128).
#
# Usage:
#   bash deploy/air-gap-apply.sh /path/to/ispf-airgap-<version>.tar.gz
#   bash deploy/air-gap-apply.sh /path/to/extracted/ispf-airgap-<version>/
#
# Optional env (commercial licensing):
#   ISPF_LICENSE_PUBLIC_KEY_PEM
#   ISPF_LICENSE_ENFORCE=true
#   ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true
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
  tar -xzf "$INPUT" -C "$WORK"
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

if [[ -f CHECKSUMS.sha256 ]]; then
  echo "==> Verifying checksums"
  sha256sum -c CHECKSUMS.sha256
fi

if [[ ! -f images/prod-stack.tar ]]; then
  echo "Missing images/prod-stack.tar in bundle" >&2
  exit 1
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
  tar -xzf artifacts/driver-packs.tar.gz -C artifacts/drivers
fi

chmod +x deploy/health-check.sh

echo "==> Starting stack"
docker compose -f "$COMPOSE_FILE" up -d

deploy/health-check.sh http://localhost:8080

echo
echo "Air-gap deployment ready:"
echo "  API:  http://localhost:8080/api/v1/info"
echo "  UI:   http://localhost:8088/"
echo "  Stop: docker compose -f $COMPOSE_FILE down"
echo
echo "Updates without internet: transfer a newer ispf-airgap-*.tar.gz and re-run this script"
echo "(docker compose down first; PostgreSQL volume ispf_airgap_pg retains data unless -v)."
