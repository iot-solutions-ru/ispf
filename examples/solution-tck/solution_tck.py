#!/usr/bin/env python3
"""One versioned solution-TCK run.

Green is computed only from phases this process executes. It does not read an
older peak file. Historian rate, year-rollup, and ClickHouse-outage claims are
not required. The shared production Postgres container is never restarted.
"""
import hashlib
import json
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

VERSION = 1
OUTAGE_SECONDS = 1800
MESSAGE = "runner-outage-" + str(int(time.time()))
HITS = []


class Inbox(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        HITS.append(self.headers.get("Idempotency-Key"))
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, fmt, *args):
        return


def http(method, path, body=None, token=None, query=None):
    url = "http://127.0.0.1:8081" + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = None if body is None else json.dumps(body).encode()
    headers = {}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as ex:
        raw = ex.read().decode()
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:300]}
        return ex.code, parsed


def psql(sql):
    result = subprocess.run(
        ["docker", "exec", "ispf-db-postgres-1", "psql", "-U", "ispf", "-d", "ispf_solution_tck", "-tAc", sql],
        text=True, capture_output=True,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr[-400:])
    return result.stdout.strip()


def login():
    status, body = http("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})
    token = body.get("token") if isinstance(body, dict) else None
    if status != 200 or not token:
        raise RuntimeError("login failed: %s" % status)
    return token


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def clickhouse_version():
    result = subprocess.run(
        ["docker", "exec", "ispf-tck-clickhouse", "clickhouse-client", "-q", "SELECT version()"],
        text=True, capture_output=True,
    )
    if result.returncode != 0:
        return ""
    return result.stdout.strip()


def run_ops():
    script = Path(__file__).resolve().parent / "ops_cert.py"
    result = subprocess.run([sys.executable, str(script)], text=True, capture_output=True)
    line = (result.stdout or "").strip().splitlines()
    try:
        payload = json.loads(line[-1]) if line else {"pass": False, "error": (result.stderr or "")[-400:]}
    except json.JSONDecodeError:
        payload = {"pass": False, "error": (result.stdout or result.stderr or "")[-400:]}
    payload["exitCode"] = result.returncode
    return payload


def erp_outage():
    token = login()
    status, created = http(
        "POST", "/api/v1/applications/plant-discrete/outbox",
        {"connectorId": "erp", "messageId": MESSAGE, "body": "{\"runner\":true}"},
        token,
    )
    print("erp-enqueued", status, created.get("created"), created.get("status"), flush=True)
    time.sleep(12)
    held = psql(
        "SELECT COUNT(*)::text FROM application_outbox WHERE message_id = '%s'" % MESSAGE
    )
    state = psql(
        "SELECT COALESCE(status, '') || '|' || COALESCE(attempts::text, '') FROM application_outbox WHERE message_id = '%s'" % MESSAGE
    )
    print("erp-held", held, state, flush=True)
    if status != 200 or created.get("created") is not True or held != "1" or state != "PENDING|0":
        return {"pass": False, "stage": "hold", "held": held + "|" + state, "messageId": MESSAGE}
    deadline = time.time() + OUTAGE_SECONDS
    while time.time() < deadline:
        time.sleep(min(60, max(1, deadline - time.time())))
        print("erp-remaining", int(max(0, deadline - time.time())), flush=True)
    server = ThreadingHTTPServer(("0.0.0.0", 8090), Inbox)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    delivered = {}
    try:
        until = time.time() + 40
        while time.time() < until:
            read_code, delivered = http(
                "GET", "/api/v1/applications/plant-discrete/outbox",
                token=login(),
                query={"connectorId": "erp", "messageId": MESSAGE},
            )
            print("erp-poll", read_code, delivered.get("status"), len(HITS), flush=True)
            if delivered.get("status") == "SENT" and HITS == [MESSAGE]:
                break
            time.sleep(2)
        time.sleep(12)
    finally:
        server.shutdown()
        server.server_close()
    stored_count = psql("SELECT COUNT(*) FROM application_outbox WHERE message_id = '%s'" % MESSAGE)
    stored_state = psql(
        "SELECT COALESCE(status, '') || '|' || COALESCE(attempts::text, '') FROM application_outbox WHERE message_id = '%s'" % MESSAGE
    )
    chained = psql(
        "SELECT COUNT(*) FROM audit_events e JOIN audit_event_chain c ON c.event_id = e.id "
        "WHERE e.action = 'outbox.enqueue' AND e.target_id = '%s'" % MESSAGE
    )
    token = login()
    chain = psql("SELECT event_id || '|' || chain_hash FROM audit_event_chain ORDER BY seq DESC LIMIT 1;")
    detected = False
    restored_intact = False
    if "|" in chain:
        event_id, original = chain.split("|", 1)
        try:
            psql("UPDATE audit_event_chain SET chain_hash = '%s' WHERE event_id = '%s';" % ("0" * 64, event_id))
            verify_status, broken = http("GET", "/api/v1/audit/verify-chain", token=token)
            detected = verify_status == 200 and broken.get("intact") is False
        finally:
            psql("UPDATE audit_event_chain SET chain_hash = '%s' WHERE event_id = '%s';" % (original, event_id))
        verify_status, restored = http("GET", "/api/v1/audit/verify-chain", token=token)
        restored_intact = verify_status == 200 and restored.get("intact") is True
    ok = (
        stored_count == "1"
        and stored_state == "SENT|0"
        and HITS == [MESSAGE]
        and chained == "1"
        and detected
        and restored_intact
    )
    return {
        "pass": ok,
        "executedThisProcess": True,
        "messageId": MESSAGE,
        "outageSeconds": OUTAGE_SECONDS,
        "stored": stored_count + "|" + stored_state,
        "deliveries": len(HITS),
        "keys": sorted(set(HITS)),
        "enqueueChained": chained,
        "tamperDetected": detected,
        "restoredIntact": restored_intact,
    }


def main():
    ops = run_ops()
    print("ops", ops.get("pass"), flush=True)
    erp = erp_outage()
    print("erp", erp.get("pass"), flush=True)
    jar = "/home/iot-solutions/ispf-tck/ispf-server.jar"
    report = {
        "version": VERSION,
        "green": bool(ops.get("pass") is True and ops.get("exitCode") == 0 and erp.get("pass") is True),
        "phases": {
            "ops": ops,
            "erpOutage": erp,
            "historian": {
                "required": False,
                "reason": "year p99, 250-500k, and ClickHouse-outage zero-loss were not required",
            },
        },
        "externalEvidence": ["pen-test", "field-soak"],
        "jarSha256": sha256(jar) if Path(jar).is_file() else "",
        "clickhouseVersion": clickhouse_version(),
        "postgresRestart": ops.get("postgresRestart", "unknown"),
    }
    print(json.dumps(report), flush=True)
    raise SystemExit(0 if report["green"] else 1)


if __name__ == "__main__":
    main()
