#!/usr/bin/env bash
# Build offline deployment bundle on an internet-connected build host (BL-128).
#
# Usage:
#   bash deploy/air-gap-pack.sh [--version 0.9.32] [--skip-build] [--skip-driver-packs] [--with-clickhouse]
#
# Output: build/air-gap/ispf-airgap-<version>.tar.gz
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=air-gap-images.env
source "$ROOT/deploy/air-gap-images.env"

VERSION="dev"
SKIP_BUILD=false
SKIP_DRIVER_PACKS=false
WITH_CLICKHOUSE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    --skip-driver-packs) SKIP_DRIVER_PACKS=true; shift ;;
    --with-clickhouse) WITH_CLICKHOUSE=true; shift ;;
    -h|--help)
      sed -n '2,8p' "$0"
      exit 0
      ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

BUNDLE_DIR="build/air-gap/ispf-airgap-${VERSION}"
ARCHIVE="build/air-gap/ispf-airgap-${VERSION}.tar.gz"

IMAGES=(
  "$ISPF_AIRGAP_POSTGRES_IMAGE"
  "$ISPF_AIRGAP_REDIS_IMAGE"
  "$ISPF_AIRGAP_JRE_IMAGE"
  "$ISPF_AIRGAP_NGINX_IMAGE"
)
if [[ "$WITH_CLICKHOUSE" == true ]]; then
  IMAGES+=("$ISPF_AIRGAP_CLICKHOUSE_IMAGE")
fi

echo "==> Preparing bundle directory: $BUNDLE_DIR"
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/artifacts/drivers" "$BUNDLE_DIR/images" "$BUNDLE_DIR/deploy"

if [[ "$SKIP_BUILD" == false ]]; then
  echo "==> Building ispf-server JAR"
  chmod +x gradlew
  ./gradlew :packages:ispf-server:bootJar -x test --no-daemon "-Pversion=${VERSION}"

  echo "==> Building web-console"
  (
    cd apps/web-console
    npm ci
    npm run build
  )

  if [[ "$SKIP_DRIVER_PACKS" == false ]]; then
    echo "==> Syncing driver packs"
    ./gradlew syncAllDriverPacks --no-daemon
  fi
fi

JAR="$(ls -1 packages/ispf-server/build/libs/ispf-server-*.jar | grep -v plain | head -1)"
if [[ ! -f "$JAR" ]]; then
  echo "Missing server JAR; run without --skip-build" >&2
  exit 1
fi
cp "$JAR" "$BUNDLE_DIR/artifacts/ispf-server.jar"

if [[ ! -d apps/web-console/dist ]]; then
  echo "Missing apps/web-console/dist; run without --skip-build" >&2
  exit 1
fi
rm -rf "$BUNDLE_DIR/artifacts/web-console"
cp -a apps/web-console/dist "$BUNDLE_DIR/artifacts/web-console"

(
  cd apps/web-console/dist
  zip -qr "$ROOT/$BUNDLE_DIR/artifacts/web-console.zip" .
)

if [[ "$SKIP_DRIVER_PACKS" == false ]]; then
  if [[ -d build/driver-packs ]]; then
    echo "==> Copying driver packs"
    cp -a build/driver-packs/. "$BUNDLE_DIR/artifacts/drivers/"
    tar -C "$BUNDLE_DIR/artifacts/drivers" -czf "$BUNDLE_DIR/artifacts/driver-packs.tar.gz" .
  else
    echo "WARNING: build/driver-packs missing; bundle will have empty drivers dir" >&2
  fi
fi

echo "==> Copying deploy helpers"
cp deploy/docker-compose.air-gap.yml "$BUNDLE_DIR/deploy/"
cp deploy/nginx-local-prod.conf "$BUNDLE_DIR/deploy/"
cp deploy/health-check.sh "$BUNDLE_DIR/deploy/"
cp deploy/air-gap-apply.sh "$BUNDLE_DIR/deploy/"
cp deploy/air-gap-images.env "$BUNDLE_DIR/deploy/"

echo "==> Pulling and exporting Docker images"
for image in "${IMAGES[@]}"; do
  docker pull "$image"
done
docker save "${IMAGES[@]}" -o "$BUNDLE_DIR/images/prod-stack.tar"

DIGEST_LINES=""
for image in "${IMAGES[@]}"; do
  digest="$(docker image inspect --format='{{index .RepoDigests 0}}' "$image" 2>/dev/null || true)"
  if [[ -n "$digest" ]]; then
    DIGEST_LINES="${DIGEST_LINES}    \"${digest}\",\n"
  fi
done
DIGEST_LINES="$(printf '%b' "$DIGEST_LINES" | sed '$ s/,$//')"

CLICKHOUSE_JSON="false"
if [[ "$WITH_CLICKHOUSE" == true ]]; then
  CLICKHOUSE_JSON="true"
fi

cat >"$BUNDLE_DIR/MANIFEST.json" <<EOF
{
  "kind": "ispf-airgap-bundle",
  "version": "${VERSION}",
  "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "images": [
$(printf '    "%s",\n' "${IMAGES[@]}" | sed '$ s/,$//')
  ],
  "imageDigests": [
${DIGEST_LINES}
  ],
  "clickhouseIncluded": ${CLICKHOUSE_JSON},
  "artifacts": {
    "serverJar": "artifacts/ispf-server.jar",
    "webConsoleZip": "artifacts/web-console.zip",
    "driverPacksTar": "artifacts/driver-packs.tar.gz"
  },
  "licensing": {
    "notes": "Set ISPF_LICENSE_PUBLIC_KEY_PEM, ISPF_LICENSE_ENFORCE, ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES on target host before apply.",
    "signTool": "tools/license-builder/sign-bundle.py",
    "docs": "docs/en/air-gap-deployment.md"
  }
}
EOF

(
  cd "$BUNDLE_DIR"
  find . -type f ! -name 'CHECKSUMS.sha256' -print0 | sort -z | xargs -0 sha256sum > CHECKSUMS.sha256
)

echo "==> Creating archive $ARCHIVE"
mkdir -p build/air-gap
tar -C build/air-gap -czf "$ARCHIVE" "ispf-airgap-${VERSION}"

echo
echo "Air-gap bundle ready:"
echo "  $ARCHIVE"
echo "  $(du -h "$ARCHIVE" | cut -f1)"
echo
echo "Transfer to isolated host, then:"
echo "  bash deploy/air-gap-apply.sh /path/to/ispf-airgap-${VERSION}.tar.gz"
