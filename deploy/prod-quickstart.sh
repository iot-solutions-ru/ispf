#!/usr/bin/env bash
# One-command production-like stack on a fresh Linux host with Docker (BL-127).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Building ispf-server JAR"
chmod +x gradlew
./gradlew :packages:ispf-server:bootJar -x test --no-daemon

echo "==> Building web-console"
(
  cd apps/web-console
  npm ci
  npm run build
)

echo "==> Staging artifacts"
mkdir -p deploy/staging
JAR="$(ls -1 packages/ispf-server/build/libs/ispf-server-*.jar | grep -v plain | head -1)"
cp "$JAR" deploy/staging/ispf-server.jar

# shellcheck source=air-gap-images.env
source "$ROOT/deploy/air-gap-images.env"
export ISPF_AIRGAP_POSTGRES_IMAGE ISPF_AIRGAP_REDIS_IMAGE ISPF_AIRGAP_JRE_IMAGE ISPF_AIRGAP_NGINX_IMAGE

echo "==> Starting docker compose prod stack"
docker compose -f deploy/docker-compose.prod-stack.yml up -d

chmod +x deploy/health-check.sh
deploy/health-check.sh http://127.0.0.1:8080

echo
echo "Production quick start ready (lab / localhost only — change defaults before internet-facing prod):"
echo "  API:  http://127.0.0.1:8080/api/v1/info"
echo "  UI:   http://127.0.0.1:8088/"
echo "  Stop: docker compose -f deploy/docker-compose.prod-stack.yml down"
