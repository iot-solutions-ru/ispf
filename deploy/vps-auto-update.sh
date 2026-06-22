#!/bin/bash
# Poll GitHub Releases and apply newer ISPF builds via vps-apply-release.sh (VPS cron).
set -euo pipefail

REPO="${ISPF_UPDATE_GITHUB_OWNER:-Michaael}/${ISPF_UPDATE_GITHUB_REPO:-IoT-Solutions-Platform}"
INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
VERSION_FILE="${ISPF_RELEASE_VERSION_FILE:-$INSTALL_ROOT/current-release.txt}"
APPLY_SCRIPT="$INSTALL_ROOT/bin/vps-apply-release.sh"
LOG_FILE="${ISPF_AUTO_UPDATE_LOG:-$INSTALL_ROOT/data/auto-update.log}"
DRY_RUN="${ISPF_AUTO_UPDATE_DRY_RUN:-0}"

mkdir -p "$(dirname "$LOG_FILE")" "$INSTALL_ROOT/data"

log() {
  echo "$(date -Iseconds) $*" | tee -a "$LOG_FILE"
}

normalize_version() {
  local v="${1:-}"
  v="${v#v}"
  v="${v#V}"
  echo "$v"
}

read_current_version() {
  if [ -f "$VERSION_FILE" ]; then
    normalize_version "$(tr -d '[:space:]' < "$VERSION_FILE")"
    return
  fi
  echo "0.0.0"
}

fetch_latest_tag() {
  curl -sf "https://api.github.com/repos/$REPO/releases/latest" \
    | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
    | head -1
}

version_lt() {
  local left right
  left="$(normalize_version "$1")"
  right="$(normalize_version "$2")"
  [ "$left" != "$right" ] && [ "$(printf '%s\n%s\n' "$left" "$right" | sort -V | tail -1)" = "$right" ]
}

CURRENT="$(read_current_version)"
LATEST_TAG="$(fetch_latest_tag || true)"
if [ -z "${LATEST_TAG:-}" ]; then
  log "GitHub release check failed (repo=$REPO)"
  exit 0
fi
LATEST="$(normalize_version "$LATEST_TAG")"
log "check current=$CURRENT latest=$LATEST (repo=$REPO)"

if ! version_lt "$CURRENT" "$LATEST"; then
  log "up to date"
  exit 0
fi

if [ "$DRY_RUN" = "1" ]; then
  log "dry-run: would apply $LATEST"
  exit 0
fi

if [ ! -x "$APPLY_SCRIPT" ]; then
  log "error: missing executable $APPLY_SCRIPT"
  exit 1
fi

log "applying release $LATEST"
bash "$APPLY_SCRIPT" "$LATEST"
echo "$LATEST" > "$VERSION_FILE"
log "apply finished $LATEST"
