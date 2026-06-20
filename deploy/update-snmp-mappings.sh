#!/bin/bash
# Update snmp-localhost point mappings on running ISPF (local profile, X-ISPF-Role: admin).
set -euo pipefail
API="${API:-http://127.0.0.1:8080}"
PATH_DEVICE="${PATH_DEVICE:-root.platform.devices.snmp-localhost}"

MAPPINGS='{"sysName":"1.3.6.1.2.1.1.5.0:STRING","sysDescr":"1.3.6.1.2.1.1.1.0:STRING","sysUpTime":"1.3.6.1.2.1.1.3.0","sysLocation":"1.3.6.1.2.1.1.6.0:STRING","sysContact":"1.3.6.1.2.1.1.4.0:STRING","hrMemorySize":"1.3.6.1.2.1.25.2.2.0:INTEGER","hrSystemProcesses":"1.3.6.1.2.1.25.1.6.0:INTEGER","hrSystemNumUsers":"1.3.6.1.2.1.25.1.5.0:INTEGER","ifNumber":"1.3.6.1.2.1.2.1.0:INTEGER","ifInOctets":"1.3.6.1.2.1.2.2.1.10.2:INTEGER","ifOutOctets":"1.3.6.1.2.1.2.2.1.16.2:INTEGER","hrProcessorLoad":"1.3.6.1.2.1.25.3.3.1.2.196608:INTEGER:optional"}'

echo "=== Current driver status ==="
curl -sf -H 'X-ISPF-Role: admin' \
  "$API/api/v1/drivers/runtime/status?devicePath=$PATH_DEVICE" || echo "not running"
echo

BODY=$(python3 -c "import json; print(json.dumps({'schema':{'name':'driverPointMappingsJson','fields':[{'name':'value','type':'STRING'}]},'rows':[{'value':'''$MAPPINGS'''}]}))")

echo "=== Updating mappings ==="
curl -sf -X PUT -H 'X-ISPF-Role: admin' -H 'Content-Type: application/json' \
  "$API/api/v1/objects/by-path/variables?path=$PATH_DEVICE&name=driverPointMappingsJson" \
  -d "$BODY"
echo

curl -sf -X POST -H 'X-ISPF-Role: admin' \
  "$API/api/v1/drivers/runtime/stop?devicePath=$PATH_DEVICE" || true
sleep 2
curl -sf -X POST -H 'X-ISPF-Role: admin' \
  "$API/api/v1/drivers/runtime/start?devicePath=$PATH_DEVICE"
echo
sleep 8
echo "=== Driver status after restart ==="
curl -sf -H 'X-ISPF-Role: admin' \
  "$API/api/v1/drivers/runtime/status?devicePath=$PATH_DEVICE"
echo
