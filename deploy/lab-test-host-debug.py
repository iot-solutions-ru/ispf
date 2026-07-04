#!/usr/bin/env python3
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV", timeout=30)
cmd = r"""
TOKEN=$(curl -sf -X POST http://127.0.0.1:18080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo TOKEN_LEN=${#TOKEN}
curl -s -o /tmp/obj.json -w 'HTTP %{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" \
  'http://127.0.0.1:18080/api/v1/objects/by-path?path=root.platform.devices.demo-sensor-01'
head -c 500 /tmp/obj.json; echo
curl -sf -H "Authorization: Bearer $TOKEN" \
  'http://127.0.0.1:18080/api/v1/objects?parent=root.platform.devices' | head -c 1000; echo
docker compose -f /home/iot-solutions/ispf/lab-test-host-compose.yml logs --tail 20 ispf-server
"""
stdin, stdout, stderr = c.exec_command(cmd, timeout=120)
print(stdout.read().decode(errors="replace"))
print(stderr.read().decode(errors="replace"))
c.close()
