#!/bin/bash
# Enable fixtures (Meters + mqtt-meter-bus models), restart ISPF, start MQTT meter bus driver.
set -euo pipefail
API="${API:-http://127.0.0.1:8080}"
DEVICE_PATH="${DEVICE_PATH:-root.platform.devices.mqtt-meter-bus}"
ENV_FILE="${ENV_FILE:-/opt/ispf/ispf-server.env}"

upsert_env() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
    sed -i "s/^${key}=.*/${key}=${value}/" "$ENV_FILE"
  else
    echo "${key}=${value}" >> "$ENV_FILE"
  fi
}

echo "==> Enabling bootstrap fixtures for meter bus models"
upsert_env ISPF_BOOTSTRAP_FIXTURES_ENABLED true

echo "==> Restarting ispf-server"
systemctl restart ispf-server

echo "==> Waiting for API"
for i in $(seq 1 60); do
  if curl -sf "$API/api/v1/info" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

TOKEN=$(curl -s -X POST "$API/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | python3 -c 'import sys,json; print(json.load(sys.stdin).get("token",""))')
if [ -z "$TOKEN" ]; then
  echo "Login failed"
  exit 1
fi

echo "==> Configuring MQTT driver variables on $DEVICE_PATH"
curl -s -w "\nconfigure HTTP:%{http_code}\n" -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  "$API/api/v1/drivers/runtime/configure?devicePath=${DEVICE_PATH}" \
  -d '{"driverId":"mqtt","pollIntervalMs":5000,"configuration":{"ingressVariable":"lastIngress","brokerUrl":"tcp://127.0.0.1:1883","ingressPayloadLanes":true,"telemetryCoalesceMs":10},"pointMappings":{"ingress":"meter"},"autoStart":false}'

echo "==> Starting MQTT driver on $DEVICE_PATH"
curl -s -w "\nstart HTTP:%{http_code}\n" -X POST \
  -H "Authorization: Bearer $TOKEN" \
  "$API/api/v1/drivers/runtime/start?devicePath=${DEVICE_PATH}"

sleep 3
curl -s -H "Authorization: Bearer $TOKEN" \
  "$API/api/v1/drivers/runtime/status?devicePath=${DEVICE_PATH}"
echo

echo "==> Smoke publish to topic meter"
if command -v mosquitto_pub >/dev/null 2>&1; then
  mosquitto_pub -h 127.0.0.1 -t meter -m '{"id":"3123123123","temperature":"22"}'
  sleep 2
  curl -s -H "Authorization: Bearer $TOKEN" \
    "$API/api/v1/objects/by-path/variables?path=root.platform.instances.3123123123" | head -c 500
  echo
else
  echo "mosquitto_pub not installed — skip publish smoke test"
fi

echo "==> Meter bus setup complete"
