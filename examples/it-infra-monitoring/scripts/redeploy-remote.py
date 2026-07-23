import json
import paramiko
import sys
import time
from pathlib import Path

PASSWORD = sys.argv[1]
REPO = Path(__file__).resolve().parents[3]
BASE = "http://127.0.0.1:8080"

client = paramiko.SSHClient()
client.load_system_host_keys()
client.set_missing_host_key_policy(paramiko.RejectPolicy())
try:
    client.connect("185.246.66.158", username="root", password=PASSWORD, timeout=20)
except paramiko.SSHException as exc:
    print(f"Host key not trusted: {exc}", file=sys.stderr)
    print("SSH to the host once manually to add it to known_hosts.", file=sys.stderr)
    sys.exit(2)


def run(cmd: str, timeout: int = 60) -> str:
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    return (stdout.read() + stderr.read()).decode()


def curl_api(method: str, path: str, body_path: str | None = None, token: str | None = None) -> tuple[int, str]:
    headers = []
    if token:
        headers.append(f"-H 'Authorization: Bearer {token}'")
    if body_path:
        headers.append("-H 'Content-Type: application/json'")
        data = f"--data-binary @{body_path}"
    else:
        data = ""
    header_str = " ".join(headers)
    cmd = f"curl -s -o /tmp/ispf-last.json -w '%{{http_code}}' -X {method} {header_str} {data} '{BASE}{path}'"
    code = run(cmd).strip()
    body = run("cat /tmp/ispf-last.json 2>/dev/null || true")
    return int(code or "0"), body


print("1) Stop duplicate ISPF processes")
run("pkill -9 -f 'ispf-server.jar' || true")
time.sleep(4)
print("remaining:", run("pgrep -af ispf-server.jar || echo none").strip())

print("2) Start single ISPF with env file")
start = (
    "bash -lc 'set -a && source /opt/ispf/ispf-server.env && set +a && "
    "cd /opt/ispf && nohup /usr/bin/java -jar /opt/ispf/ispf-server.jar "
    "--spring.profiles.active=local >> /opt/ispf/data/ispf-server.log 2>&1 & disown'"
)
transport = client.get_transport()
channel = transport.open_session()
channel.exec_command(start)
time.sleep(2)
channel.close()

for i in range(40):
    time.sleep(3)
    code, _ = curl_api("GET", "/api/v1/info")
    print(f"   wait {i + 1}: HTTP {code}")
    if code == 200:
        break
else:
    print(run("tail -40 /opt/ispf/data/ispf-server.log")[-2500:])
    sys.exit(1)

print("process count:", run("pgrep -c -f ispf-server.jar || echo 0").strip())

print("3) Login")
login_path = "/tmp/ispf-login.json"
sftp = client.open_sftp()
with sftp.file(login_path, "w") as f:
    f.write('{"username":"admin","password":"admin"}')
sftp.close()
code, body = curl_api("POST", "/api/v1/auth/login", login_path)
print("login:", code, body[:200])
if code != 200:
    print("Login failed; trying redeploy may still work if users exist with other creds.")
    token = None
else:
    token = json.loads(body)["token"]

if not token:
    sys.exit(2)

print("4) Import bundles")
bundles = [
    ("it-infra-monitoring", REPO / "examples/it-infra-monitoring/bundle.json"),
    ("itm-plugin-inventory-m11", REPO / "plugins/itm-site-inventory/sites/m11/bundle.json"),
    ("itm-plugin-topology-m11", REPO / "plugins/itm-site-topology/sites/m11/bundle.json"),
    ("itm-plugin-integrations-m11", REPO / "plugins/itm-site-integrations/sites/m11/bundle.json"),
]
sftp = client.open_sftp()
remote_bundle = "/tmp/itm-bundle.json"
for package_id, local in bundles:
    sftp.put(str(local), remote_bundle)
    code, body = curl_api(
        "POST",
        f"/api/v1/platform/packages/import?packageId={package_id}",
        remote_bundle,
        token,
    )
    print(f"   {package_id}: HTTP {code} -> {body[:120]}")
sftp.close()

print("5) Mimic diagram")
mimic_src = REPO / "plugins/itm-site-topology/sites/m11/mimic-diagram.json"
diagram = mimic_src.read_text(encoding="utf-8")
payload = json.dumps({"diagramJson": diagram})
with sftp.open("/tmp/mimic-put.json", "w") as f:
    f.write(payload)
code = run(
    f"curl -s -o /tmp/mimic-out.json -w '%{{http_code}}' -X PUT "
    f"-H 'Authorization: Bearer {token}' -H 'Content-Type: application/json' "
    f"--data-binary @/tmp/mimic-put.json "
    f"'{BASE}/api/v1/mimics/by-path/diagram?path=root.platform.mimics.itm-m11-dcn'"
).strip()
print(f"   mimic: HTTP {code}")

code, body = curl_api("GET", "/api/v1/objects/by-path?path=root.platform.devices.itm.hub", token=token)
print("6) hub object:", code, body[:180])
client.close()
print("DONE")
