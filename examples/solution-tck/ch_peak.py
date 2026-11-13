#!/usr/bin/env python3
"""Replay the lab historian peak on the private TCK ClickHouse.

Same stress profile as docs/en/lab-mqtt-historian-stress.md: ClickHouse is the
historian store, ingress coalesce is off, minIntervalMs is 0, 16 TELEMETRY_ONLY
MQTT devices, numeric payloads. Does not touch the production server, database,
or ClickHouse.
"""

import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

ENV_FILE = "/home/iot-solutions/ispf-tck/node.env"
JAR = "/home/iot-solutions/ispf-tck/ispf-server.jar"
BASE = "http://127.0.0.1:8081"
PREFIX = "ispf/tck/"
DEVICES = 16
TARGET = int(os.environ.get("CH_PEAK_TARGET", "500000"))
DURATION = int(os.environ.get("CH_PEAK_SECONDS", "20"))

STRESS = """
ISPF_EVENT_JOURNAL_ENABLED=false
ISPF_OBJECT_CHANGE_ASYNC_ENABLED=false
ISPF_RUNTIME_TELEMETRY_COALESCE_ENABLED=false
ISPF_RUNTIME_TELEMETRY_INGRESS_QUEUE_ENABLED=true
ISPF_RUNTIME_TELEMETRY_INGRESS_QUEUE_COALESCE=false
ISPF_RUNTIME_TELEMETRY_FAST_HISTORIAN_PATH=true
ISPF_MQTT_GATEWAY_INGRESS_DISPATCH_COALESCE=false
ISPF_MQTT_GATEWAY_INGRESS_BYPASS_L3=true
ISPF_DRIVER_MQTT_INGRESS_COALESCE=false
ISPF_DRIVER_MQTT_CALLBACK_ELASTIC_ENABLED=false
ISPF_DRIVER_MQTT_CALLBACK_THREADS=32
ISPF_DRIVER_MQTT_CALLBACK_THREADS_MAX=32
ISPF_DRIVER_MQTT_CALLBACK_QUEUE_CAPACITY=500000
ISPF_VARIABLE_HISTORY_OVERFLOW_COALESCE=false
ISPF_VARIABLE_HISTORY_BENCHMARK_SPREAD_SAMPLED_AT=false
ISPF_VARIABLE_HISTORY_WRITER_THREADS=32
ISPF_VARIABLE_HISTORY_QUEUE_CAPACITY=8000000
ISPF_VARIABLE_HISTORY_BATCH_SIZE=5000
ISPF_VARIABLE_HISTORY_FLUSH_INTERVAL_MS=10
ISPF_VARIABLE_HISTORY_WRITER_THREADS_MIN=8
ISPF_VARIABLE_HISTORY_WRITER_THREADS_MAX=48
ISPF_VARIABLE_HISTORY_ELASTIC_WRITER_ENABLED=true
ISPF_VARIABLE_HISTORY_CLICKHOUSE_TABLE=variable_samples
"""


def log(message):
    print(message, flush=True)


def sh(command):
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode != 0:
        raise SystemExit(f"{command} -> {result.returncode}\n{result.stderr[-800:]}")
    return result.stdout


def apply_stress_env():
    text = open(ENV_FILE, encoding="utf-8").read()
    replacements = {
        "ISPF_VARIABLE_HISTORY_STORE=jdbc": "ISPF_VARIABLE_HISTORY_STORE=clickhouse",
        "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true": "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false",
        "JAVA_TOOL_OPTIONS=-Xms1g -Xmx4g": "JAVA_TOOL_OPTIONS=-Xms8g -Xmx16g -XX:+UseG1GC -XX:MaxGCPauseMillis=100",
    }
    for old, new in replacements.items():
        if old not in text and new not in text:
            raise SystemExit(f"node.env is missing {old}")
        text = text.replace(old, new)
    if "ISPF_RUNTIME_TELEMETRY_COALESCE_ENABLED" not in text:
        text = text.rstrip() + "\n" + STRESS.strip() + "\n"
    with open(ENV_FILE, "w", encoding="utf-8") as handle:
        handle.write(text)
    os.chmod(ENV_FILE, 0o600)


