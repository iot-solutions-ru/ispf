#!/usr/bin/env python3
import paramiko

c = connect_ssh(timeout=30)

cmds = [
    "ss -tlnp | grep -E ':8000|:18080' || true",
    "docker compose -f /home/iot-solutions/ispf/lab-test-host-compose.yml ps -a",
    "curl -s -o /dev/null -w '8000 HTTP %{http_code}\\n' http://127.0.0.1:8000/ || true",
    "curl -sf http://127.0.0.1:8000/api/v1/info | head -c 300 || echo API_FAIL",
    "docker compose -f /home/iot-solutions/ispf/lab-test-host-compose.yml logs --tail 25 ispf-server",
    "docker compose -f /home/iot-solutions/ispf/lab-test-host-compose.yml logs --tail 10 nginx",
]
for cmd in cmds:
    print("===", cmd[:80], "===")
    _, o, e = c.exec_command(cmd, timeout=60)
    print(o.read().decode(errors="replace"))
    err = e.read().decode(errors="replace")
    if err:
        print("ERR:", err[:500])
c.close()
