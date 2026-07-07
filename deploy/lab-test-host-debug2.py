#!/usr/bin/env python3
import paramiko

c = connect_ssh(timeout=30)

cmd = r"""
COMPOSE=/home/iot-solutions/ispf/lab-test-host-compose.yml
CID=$(docker compose -f $COMPOSE ps -q ispf-server)
docker exec "$CID" curl -sf -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' > /tmp/login.json
cat /tmp/login.json
echo
TOKEN=$(python3 -c "import json; print(json.load(open('/tmp/login.json'))['token'])")
docker exec "$CID" curl -s -o /tmp/obj.json -w 'direct HTTP %{http_code}\n' \
  -H "Authorization: Bearer ${TOKEN}" \
  'http://127.0.0.1:8080/api/v1/objects/by-path?path=root.platform.devices.demo-sensor-01'
cat /tmp/obj.json | head -c 400; echo
"""
stdin, stdout, stderr = c.exec_command(cmd, timeout=120)
print(stdout.read().decode(errors="replace"))
print(stderr.read().decode(errors="replace"))
c.close()
