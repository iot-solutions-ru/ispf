#!/usr/bin/env python3
"""Failure gates for the solution-tck stand. Nodes must already be up.

Green requires every plan threshold: ClickHouse catch-up after the configured
outage, driver-lease failover under 5 seconds, one ERP delivery after the
configured receiver outage, a tampered audit chain, and at least 100 000
tags/s. A measured rate below that stays a failure.
"""

import json
import os
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = "/home/iot-solutions/ispf-tck"
RESULT = ROOT + "/gates.json"
DEVICE = "root.platform.devices.tck-field"
CMD_DEVICE = "root.platform.devices.tck-cmd2"
CMD_PORT = 8091
CH_OUTAGE_SECONDS = int(os.environ.get("SOLUTION_TCK_CH_OUTAGE_SECONDS", "600"))
ERP_OUTAGE_SECONDS = int(os.environ.get("SOLUTION_TCK_ERP_OUTAGE_SECONDS", "1800"))
ERP_PORT = 8090
PORTS = {"replica-1": 8081, "replica-2": 8082}
CONTAINERS = {"replica-1": "ispf-tck-1", "replica-2": "ispf-tck-2"}


def log(message):
    print(message, flush=True)


def sh(args, check=True, input_text=None):
    import subprocess
    proc = subprocess.run(args, input=input_text, text=True, capture_output=True)
    if check and proc.returncode != 0:
        raise RuntimeError((proc.stderr or proc.stdout or "").strip()[-2000:])
    return proc


def http(method, url, body=None, token=None, timeout=20):
    data = None if body is None else json.dumps(body).encode()
    headers = {}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as ex:
        raw = ex.read().decode()
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:400]}
        return ex.code, parsed


def api(method, path, token, body=None, query=None, port=8081):
    url = f"http://127.0.0.1:{port}{path}"
    if query:
        url += "?" + urllib.parse.urlencode(query)
    return http(method, url, body, token)


def login(port, deadline):
    while time.time() < deadline:
        try:
            status, body = http(
                "POST",
                f"http://127.0.0.1:{port}/api/v1/auth/login",
                {"username": "admin", "password": "admin"},
            )
            if status == 200 and body.get("token"):
                return body["token"]
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError(f"login on :{port} failed")


def psql(sql):
    return sh(
        ["docker", "exec", "-i", "ispf-db-postgres-1", "psql", "-U", "ispf", "-d", "ispf_solution_tck",
         "-v", "ON_ERROR_STOP=1", "-tA"],
        input_text=sql,
    ).stdout.strip()


def ch_count():
    query = (
        "SELECT count() FROM default.variable_samples "
        f"WHERE object_path = '{DEVICE}' AND variable_name = 'level'"
    )
    proc = sh(
        ["docker", "exec", "ispf-tck-clickhouse", "clickhouse-client",
         "--user", "default", "--password", "tck", "--query", query],
        check=False,
    )
    text = (proc.stdout or "").strip()
    return int(text) if proc.returncode == 0 and text.isdigit() else 0


def pg_count():
    text = psql(
        "SELECT count(*) FROM variable_samples "
        f"WHERE object_path = '{DEVICE}' AND variable_name = 'level';"
    )
    return int(text or "0")


def level_body(value):
    return {
        "schema": {"name": "level", "fields": [{"name": "value", "type": "DOUBLE"}]},
        "rows": [{"value": value}],
    }


def setup(token):
    status, body = api("POST", "/api/v1/objects", token, {
        "parentPath": "root.platform.devices",
        "name": "tck-field",
        "type": "DEVICE",
        "displayName": "solution-tck field",
    })
    if status not in (200, 409):
        raise RuntimeError(f"create device {status} {body}")
    status, body = api("POST", "/api/v1/objects/by-path/variables", token, {
        "name": "level",
        "schema": {"name": "level", "fields": [{"name": "value", "type": "DOUBLE"}]},
        "readable": True,
        "writable": True,
        "historyEnabled": True,
    }, {"path": DEVICE})
    if status not in (200, 409) and "already exists" not in json.dumps(body):
        raise RuntimeError(f"create variable {status} {body}")
    status, body = api("PATCH", "/api/v1/objects/by-path/variables/history", token, {
        "historyEnabled": True,
        "historyRetentionDays": 7,
        "historySampleMode": "ALL_VALUES",
    }, {"path": DEVICE, "name": "level"})
    if status != 200:
        raise RuntimeError(f"history policy {status} {body}")
    status, body = api("PUT", "/api/v1/drivers/runtime/configure", token, {
        "driverId": "virtual",
        "pollIntervalMs": 1000,
        "configuration": {"baseTemperature": "22.0", "amplitude": "1.0"},
        "pointMappings": {"temperature": "sim"},
        "autoStart": True,
    }, {"devicePath": DEVICE})
    if status != 200 and "owned by" not in json.dumps(body):
        raise RuntimeError(f"configure driver {status} {body}")
    api("POST", "/api/v1/drivers/runtime/start", token, query={"devicePath": DEVICE})


