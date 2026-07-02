#!/usr/bin/env bash
# Direct VPS deploy: build locally, SCP to staging, apply on server.
# Bash equivalent of deploy/vps-deploy-direct.ps1 — see .cursor/rules/vps-deploy.mdc
set -euo pipefail

VERSION="${1:?Usage: $0 <version> [--skip-tests] [--verify-clickhouse]}"
shift || true
SKIP_TESTS=false
VERIFY_CLICKHOUSE=false
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=true ;;
    --verify-clickhouse) VERIFY_CLICKHOUSE=true ;;
  esac
done

REMOTE_HOST="${REMOTE_HOST:-root@ispf.iot-solutions.ru}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAGING="/opt/ispf/staging/${VERSION}"
JAR="${REPO_ROOT}/packages/ispf-server/build/libs/ispf-server-${VERSION}.jar"
WEB_ZIP="${REPO_ROOT}/build/ispf-${VERSION}-web-console.zip"

echo "==> Building ispf-server ${VERSION}"
GRADLE_ARGS=( ":packages:ispf-server:bootJar" "-Pversion=${VERSION}" )
if [[ "$SKIP_TESTS" == true ]]; then
  GRADLE_ARGS+=( -x test )
fi
( cd "$REPO_ROOT" && ./gradlew "${GRADLE_ARGS[@]}" )

echo "==> Building web-console"
( cd "${REPO_ROOT}/apps/web-console" && npm ci && npm run build )
mkdir -p "${REPO_ROOT}/build"
( cd "${REPO_ROOT}/apps/web-console/dist" && tar -a -c -f "$WEB_ZIP" . )

echo "==> Upload to ${REMOTE_HOST}:${STAGING}"
ssh -o BatchMode=yes "$REMOTE_HOST" "mkdir -p '${STAGING}'"
scp -o BatchMode=yes "$JAR" "${REMOTE_HOST}:${STAGING}/ispf-server.jar"
scp -o BatchMode=yes "$WEB_ZIP" "${REMOTE_HOST}:${STAGING}/web-console.zip"

echo "==> Apply platform update"
ssh -o BatchMode=yes "$REMOTE_HOST" "bash /opt/ispf/bin/apply-platform-update.sh '${STAGING}'"

echo "==> Verify version"
curl -sf "https://ispf.iot-solutions.ru/api/v1/info" | rg "\"version\":\"${VERSION}\""

if [[ "$VERIFY_CLICKHOUSE" == true ]]; then
  ssh -o BatchMode=yes "$REMOTE_HOST" "bash /opt/ispf/vps-clickhouse-verify.sh"
fi

echo "Deploy ${VERSION} complete."
