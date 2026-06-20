#!/bin/bash
# Start SNMP driver for demo device snmp-localhost (run on the ISPF host after deploy).
set -euo pipefail
API="${API:-http://127.0.0.1:8080}"
DEVICE_PATH="${DEVICE_PATH:-root.platform.devices.snmp-localhost}"
TOKEN=$(curl -s -X POST "$API/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | python3 -c 'import sys,json; print(json.load(sys.stdin).get("token",""))')
if [ -z "$TOKEN" ]; then
  echo "Login failed"
  exit 1
fi
curl -s -w "\nstart HTTP:%{http_code}\n" -X POST \
  -H "Authorization: Bearer $TOKEN" \
  "$API/api/v1/drivers/runtime/start?devicePath=${DEVICE_PATH}"
sleep 6
curl -s -H "Authorization: Bearer $TOKEN" \
  "$API/api/v1/drivers/runtime/status?devicePath=${DEVICE_PATH}"
echo
