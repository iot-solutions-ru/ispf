#!/usr/bin/env bash
# ISPF disk guardian — monitor / usage, auto-clean safe caches, log warnings.
# Install: copy to /opt/ispf/bin/disk-guardian.sh and add deploy/cron/ispf-disk-guardian to /etc/cron.d/
set -euo pipefail

LOG="${ISPF_DISK_GUARDIAN_LOG:-/opt/ispf/data/disk-guardian.log}"
WARN_PCT="${ISPF_DISK_WARN_PCT:-80}"
CRIT_PCT="${ISPF_DISK_CRIT_PCT:-90}"
AUTO_CLEAN_PCT="${ISPF_DISK_AUTO_CLEAN_PCT:-85}"

log() { echo "$(date -Is) $*" | tee -a "$LOG"; }

USE_PCT=$(df / --output=pcent | tail -1 | tr -dc '0-9')
AVAIL_KB=$(df / --output=avail | tail -1 | tr -dc '0-9')
AVAIL_GB=$((AVAIL_KB / 1024 / 1024))

log "disk use=${USE_PCT}% avail=${AVAIL_GB}G"

auto_clean() {
  log "AUTO-CLEAN triggered at ${USE_PCT}%"
  journalctl --vacuum-size=200M >/dev/null 2>&1 || true
  apt-get clean >/dev/null 2>&1 || true
  docker image prune -af >/dev/null 2>&1 || true

  ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
  CHPASS=$(grep '^ISPF_VARIABLE_HISTORY_CLICKHOUSE_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || true)
  if [ -n "${CHPASS:-}" ] && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx ispf-clickhouse; then
    for t in text_log processors_profile_log query_log part_log trace_log metric_log asynchronous_metric_log; do
      docker exec ispf-clickhouse clickhouse-client --password="$CHPASS" \
        -q "TRUNCATE TABLE IF EXISTS system.$t" >/dev/null 2>&1 || true
    done
    log "ClickHouse system logs truncated"
  fi

  USE_PCT=$(df / --output=pcent | tail -1 | tr -dc '0-9')
  AVAIL_KB=$(df / --output=avail | tail -1 | tr -dc '0-9')
  AVAIL_GB=$((AVAIL_KB / 1024 / 1024))
  log "after clean: use=${USE_PCT}% avail=${AVAIL_GB}G"
}

if [ "$USE_PCT" -ge "$AUTO_CLEAN_PCT" ]; then
  auto_clean
fi

if [ "$USE_PCT" -ge "$CRIT_PCT" ]; then
  log "CRITICAL: disk ${USE_PCT}% on $(hostname)"
elif [ "$USE_PCT" -ge "$WARN_PCT" ]; then
  log "WARNING: disk ${USE_PCT}% on $(hostname)"
fi

if [ "$USE_PCT" -ge "$WARN_PCT" ]; then
  {
    echo "--- top dirs ---"
    du -xh /var/lib/docker/volumes --max-depth=1 2>/dev/null | sort -hr | head -8 || true
    du -xh /var/log --max-depth=1 2>/dev/null | sort -hr | head -8 || true
  } >> "$LOG" 2>&1 || true
fi

if [ -f "$LOG" ] && [ "$(stat -c%s "$LOG" 2>/dev/null || echo 0)" -gt 5242880 ]; then
  tail -n 2000 "$LOG" > "${LOG}.tmp" && mv "${LOG}.tmp" "$LOG"
fi