def clickhouse_outage(token):
    stop = threading.Event()
    writes = {"ok": 0}
    value = {"n": 0}

    def writer():
        while not stop.is_set():
            value["n"] += 1
            status, _body = api(
                "PUT", "/api/v1/objects/by-path/variables", token,
                level_body(value["n"] / 10.0),
                {"path": DEVICE, "name": "level"},
            )
            if status == 200:
                writes["ok"] += 1
            time.sleep(0.02)

    thread = threading.Thread(target=writer, daemon=True)
    thread.start()
    time.sleep(2)
    stop.set()
    thread.join(timeout=5)
    time.sleep(2)
    primary_before = pg_count()
    clickhouse_before = ch_count()
    stop.clear()
    thread = threading.Thread(target=writer, daemon=True)
    thread.start()
    log("stopping private ClickHouse")
    sh(["docker", "stop", "ispf-tck-clickhouse"])
    time.sleep(CH_OUTAGE_SECONDS)
    sh(["docker", "start", "ispf-tck-clickhouse"])
    deadline = time.time() + 90
    while time.time() < deadline:
        probe = sh(
            ["docker", "exec", "ispf-tck-clickhouse", "clickhouse-client",
             "--user", "default", "--password", "tck", "--query", "SELECT 1"],
            check=False,
        )
        if probe.returncode == 0:
            break
        time.sleep(2)
    time.sleep(3)
    stop.set()
    thread.join(timeout=10)
    primary = pg_count()
    secondary = ch_count()
    matched = False
    deadline = time.time() + 180
    while time.time() < deadline:
        primary = pg_count()
        secondary = ch_count()
        gained = primary - primary_before
        if gained > 0 and secondary - clickhouse_before == gained:
            matched = True
            break
        time.sleep(5)
    return {
        "pass": matched,
        "outageSeconds": CH_OUTAGE_SECONDS,
        "acceptedWrites": writes["ok"],
        "primaryBefore": primary_before,
        "clickHouseBefore": clickhouse_before,
        "primaryRows": primary,
        "clickHouseRows": secondary,
    }


def arm_command_driver(token):
    status, body = api("POST", "/api/v1/objects", token, {
        "parentPath": "root.platform.devices",
        "name": "tck-cmd2",
        "type": "DEVICE",
        "displayName": "solution-tck commands",
    })
    if status not in (200, 409) and "already exists" not in json.dumps(body):
        raise RuntimeError(f"create command device {status} {body}")
    payload = {
        "driverId": "mqtt",
        "pollIntervalMs": 5000,
        "configuration": {
            "brokerUrl": "tcp://ispf-tck-mqtt:1883",
            "topicPrefix": "ispf/tck/cmd/",
            "ingressCoalesceEnabled": "false",
            "eventToVariable": "false",
        },
        "pointMappings": {"command": "command"},
        "autoStart": True,
        "telemetryPublishMode": "TELEMETRY_ONLY",
    }
    status, body = api(
        "PUT", "/api/v1/drivers/runtime/configure", token, payload, {"devicePath": CMD_DEVICE},
    )
    if status == 409 and "owned by replica" in json.dumps(body):
        owner = json.dumps(body).split("owned by replica ")[-1].split(";")[0].strip().strip('"')
        status, body = api(
            "PUT", "/api/v1/drivers/runtime/configure", token, payload,
            {"devicePath": CMD_DEVICE}, port=PORTS.get(owner, 8081),
        )
    if status != 200 and "owned by" not in json.dumps(body):
        raise RuntimeError(f"configure command driver {status} {body}")
    api("POST", "/api/v1/drivers/runtime/start", token, query={"devicePath": CMD_DEVICE})


