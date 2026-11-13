#!/usr/bin/env python3
"""Lab ops gates for the TCK stand.

Restarts the TCK application node and the TCK NATS container, and round-trips
one CUSTOM marker through backup/export/delete/import. The container allowlist
refuses every other docker stop/restart, including the shared production
database ispf-db-postgres-1.
"""
import json
import os
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MARKER = "root.platform.devices.tck-ops-marker"
MESSAGE = os.environ.get("OPS_CERT_MESSAGE", "tck-ops-outbox-1")
ALLOWLIST = {"ispf-tck-1", "ispf-tck-nats"}
CONTROL = {"stop", "rm", "kill", "start", "restart"}


class Inbox(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, fmt, *args):
        return


def guard(name):
    if name not in ALLOWLIST:
        raise SystemExit("refusing to control " + name)


def sh(args):
    if args and args[0] == "docker" and len(args) > 1 and args[1] in CONTROL:
        for name in args[2:]:
            if not name.startswith("-"):
                guard(name)
    result = subprocess.run(args, text=True, capture_output=True)
    if result.returncode != 0:
        raise RuntimeError((result.stderr or result.stdout or "")[-400:])
    return result.stdout.strip()


def refuses(name):
    try:
        guard(name)
    except SystemExit:
        return True
    return False


def http(method, path, body=None, token=None, query=None, raw=None):
    url = "http://127.0.0.1:8081" + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = raw if raw is not None else (None if body is None else json.dumps(body).encode())
    headers = {}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            text = response.read().decode()
            parsed = json.loads(text) if text else {}
            return response.status, parsed, text
    except urllib.error.HTTPError as ex:
        text = ex.read().decode()
        try:
            parsed = json.loads(text) if text else {}
        except json.JSONDecodeError:
            parsed = {"raw": text[:300]}
        return ex.code, parsed, text


def login():
    status, body, _text = http("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})
    token = body.get("token") if isinstance(body, dict) else None
    if status != 200 or not token:
        raise RuntimeError("login failed: %s" % status)
    return token


def healthy(port):
    try:
        with urllib.request.urlopen("http://127.0.0.1:%s/api/v1/info" % port, timeout=3) as response:
            return response.status == 200
    except Exception:
        return False


def wait_healthy(port, seconds):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if healthy(port):
            return True
        time.sleep(3)
    return False


def outbox(token):
    status, body, _text = http(
        "GET", "/api/v1/applications/plant-discrete/outbox",
        token=token,
        query={"connectorId": "erp", "messageId": MESSAGE},
    )
    return status, body.get("status"), body.get("attempts")


def ensure_sent(token):
    status, state, attempts = outbox(token)
    if status == 200 and state == "SENT" and attempts == 0:
        return (status, state, attempts)
    if status == 404:
        created, _body, _text = http(
            "POST", "/api/v1/applications/plant-discrete/outbox",
            {"connectorId": "erp", "messageId": MESSAGE, "body": "{}"},
            token,
        )
        if created != 200:
            raise RuntimeError("enqueue failed: %s" % created)
    elif not (status == 200 and state == "PENDING"):
        raise RuntimeError("unexpected outbox %s %s" % (status, state))
    server = ThreadingHTTPServer(("0.0.0.0", 8090), Inbox)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        deadline = time.time() + 30
        while time.time() < deadline:
            status, state, attempts = outbox(token)
            if status == 200 and state == "SENT" and attempts == 0:
                return (status, state, attempts)
            time.sleep(2)
    finally:
        server.shutdown()
        server.server_close()
    raise RuntimeError("outbox did not reach SENT")


def nats_ready():
    running = sh(["docker", "inspect", "-f", "{{.State.Running}}", "ispf-tck-nats"])
    if running != "true":
        return False
    try:
        with socket.create_connection(("127.0.0.1", 4222), 2) as sock:
            banner = sock.recv(16)
        return banner.startswith(b"INFO")
    except OSError:
        return False


def backup_round_trip(token):
    http("DELETE", "/api/v1/objects/by-path", token=token, query={"path": MARKER})
    created, created_body, _text = http(
        "POST", "/api/v1/objects",
        {"parentPath": "root.platform.devices", "name": "tck-ops-marker", "type": "CUSTOM", "displayName": "TCK ops marker"},
        token,
    )
    if created not in (200, 201):
        return {"pass": False, "stage": "create", "status": created, "body": created_body}
    exported, export_body, export_text = http(
        "GET", "/api/v1/platform/backup/export", token=token, query={"rootPath": MARKER}
    )
    if exported != 200 or export_body.get("nodeCount") != 1:
        return {"pass": False, "stage": "export", "status": exported, "nodeCount": export_body.get("nodeCount")}
    deleted, _body, _text = http("DELETE", "/api/v1/objects/by-path", token=token, query={"path": MARKER})
    if deleted != 200:
        return {"pass": False, "stage": "delete", "status": deleted}
    missing, _body, _text = http("GET", "/api/v1/objects/by-path", token=token, query={"path": MARKER})
    restored, restore_body, _text = http(
        "POST", "/api/v1/platform/backup/import", token=token, query={"dryRun": "false"}, raw=export_text.encode()
    )
    present, present_body, _text = http("GET", "/api/v1/objects/by-path", token=token, query={"path": MARKER})
    return {
        "pass": missing == 404 and restored == 200 and restore_body.get("created") == 1 and present == 200,
        "missing": missing,
        "restored": restored,
        "created": restore_body.get("created"),
        "present": present,
        "type": present_body.get("type") if isinstance(present_body, dict) else None,
    }


def main():
    postgres_refused = refuses("ispf-db-postgres-1")
    token = login()
    before = ensure_sent(token)
    backup = backup_round_trip(token)
    sh(["docker", "restart", "ispf-tck-1"])
    node_up = wait_healthy(8081, 180)
    token = login()
    after_node = outbox(token)
    sh(["docker", "restart", "ispf-tck-nats"])
    nats_up = False
    after_nats = (0, None, None)
    deadline = time.time() + 60
    while time.time() < deadline:
        if nats_ready():
            try:
                token = login()
                after_nats = outbox(token)
                nats_up = True
                break
            except Exception:
                pass
        time.sleep(3)
    peer_up = healthy(8082)
    marker = 0
    marker_deadline = time.time() + 30
    while time.time() < marker_deadline:
        marker, _marker_body, _text = http("GET", "/api/v1/objects/by-path", token=token, query={"path": MARKER})
        if marker == 200:
            break
        time.sleep(2)
        try:
            token = login()
        except Exception:
            pass
    result = {
        "pass": (
            postgres_refused
            and backup.get("pass") is True
            and node_up
            and before == (200, "SENT", 0)
            and after_node == (200, "SENT", 0)
            and nats_up
            and after_nats == (200, "SENT", 0)
            and peer_up
            and marker == 200
        ),
        "messageId": MESSAGE,
        "backup": backup,
        "before": before,
        "afterNodeRestart": after_node,
        "afterNatsRestart": after_nats,
        "nodeUp": node_up,
        "natsUp": nats_up,
        "peerUp": peer_up,
        "marker": marker,
        "postgresRestart": "refused" if postgres_refused else "not-guarded",
    }
    print(json.dumps(result), flush=True)
    raise SystemExit(0 if result["pass"] else 1)


if __name__ == "__main__":
    main()
