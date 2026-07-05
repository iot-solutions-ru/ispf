#!/usr/bin/env bash
# One-command multi-replica cluster stack (BL-134).
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

echo "==> Starting docker compose cluster stack (3 replicas)"
docker compose -f deploy/docker-compose.cluster.yml up -d

echo "==> Waiting for replicas"
sleep 15

echo "==> Round-robin check via nginx"
bash deploy/cluster-smoke-test.sh

echo
echo "Cluster quick start ready (lab / localhost only):"
echo "  UI:         http://127.0.0.1:8088/"
echo "  API (LB):   http://127.0.0.1:8088/api/v1/info"
echo "  Replica 1:  http://127.0.0.1:8080 (direct — map port in compose if needed)"
echo "  Stop:       docker compose -f deploy/docker-compose.cluster.yml down"