def restart_writer():
    mem = open("/proc/meminfo", encoding="utf-8").read()
    for line in mem.splitlines():
        if line.startswith("MemAvailable:"):
            log(line.strip())
    sh(["docker", "stop", "ispf-tck-2"])
    sh(["docker", "update", "--memory", "12g", "--memory-swap", "12g", "ispf-tck-clickhouse"])
    apply_stress_env()
    subprocess.run(["docker", "rm", "-f", "ispf-tck-1"], text=True, capture_output=True)
    sh([
        "docker", "run", "-d", "--name", "ispf-tck-1", "--network", "ispf-tck",
        "--add-host", "host.docker.internal:host-gateway",
        "-p", "127.0.0.1:8081:8080",
        "-v", f"{JAR}:/opt/ispf/ispf-server.jar:ro",
        "-v", "/home/iot-solutions/ispf-tck/drivers:/opt/ispf/drivers:ro",
        "--env-file", ENV_FILE,
        "-e", "ISPF_REPLICA_ID=replica-1",
        "-e", "ISPF_APPLICATION_OUTBOX_HTTP_ENDPOINTS=sap=http://host.docker.internal:8090/inbox",
        "eclipse-temurin:25-jre-jammy",
        "java", "-jar", "/opt/ispf/ispf-server.jar",
    ])
    log("started ispf-tck-1 on ClickHouse primary")


def http(method, path, body=None, token=None, query=None, timeout=60):
    url = BASE + path
    if query:
        url += "?" + "&".join(f"{k}={urllib.parse.quote(str(v))}" for k, v in query.items())
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


