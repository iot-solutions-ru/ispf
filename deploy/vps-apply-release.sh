#!/bin/bash
# Download release artifacts from GitHub and apply on VPS.
# NOTE: Default prod deploy is direct SCP — see deploy/vps-deploy-direct.ps1 and .cursor/rules/vps-deploy.mdc.
# Use this script only for manual recovery or when explicitly requested; do NOT auto-run after gh release.
set -euo pipefail
VERSION="${1:-0.7.4}"
STAGING=/opt/ispf/staging/$VERSION
REPO=Michaael/IoT-Solutions-Platform
mkdir -p "$STAGING"
curl -sfL -o "$STAGING/ispf-server.jar" "https://github.com/$REPO/releases/download/v$VERSION/ispf-server.jar"
curl -sfL -o "$STAGING/web-console.zip" "https://github.com/$REPO/releases/download/v$VERSION/web-console.zip"
ls -lh "$STAGING"
/opt/ispf/bin/apply-platform-update.sh "$STAGING"
for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo HEALTH_OK
    break
  fi
  if [ "$i" -eq 60 ]; then
    journalctl -u ispf-server -n 40 --no-pager
    exit 1
  fi
  sleep 2
done
curl -sf http://127.0.0.1:8080/api/v1/info
echo
