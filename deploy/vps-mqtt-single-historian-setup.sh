#!/usr/bin/env bash
# Full DB reset + one MQTT device with variable historian (prod lab).
#
#   bash deploy/vps-mqtt-single-historian-setup.sh
#   bash deploy/vps-mqtt-single-historian-setup.sh --ingress-gateway
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOADTEST_DIR="${LOADTEST_DIR:-/opt/ispf/loadtest}"
FACTORY_RESET="${ISPF_FACTORY_RESET:-/opt/ispf/bin/vps-factory-reset.sh}"
if [ ! -x "$FACTORY_RESET" ]; then
  FACTORY_RESET="$SCRIPT_DIR/vps-factory-reset.sh"
fi
INGRESS=false

for arg in "$@"; do
  case "$arg" in
    --ingress-gateway) INGRESS=true ;;
    -h|--help)
      sed -n '2,8p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

echo "=== Factory reset PostgreSQL + ClickHouse (fixtures disabled) ==="
bash "$FACTORY_RESET" --no-fixtures

echo "=== Ensure loadtest scripts ==="
mkdir -p "$LOADTEST_DIR"
for f in setup-single-mqtt-historian.py mqtt_loadtest_lib.py loadtest_cleanup_lib.py; do
  if [ -f "$SCRIPT_DIR/$f" ]; then
    cp "$SCRIPT_DIR/$f" "$LOADTEST_DIR/"
  fi
done

echo "=== Single MQTT device ==="
python3 "$LOADTEST_DIR/setup-single-mqtt-historian.py" --base-url http://127.0.0.1:8080 --skip-purge

echo ""
echo "=== Done ==="
echo "Publish test message:"
echo "  docker run --rm --network host eclipse-mosquitto:2 mosquitto_pub -h 127.0.0.1 -t ispf/mqtt-device-01/temperature -m 23.5"
echo "Check history (admin token required from UI or API)."