def login(deadline):
    last = None
    while time.time() < deadline:
        try:
            status, body = http("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})
            if status == 200 and body.get("token"):
                return body["token"]
            last = (status, body)
        except Exception as ex:
            last = str(ex)
        time.sleep(2)
    raise SystemExit(f"login failed {last}")


def wait_info():
    deadline = time.time() + 180
    last = None
    while time.time() < deadline:
        try:
            status, body = http("GET", "/api/v1/info")
            if status == 200 and body.get("version"):
                log(f"version {body.get('version')}")
                return
            last = (status, body)
        except Exception as ex:
            last = str(ex)
        time.sleep(2)
    raise SystemExit(f"info failed {last}")


def history(token):
    status, body = http("GET", "/api/v1/platform/metrics", token=token)
    if status != 200:
        raise SystemExit(f"metrics {status} {body}")
    for section in body.get("sections", []):
        if section.get("id") == "variableHistory":
            return section.get("values") or {}
    return {}


def ch_count():
    result = subprocess.run([
        "docker", "exec", "ispf-tck-clickhouse",
        "clickhouse-client", "--user", "default", "--password", "tck",
        "--query",
        "SELECT count() FROM default.variable_samples "
        "WHERE object_path LIKE 'root.platform.devices.tckm%'",
    ], text=True, capture_output=True)
    text = (result.stdout or "").strip()
    if result.returncode != 0:
        return {"error": (result.stderr or "")[-300:]}
    return int(text) if text.isdigit() else text


def arm(token):
    for index in range(1, DEVICES + 1):
        name = f"tckm{index:02d}"
        path = f"root.platform.devices.{name}"
        topic = f"{index:05d}/temperature"
        status, body = http("POST", "/api/v1/objects", {
            "parentPath": "root.platform.devices",
            "name": name,
            "type": "DEVICE",
            "displayName": name,
        }, token)
        if status not in (200, 409) and "already exists" not in json.dumps(body):
            raise SystemExit(f"device {name} {status} {body}")
        status, body = http("POST", "/api/v1/objects/by-path/variables", {
            "name": "temperature",
            "schema": {"name": "temperature", "fields": [{"name": "value", "type": "DOUBLE"}]},
            "readable": True,
            "writable": True,
            "historyEnabled": True,
        }, token, {"path": path})
        if status not in (200, 409) and "already exists" not in json.dumps(body):
            raise SystemExit(f"variable {name} {status} {body}")
        http("PATCH", "/api/v1/objects/by-path/variables/history", {
            "historyEnabled": True,
            "historyRetentionDays": 1,
            "historySampleMode": "ALL_VALUES",
            "telemetryPublishMode": "TELEMETRY_ONLY",
        }, token, {"path": path, "name": "temperature"})
        configuration = {
            "brokerUrl": "tcp://ispf-tck-mqtt:1883",
            "topicPrefix": PREFIX,
            "eventToVariable": "false",
            "ingressCoalesceEnabled": "false",
            "telemetryPublishMode": "TELEMETRY_ONLY",
            "callbackThreads": "32",
            "callbackThreadsMin": "32",
            "callbackThreadsMax": "32",
            "callbackElasticEnabled": "false",
            "callbackQueueCapacity": "500000",
        }
        status, body = http("PUT", "/api/v1/drivers/runtime/configure", {
            "driverId": "mqtt",
            "pollIntervalMs": 1000,
            "configuration": configuration,
            "pointMappings": {"temperature": topic},
            "autoStart": True,
            "telemetryPublishMode": "TELEMETRY_ONLY",
        }, token, {"devicePath": path})
        if status not in (200, 409):
            raise SystemExit(f"configure {name} {status} {body}")
        if status == 409 and "owned by" not in json.dumps(body) and "already" not in json.dumps(body).lower() and "persist" not in json.dumps(body).lower():
            raise SystemExit(f"configure {name} {status} {body}")
        http("POST", "/api/v1/drivers/runtime/start", token=token, query={"devicePath": path})
        log(f"armed {name}")


def main():
    restart_writer()
    wait_info()
    token = login(time.time() + 90)
    before_metrics = history(token)
    log("store " + json.dumps({
        "store": before_metrics.get("store"),
        "minIntervalMs": before_metrics.get("minIntervalMs"),
        "flushedTotal": before_metrics.get("flushedTotal"),
    }))
    arm(token)
    time.sleep(5)
    flushed_before = int(history(token).get("flushedTotal") or 0)
    rows_before = ch_count()
    log(f"before flushed={flushed_before} rows={rows_before}")
    started = time.time()
    env = os.environ.copy()
    env["NUMERIC_PAYLOAD"] = "true"
    env["EMQTT_CPU_LIMIT"] = "4.0"
    proc = subprocess.run([
        "bash", "/home/iot-solutions/ispf/loadtest/mqtt-emqtt-bench.sh",
        "--host", "127.0.0.1", "--port", "1883",
        "--devices", str(DEVICES),
        "--messages-per-second", str(TARGET),
        "--duration-seconds", str(DURATION),
        "--interval-ms", "1",
        "--topic-prefix", "ispf/tck",
        "--single-container",
    ], text=True, capture_output=True, env=env)
    publish_seconds = time.time() - started
    log((proc.stdout or "")[-1200:])
    log((proc.stderr or "")[-800:])
    deadline = time.time() + 90
    queued = None
    flushed_after = flushed_before
    while time.time() < deadline:
        values = history(token)
        flushed_after = int(values.get("flushedTotal") or 0)
        queued = values.get("queueSize")
        if queued in (0, "0", None) and flushed_after > flushed_before:
            break
        time.sleep(2)
    rows_after = ch_count()
    gained_rows = None
    if isinstance(rows_before, int) and isinstance(rows_after, int):
        gained_rows = rows_after - rows_before
    window = max(publish_seconds, 0.001)
    result = {
        "benchExit": proc.returncode,
        "targetPerSecond": TARGET,
        "publishSeconds": round(publish_seconds, 1),
        "flushedBefore": flushed_before,
        "flushedAfter": flushed_after,
        "flushed": flushed_after - flushed_before,
        "flushedPerSecond": round((flushed_after - flushed_before) / window, 1),
        "rowsBefore": rows_before,
        "rowsAfter": rows_after,
        "rows": gained_rows,
        "rowsPerSecond": None if gained_rows is None else round(gained_rows / window, 1),
        "queueSize": queued,
        "store": history(token).get("store"),
        "minIntervalMs": history(token).get("minIntervalMs"),
    }
    log(json.dumps(result))
    with open("/home/iot-solutions/ispf-tck/peak.json", "w", encoding="utf-8") as handle:
        json.dump(result, handle)
    if (flushed_after - flushed_before) < 1000:
        logs = subprocess.run(["docker", "logs", "--tail", "80", "ispf-tck-1"], text=True, capture_output=True)
        interesting = [
            line for line in (logs.stdout + logs.stderr).splitlines()
            if any(word in line for word in ("ClickHouse", "ERROR", "Exception", "historian", "MQTT"))
        ]
        log("\n".join(interesting[-30:]))


if __name__ == "__main__":
    main()