def reject_stale_fencing_epoch(token, holder):
    """The holder still has the epoch from when it acquired the lease.

    Advancing the database generation makes that in-memory epoch stale. The
    write must be refused, then the generation is put back.
    """
    if holder not in PORTS:
        return {"pass": False, "reason": f"holder {holder!r} has no API port"}
    epoch = psql(
        "SELECT fencing_epoch FROM platform_driver_locks "
        f"WHERE device_path = '{CMD_DEVICE}' AND holder_id = '{holder}';"
    )
    if not epoch.isdigit() or int(epoch) < 1:
        return {"pass": False, "reason": f"no fencing epoch ({epoch!r})"}
    psql(
        "UPDATE platform_driver_locks SET fencing_epoch = fencing_epoch + 1 "
        f"WHERE device_path = '{CMD_DEVICE}' AND holder_id = '{holder}';"
    )
    try:
        status, body = api(
            "POST", "/api/v1/drivers/runtime/write", token,
            {"rows": [{"value": "stale-epoch"}]},
            {"devicePath": CMD_DEVICE, "pointId": "command"},
            port=PORTS[holder],
        )
    finally:
        psql(
            "UPDATE platform_driver_locks SET fencing_epoch = "
            f"{int(epoch)} WHERE device_path = '{CMD_DEVICE}' AND holder_id = '{holder}';"
        )
    text = json.dumps(body)
    return {
        "pass": status == 409 and "stale fencing epoch" in text,
        "status": status,
        "detail": body.get("detail") if isinstance(body, dict) else text[:300],
    }


def failover(token):
    import subprocess
    received = []
    subscriber = subprocess.Popen(
        ["docker", "exec", "-t", "ispf-tck-mqtt", "mosquitto_sub", "-h", "localhost",
         "-t", "ispf/tck/cmd/command", "-v"],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
    )

    def collect():
        if subscriber.stdout is None:
            return
        for line in subscriber.stdout:
            received.append(line.strip())

    threading.Thread(target=collect, daemon=True).start()
    try:
        arm_command_driver(token)
        holder = ""
        deadline = time.time() + 30
        while time.time() < deadline:
            holder = psql(
                "SELECT holder_id FROM platform_driver_locks "
                f"WHERE device_path = '{CMD_DEVICE}';"
            )
            if holder in CONTAINERS:
                break
            time.sleep(1)
        if holder not in CONTAINERS:
            return {"pass": False, "reason": f"no command-driver lease, holder={holder!r}"}
        other_id = "replica-2" if holder == "replica-1" else "replica-1"
        sh(["docker", "restart", CONTAINERS[other_id]])
        ready = time.time() + 90
        while time.time() < ready:
            try:
                with urllib.request.urlopen(
                    f"http://127.0.0.1:{PORTS[other_id]}/api/v1/info", timeout=5
                ) as response:
                    if response.status == 200:
                        break
            except Exception:
                time.sleep(2)
        time.sleep(3)
        survivor = other_id
        stop = threading.Event()
        accepted = []
        last_error = {"text": ""}
        target = {"port": PORTS[holder]}

        def sender():
            n = 0
            while not stop.is_set():
                n += 1
                try:
                    status, body = api(
                        "POST", "/api/v1/drivers/runtime/write", token,
                        {"rows": [{"value": f"cmd-{n}"}]},
                        {"devicePath": CMD_DEVICE, "pointId": "command"},
                        port=target["port"],
                    )
                except Exception as ex:
                    last_error["text"] = str(ex)[:300]
                    time.sleep(0.05)
                    continue
                if status == 200:
                    accepted.append(n)
                else:
                    last_error["text"] = f"{status} {body}"[:300]
                time.sleep(0.05)

        thread = threading.Thread(target=sender, daemon=True)
        thread.start()
        time.sleep(1)
        container = CONTAINERS[holder]
        started = time.time()
        sh(["docker", "kill", "--signal", "KILL", container])
        moved = ""
        elapsed = None
        deadline = time.time() + 20
        while time.time() < deadline:
            moved = psql(
                "SELECT holder_id FROM platform_driver_locks "
                f"WHERE device_path = '{CMD_DEVICE}';"
            )
            if moved and moved != holder:
                elapsed = time.time() - started
                if moved in PORTS:
                    target["port"] = PORTS[moved]
                break
            time.sleep(0.2)
        time.sleep(6)
        stop.set()
        thread.join(timeout=5)
        sh(["docker", "start", container], check=False)
        sh(
            ["docker", "exec", "ispf-tck-mqtt", "mosquitto_pub", "-h", "localhost",
             "-t", "ispf/tck/cmd/command", "-m", "sentinel-end"],
            check=False,
        )
        drain_deadline = time.time() + 5
        while time.time() < drain_deadline and not any("sentinel-end" in line for line in list(received)):
            time.sleep(0.05)
        delivered = []
        for body in received:
            payload = body.replace("\r", "").split()[-1] if body else ""
            if payload == "sentinel-end":
                continue
            for n in accepted:
                if payload == f"cmd-{n}":
                    delivered.append(n)
        duplicates = len(delivered) - len(set(delivered))
        missing = [n for n in accepted if n not in set(delivered)]
        lease_ok = elapsed is not None and elapsed < 5.0
        commands_ok = len(accepted) > 0 and not missing and duplicates == 0
        stale = reject_stale_fencing_epoch(token, moved) if moved in PORTS else {
            "pass": False,
            "reason": f"lease did not move to a known replica ({moved!r})",
        }
        return {
            "pass": lease_ok and commands_ok and stale.get("pass") is True,
            "previousHolder": holder,
            "newHolder": moved,
            "seconds": None if elapsed is None else round(elapsed, 3),
            "acceptedCommands": len(accepted),
            "deliveredCommands": len(set(delivered)),
            "duplicateCommands": duplicates,
            "lostCommands": len(missing),
            "lastError": last_error["text"],
            "staleEpoch": stale,
        }
    finally:
        subscriber.terminate()
        try:
            subscriber.wait(timeout=5)
        except subprocess.TimeoutExpired:
            subscriber.kill()
        if subscriber.stdout is not None:
            subscriber.stdout.close()


