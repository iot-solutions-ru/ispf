"""Deploy web-console + ITM bundles to M11 pilot (interactive SVG widget)."""
import json
import os
import sys
import time
from pathlib import Path

import paramiko

HOST = "185.246.66.158"
USER = "root"
PASSWORD = os.environ.get("ISPF_SSH_PASSWORD") or (sys.argv[1] if len(sys.argv) > 1 else "")
REPO = Path(__file__).resolve().parents[3]
DIST = REPO / "apps/web-console/dist"
WEB_ROOT = "/opt/ispf/web-console"
BASE = "http://127.0.0.1:8080"

if not PASSWORD:
    print("Usage: ISPF_SSH_PASSWORD=... python deploy-svg-pilot.py")
    sys.exit(1)

if not DIST.is_dir():
    print("Missing web-console dist. Run: cd apps/web-console && npm run build")
    sys.exit(1)


def run(client: paramiko.SSHClient, cmd: str, timeout: int = 120) -> str:
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read() + stderr.read()
    return out.decode(errors="replace")


def curl_api(client: paramiko.SSHClient, method: str, path: str, body_path: str | None = None, token: str | None = None) -> tuple[int, str]:
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
    code = run(client, cmd).strip()
    body = run(client, "cat /tmp/ispf-last.json 2>/dev/null || true")
    try:
        return int(code or "0"), body
    except ValueError:
        return 0, body


print("1) Connect SSH")
client = paramiko.SSHClient()
client.load_system_host_keys()
client.set_missing_host_key_policy(paramiko.RejectPolicy())
try:
    client.connect(HOST, username=USER, password=PASSWORD, timeout=30)
except paramiko.SSHException as exc:
    print(f"Host key for {HOST} not trusted: {exc}", file=sys.stderr)
    print("SSH to the host once manually to add it to known_hosts.", file=sys.stderr)
    sys.exit(2)

print("2) Upload web-console dist ->", WEB_ROOT)
run(client, f"mkdir -p '{WEB_ROOT}'")
sftp = client.open_sftp()

uploaded = 0

def upload_tree(local: Path, remote: str) -> None:
    global uploaded
    for item in local.iterdir():
        rpath = f"{remote}/{item.name}"
        if item.is_dir():
            try:
                sftp.mkdir(rpath)
            except OSError:
                pass
            upload_tree(item, rpath)
        else:
            sftp.put(str(item), rpath)
            uploaded += 1

upload_tree(DIST, WEB_ROOT)
sftp.close()
print(f"   uploaded {uploaded} files")

print("3) Health check")
code, _ = curl_api(client, "GET", "/api/v1/info")
print(f"   /api/v1/info -> HTTP {code}")
if code != 200:
    print(run(client, "tail -30 /opt/ispf/data/ispf-server.log")[-2000:])
    client.close()
    sys.exit(1)

print("4) Login")
login_path = "/tmp/ispf-login.json"
sftp = client.open_sftp()
with sftp.file(login_path, "w") as f:
    f.write('{"username":"admin","password":"admin"}')
sftp.close()
code, body = curl_api(client, "POST", "/api/v1/auth/login", login_path)
print(f"   login -> HTTP {code}")
if code != 200:
    client.close()
    sys.exit(2)
token = json.loads(body)["token"]

print("5) Import bundles")
bundles = [
    ("it-infra-monitoring", REPO / "examples/it-infra-monitoring/bundle.json"),
    ("itm-plugin-inventory-m11", REPO / "plugins/itm-site-inventory/sites/m11/bundle.json"),
    ("itm-plugin-topology-m11", REPO / "plugins/itm-site-topology/sites/m11/bundle.json"),
    ("itm-plugin-integrations-m11", REPO / "plugins/itm-site-integrations/sites/m11/bundle.json"),
]
sftp = client.open_sftp()
remote_bundle = "/tmp/itm-bundle.json"
for package_id, local in bundles:
    if not local.is_file():
        print(f"   SKIP missing {local}")
        continue
    sftp.put(str(local), remote_bundle)
    code, resp = curl_api(
        client,
        "POST",
        f"/api/v1/platform/packages/import?packageId={package_id}",
        remote_bundle,
        token,
    )
    print(f"   {package_id}: HTTP {code} {resp[:100]}")
sftp.close()

print("6) Verify DCN dashboard widget")
code, body = curl_api(
    client,
    "GET",
    "/api/v1/dashboards/by-path?path=root.platform.dashboards.itm-dcn",
    token=token,
)
if code == 200:
    layout = json.loads(json.loads(body).get("layoutJson", "{}"))
    topo = next((w for w in layout.get("widgets", []) if w.get("id") == "dcn-topology"), None)
    if topo:
        print(f"   dcn-topology type={topo.get('type')} behaviors={bool(topo.get('behaviorsJson'))} svgInner={bool(topo.get('svgInnerJson'))}")
    else:
        print("   dcn-topology widget not found in layout")
else:
    print(f"   dashboard fetch failed HTTP {code}")

client.close()
print("DONE — open Network Topology Map (itm-dcn), hard-refresh browser (Ctrl+F5)")
