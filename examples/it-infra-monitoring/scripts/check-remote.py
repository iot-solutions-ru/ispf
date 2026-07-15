import paramiko
import sys

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect("185.246.66.158", username="root", password=sys.argv[1], timeout=20)


def run(cmd: str) -> str:
    _, stdout, stderr = client.exec_command(cmd)
    return (stdout.read() + stderr.read()).decode()


checks = [
    "curl -s -o /dev/null -w 'role:%{http_code}' -H 'X-ISPF-Role: admin' http://127.0.0.1:8080/api/v1/operator-apps",
    "curl -s -H 'X-ISPF-Role: admin' 'http://127.0.0.1:8080/api/v1/objects/by-path?path=root.platform.devices.itm.hub' | head -c 250",
    "pgrep -c -f ispf-server.jar || true",
    "docker exec ispf-postgres psql -U ispf -d ispf -tAc \"SELECT username FROM security_user LIMIT 5\" 2>/dev/null",
]
for cmd in checks:
    print(">", cmd)
    print(run(cmd).strip())
    print("---")

client.close()
