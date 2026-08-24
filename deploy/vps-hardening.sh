#!/usr/bin/env bash
# Apply VPS hardening from deploy/: disk guardian, ClickHouse log TTL, journald limits.
# Run on server as root after copying /opt/ispf from release.
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
DEPLOY="${ISPF_DEPLOY_DIR:-${INSTALL_ROOT}/deploy}"

if [ ! -d "$DEPLOY" ]; then
  DEPLOY="$(cd "$(dirname "$0")/.." && pwd)"
fi

echo "==> Install disk-guardian"
install -d "$INSTALL_ROOT/bin" "$INSTALL_ROOT/data"
install -m 0755 "$DEPLOY/bin/disk-guardian.sh" "$INSTALL_ROOT/bin/disk-guardian.sh"
install -m 0644 "$DEPLOY/cron/ispf-disk-guardian" /etc/cron.d/ispf-disk-guardian

echo "==> Journald limit (300M)"
mkdir -p /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/ispf-limits.conf << 'EOF'
[Journal]
SystemMaxUse=300M
SystemMaxFileSize=50M
MaxRetentionSec=7day
Compress=yes
EOF
systemctl restart systemd-journald || true
journalctl --vacuum-size=200M || true

echo "==> Docker log rotation"
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  cat > /etc/docker/daemon.json << 'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "50m",
    "max-file": "3"
  }
}
EOF
  echo "    Created /etc/docker/daemon.json (restart docker manually if needed)"
fi

echo "==> ClickHouse compose + config"
install -d "$INSTALL_ROOT/clickhouse-config"
install -m 0644 "$DEPLOY/clickhouse-config/system-logs-production.xml" \
  "$INSTALL_ROOT/clickhouse-config/system-logs-production.xml"
install -m 0644 "$DEPLOY/clickhouse-local-user.xml" "$INSTALL_ROOT/clickhouse-local-user.xml" 2>/dev/null || true
install -m 0644 "$DEPLOY/docker-compose.clickhouse.yml" "$INSTALL_ROOT/docker-compose.clickhouse.yml"

echo "==> Run disk-guardian once"
"$INSTALL_ROOT/bin/disk-guardian.sh" | tail -3

echo "Done. Recreate ClickHouse if needed:"
echo "  docker-compose -p ispf-ch -f $INSTALL_ROOT/docker-compose.clickhouse.yml up -d --force-recreate"