def erp_outage():
    psql("DELETE FROM application_outbox WHERE id = 'tck-erp-1';")
    psql(
        "INSERT INTO application_outbox (id, app_id, connector_id, message_id, body, status, attempts, created_at) "
        "VALUES ('tck-erp-1', 'solution-tck', 'sap', 'tck-msg-1', '{\"order\":\"TCK-1\"}', 'PENDING', 0, now());"
    )
    posts = []
    lock = threading.Lock()

    class Inbox(BaseHTTPRequestHandler):
        def do_POST(self):
            length = int(self.headers.get("Content-Length", "0") or "0")
            self.rfile.read(length)
            key = self.headers.get("Idempotency-Key", "")
            with lock:
                posts.append(key)
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"{}")

        def log_message(self, fmt, *args):
            return

    log(f"ERP receiver down for {ERP_OUTAGE_SECONDS}s")
    time.sleep(ERP_OUTAGE_SECONDS)
    server = ThreadingHTTPServer(("0.0.0.0", ERP_PORT), Inbox)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    status = ""
    attempts = ""
    deadline = time.time() + 30
    try:
        while time.time() < deadline:
            status = psql("SELECT status FROM application_outbox WHERE id = 'tck-erp-1';")
            attempts = psql("SELECT attempts FROM application_outbox WHERE id = 'tck-erp-1';")
            if status == "SENT":
                break
            time.sleep(2)
    finally:
        server.shutdown()
    unique = sorted(set(posts))
    return {
        "pass": status == "SENT" and unique == ["tck-msg-1"] and attempts == "0",
        "outageSeconds": ERP_OUTAGE_SECONDS,
        "status": status,
        "attempts": attempts,
        "deliveries": len(posts),
        "keys": unique,
    }


def audit_tamper(token):
    row = psql("SELECT event_id || '|' || chain_hash FROM audit_event_chain ORDER BY seq DESC LIMIT 1;")
    if "|" not in row:
        return {"pass": False, "reason": "audit chain is empty"}
    event_id, original = row.split("|", 1)
    zero = "0" * 64
    try:
        psql(f"UPDATE audit_event_chain SET chain_hash = '{zero}' WHERE event_id = '{event_id}';")
        status, broken = api("GET", "/api/v1/audit/verify-chain", token)
        detected = status == 200 and broken.get("intact") is False
    finally:
        psql(f"UPDATE audit_event_chain SET chain_hash = '{original}' WHERE event_id = '{event_id}';")
    status, restored = api("GET", "/api/v1/audit/verify-chain", token)
    return {
        "pass": detected and status == 200 and restored.get("intact") is True,
        "tamperDetected": detected,
        "restored": restored,
    }


