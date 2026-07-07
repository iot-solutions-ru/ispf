#!/usr/bin/env python3
import paramiko
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
c = connect_ssh(timeout=60)


remote = r"""
TOKEN=$(curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')

metrics() {
  curl -sf http://127.0.0.1:8000/api/v1/platform/metrics \
    -H "Authorization: Bearer $TOKEN" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); 
for s in d.get("sections",[]):
  if s.get("id")=="automation":
    v=s.get("values",{}); 
    print("eventsFiredTotal", v.get("eventsFiredTotal"));
    print("eventJournalFlushedTotal", v.get("eventJournalFlushedTotal"));
    print("eventJournalQueueSize", v.get("eventJournalQueueSize"))'
}

echo "=== BEFORE ==="
metrics
RX0=$(docker exec ispf-lab-mqtt-1 mosquitto_sub -h localhost -t '$SYS/broker/messages/received' -C 1 -W 5)
TX0=$(docker exec ispf-lab-mqtt-1 mosquitto_sub -h localhost -t '$SYS/broker/messages/sent' -C 1 -W 5)
SUBS=$(docker exec ispf-lab-mqtt-1 mosquitto_sub -h localhost -t '$SYS/broker/subscriptions/count' -C 1 -W 5)
CLIENTS=$(docker exec ispf-lab-mqtt-1 mosquitto_sub -h localhost -t '$SYS/broker/clients/connected' -C 1 -W 5)
echo "broker subs=$SUBS clients=$CLIENTS"

for i in 1 2 3 4 5 6 7 8 9 10; do
  docker exec ispf-lab-mqtt-1 mosquitto_pub -h localhost \
    -t ispf/loadtest/shared/temperature -m "{\"probe\":$i}" >/dev/null
done
sleep 4

echo "=== AFTER 10 publishes ==="
metrics
RX1=$(docker exec ispf-lab-mqtt-1 mosquitto_sub -h localhost -t '$SYS/broker/messages/received' -C 1 -W 5)
TX1=$(docker exec ispf-lab-mqtt-1 mosquitto_sub -h localhost -t '$SYS/broker/messages/sent' -C 1 -W 5)
echo "rx_delta=$((RX1-RX0)) tx_delta=$((TX1-TX0)) fanout=$(( (TX1-TX0) / 10 ))"

echo "=== driver 17 status ==="
curl -sf "http://127.0.0.1:8000/api/v1/drivers/runtime/status?devicePath=root.platform.devices.loadtest-mqtt-dev-00017" \
  -H "Authorization: Bearer $TOKEN" || echo "NO_STATUS_17"

echo "=== ispf errors tail ==="
docker logs ispf-lab-ispf-server-1 2>&1 | grep -iE 'Unknown event|ingress|mqtt|Not connected' | tail -8
"""

_, o, e = c.exec_command(remote, timeout=120)
print(o.read().decode("utf-8", "replace"))
if e.read().decode().strip():
    print("ERR", e.read().decode()[:500])
c.close()
