#!/usr/bin/env bash
# Start Mosquitto for MQTT ingress load tests on VPS (localhost only).
set -eu

CONF_DIR="${CONF_DIR:-/opt/ispf/mqtt}"
CONTAINER_NAME="${CONTAINER_NAME:-ispf-mqtt-loadtest}"
IMAGE="${IMAGE:-eclipse-mosquitto:2}"
LOADTEST_DIR="${LOADTEST_DIR:-/opt/ispf/loadtest}"

mkdir -p "$CONF_DIR" "$LOADTEST_DIR"

cat >"$CONF_DIR/mosquitto.conf" <<'EOF'
listener 1883
allow_anonymous true
persistence false
log_type error
EOF

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  docker rm -f "$CONTAINER_NAME" >/dev/null
fi

docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  -p 127.0.0.1:1883:1883 \
  -v "$CONF_DIR/mosquitto.conf:/mosquitto/config/mosquitto.conf:ro" \
  "$IMAGE"

echo "Mosquitto running: 127.0.0.1:1883 (container $CONTAINER_NAME)"
docker ps --filter "name=$CONTAINER_NAME" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
for f in mqtt-loadtest-publisher.py mqtt-loadtest-tap.py mqtt_loadtest_lib.py; do
  if [ -f "$SCRIPT_DIR/$f" ]; then
    cp "$SCRIPT_DIR/$f" "$LOADTEST_DIR/"
  fi
done

echo "ISPF mqtt driver brokerUrl: tcp://127.0.0.1:1883"

if command -v pip3 >/dev/null 2>&1; then
  pip3 install --quiet paho-mqtt || true
elif command -v python3 >/dev/null 2>&1; then
  python3 -m pip install --quiet paho-mqtt || true
fi

VENV_DIR="$LOADTEST_DIR/venv"
if [ ! -x "$VENV_DIR/bin/python" ]; then
  python3 -m venv "$VENV_DIR"
  "$VENV_DIR/bin/pip" install --quiet paho-mqtt
fi
echo "Publisher venv: $VENV_DIR/bin/python"