def throughput(token):
    stop = time.time() + 5
    counter = {"ok": 0}
    lock = threading.Lock()

    def worker(seed):
        n = seed
        while time.time() < stop:
            n += 4
            status, _body = api(
                "PUT", "/api/v1/objects/by-path/variables", token,
                level_body(100000 + n),
                {"path": DEVICE, "name": "level"},
            )
            if status == 200:
                with lock:
                    counter["ok"] += 1

    started = time.time()
    threads = [threading.Thread(target=worker, args=(i,)) for i in range(4)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    elapsed = max(time.time() - started, 0.001)
    rate = counter["ok"] / elapsed
    return {
        "pass": rate >= 100000,
        "restWritesPerSecond": round(rate, 1),
        "acceptedWrites": counter["ok"],
        "seconds": round(elapsed, 3),
        "reason": "REST sample writes on one tag; 100000 tags/s was not reached",
    }


def historian_peak():
    path = ROOT + "/peak.json"
    if not os.path.exists(path):
        return {"pass": False, "reason": "no historian peak measurement on this stand"}
    peak = json.loads(open(path, encoding="utf-8").read())
    rate = float(peak.get("rowsPerSecond") or 0)
    queue = peak.get("queueSize")
    return {
        "pass": rate >= 100000 and queue == 0,
        "rowsPerSecond": rate,
        "flushedPerSecond": peak.get("flushedPerSecond"),
        "queueSize": queue,
        "store": peak.get("store"),
        "reason": "stored ClickHouse rows during the peak run",
    }


def _openssl_bytes(args):
    import subprocess
    proc = subprocess.run(["openssl", *args], capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError((proc.stderr or b"").decode()[-500:])
    return proc.stdout


def _enroll(token, public_b64):
    status, body = api("PUT", "/api/v1/auth/signature-key", token, {"publicKey": public_b64})
    if status != 200:
        raise RuntimeError(f"enroll {status} {body}")
    return body


def _login_user(username, password):
    status, body = http(
        "POST",
        "http://127.0.0.1:8081/api/v1/auth/login",
        {"username": username, "password": password},
    )
    if status != 200 or not body.get("token"):
        raise RuntimeError(f"login {username} {status} {body}")
    return body["token"]


def double_signature(admin_token):
    import hashlib
    import base64
    source = '{"signaturePolicy":"DUAL","steps":[{"type":"return","fields":{"ok":"yes"}}]}'
    folder_status, folder_body = api("POST", "/api/v1/objects", admin_token, {
        "parentPath": "root.platform",
        "name": "tck-sign",
        "type": "CUSTOM",
        "displayName": "TCK signature",
    })
    if folder_status not in (200, 201, 409) and "already" not in json.dumps(folder_body):
        return {"pass": False, "step": "folder", "status": folder_status, "body": folder_body}
    status, created = api("PUT", "/api/v1/objects/by-path/functions", admin_token, {
        "name": "tckSign",
        "description": "solution-tck dual signature",
        "sourceType": "script",
        "sourceBody": source,
        "outputSchema": {
            "name": "tckSignOut",
            "fields": [{"name": "ok", "type": "STRING"}],
        },
        "inputSchema": {
            "name": "tckSignInput",
            "fields": [
                {"name": "signature", "type": "STRING"},
                {"name": "countersigner", "type": "STRING"},
                {"name": "countersignature", "type": "STRING"},
            ],
        },
    }, {"path": "root.platform.tck-sign"})
    if status not in (200, 201):
        return {"pass": False, "step": "function", "status": status, "body": created}
    stored = created.get("sourceBody") or source
    digest = hashlib.sha256(stored.encode()).hexdigest()
    message = f"v1\nDUAL\nadmin\ndeveloper\n{digest}\n".encode()
    sh(["openssl", "genpkey", "-algorithm", "ED25519", "-out", "/tmp/tck-admin.pem"])
    sh(["openssl", "genpkey", "-algorithm", "ED25519", "-out", "/tmp/tck-op.pem"])
    admin_b64 = base64.b64encode(_openssl_bytes(
        ["pkey", "-in", "/tmp/tck-admin.pem", "-pubout", "-outform", "DER"]
    )).decode()
    op_b64 = base64.b64encode(_openssl_bytes(
        ["pkey", "-in", "/tmp/tck-op.pem", "-pubout", "-outform", "DER"]
    )).decode()
    _enroll(admin_token, admin_b64)
    operator_token = _login_user("developer", "developer")
    _enroll(operator_token, op_b64)
    open("/tmp/tck-sign.bin", "wb").write(message)
    admin_sig = base64.b64encode(_openssl_bytes(
        ["pkeyutl", "-sign", "-inkey", "/tmp/tck-admin.pem", "-rawin", "-in", "/tmp/tck-sign.bin"]
    )).decode()
    op_sig = base64.b64encode(_openssl_bytes(
        ["pkeyutl", "-sign", "-inkey", "/tmp/tck-op.pem", "-rawin", "-in", "/tmp/tck-sign.bin"]
    )).decode()

    def invoke(row):
        return api("POST", "/api/v1/objects/by-path/functions/invoke", admin_token, {
            "rows": [row],
        }, {"path": "root.platform.tck-sign", "name": "tckSign"})

    forged_status, forged_body = invoke({
        "signature": "abc",
        "countersigner": "developer",
        "countersignature": "abc",
    })
    forged_rejected = forged_status >= 400 and any(
        word in json.dumps(forged_body).lower() for word in ("rejected", "verify", "signature")
    )
    same_status, same_body = invoke({
        "signature": admin_sig,
        "countersigner": "admin",
        "countersignature": admin_sig,
    })
    same_rejected = same_status >= 400
    ok_status, ok_body = invoke({
        "signature": admin_sig,
        "countersigner": "developer",
        "countersignature": op_sig,
    })
    accepted = ok_status == 200
    return {
        "pass": forged_rejected and same_rejected and accepted,
        "forgedStatus": forged_status,
        "sameSignerStatus": same_status,
        "acceptedStatus": ok_status,
        "accepted": ok_body if not accepted else {"ok": True},
        "forged": forged_body if not forged_rejected else {"rejected": True},
        "sameSigner": same_body if not same_rejected else {"rejected": True},
    }


def main():
    log("solution-tck gates")
    token = login(8081, time.time() + 60)
    login(8082, time.time() + 60)
    result = {}
    try:
        result["doubleSignature"] = double_signature(token)
    except Exception as ex:
        result["doubleSignature"] = {"pass": False, "error": str(ex)}
    log("signature " + json.dumps(result.get("doubleSignature")))
    try:
        setup(token)
        result["clickhouse"] = clickhouse_outage(token)
    except Exception as ex:
        result["clickhouse"] = {"pass": False, "error": str(ex)}
        sh(["docker", "start", "ispf-tck-clickhouse"], check=False)
    log("clickhouse " + json.dumps(result.get("clickhouse")))
    try:
        result["failover"] = failover(token)
    except Exception as ex:
        result["failover"] = {"pass": False, "error": str(ex)}
        sh(["docker", "start", "ispf-tck-1"], check=False)
        sh(["docker", "start", "ispf-tck-2"], check=False)
    log("failover " + json.dumps(result.get("failover")))
    for port in (8081, 8082):
        try:
            login(port, time.time() + 180)
        except Exception as ex:
            result.setdefault("nodes", {})[str(port)] = str(ex)
    token = login(8081, time.time() + 60)
    try:
        result["erp"] = erp_outage()
    except Exception as ex:
        result["erp"] = {"pass": False, "error": str(ex)}
    log("erp " + json.dumps(result.get("erp")))
    try:
        result["audit"] = audit_tamper(token)
    except Exception as ex:
        result["audit"] = {"pass": False, "error": str(ex)}
    log("audit " + json.dumps(result.get("audit")))
    try:
        result["throughput"] = historian_peak()
    except Exception as ex:
        result["throughput"] = {"pass": False, "error": str(ex)}
    log("throughput " + json.dumps(result.get("throughput")))
    gates = ("clickhouse", "failover", "erp", "audit", "throughput", "doubleSignature")
    result["green"] = all(isinstance(result.get(name), dict) and result[name].get("pass") is True for name in gates)
    with open(RESULT, "w", encoding="utf-8") as handle:
        json.dump(result, handle, indent=2)
    print(json.dumps(result))
    if not result["green"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
