#!/usr/bin/env python3
"""Sole-store ClickHouse historian certification on the private TCK stand.

One configuration: store=clickhouse plus the disk spool. The rate window and the
600s outage are the same publisher run. Results come only from this process.
Production containers are not stopped.

Pass requires saved rows >= 100000/s, durable-accept p99 <= 50 ms, spool and
queue empty after catch-up, loss=0, duplicate=0, a kill -9 replay, a year
rollup p99 <= 50 ms, and a write/read while one operator-managed replica is down.
"""

import json
import math
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = "/home/iot-solutions/ispf-tck"
ENV_FILE = ROOT + "/node.env"
JAR = ROOT + "/ispf-server.jar"
RESULT = ROOT + "/historian-cert.json"
SPOOL_DIR = ROOT + "/spool"
BASE = "http://127.0.0.1:8081"
PREFIX = "ispf/tck/"
DEVICES = 16
TARGET = int(os.environ.get("CH_PEAK_TARGET", "100000"))
HEALTHY_SECONDS = int(os.environ.get("HISTORIAN_CERT_HEALTHY_SECONDS", "60"))
OUTAGE_SECONDS = int(os.environ.get("SOLUTION_TCK_CH_OUTAGE_SECONDS", "600"))
RATE_MIN = 100_000
ACCEPT_P99_MAX_MS = 50
ROLLUP_P99_MAX_MS = 50
YEAR_ROWS = 365 * 24 * 60
ALLOWLIST = {
    "ispf-tck-1",
    "ispf-tck-2",
    "ispf-tck-clickhouse",
    "ispf-tck-clickhouse-b",
    "ispf-tck-keeper",
}

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
ISPF_VARIABLE_HISTORY_BENCHMARK_SPREAD_SAMPLED_AT=true
ISPF_PLATFORM_METRICS_PROBE_ENABLED=false
ISPF_VARIABLE_HISTORY_RECORD_SNAPSHOT_ENABLED=false
ISPF_VARIABLE_HISTORY_WRITER_THREADS=8
ISPF_VARIABLE_HISTORY_QUEUE_CAPACITY=8000000
ISPF_VARIABLE_HISTORY_BATCH_SIZE=5000
ISPF_VARIABLE_HISTORY_FLUSH_INTERVAL_MS=10
ISPF_VARIABLE_HISTORY_WRITER_THREADS_MIN=8
ISPF_VARIABLE_HISTORY_WRITER_THREADS_MAX=48
ISPF_VARIABLE_HISTORY_ELASTIC_WRITER_ENABLED=false
ISPF_VARIABLE_HISTORY_CLICKHOUSE_TABLE=variable_samples
ISPF_VARIABLE_HISTORY_SPOOL_ENABLED=true
ISPF_VARIABLE_HISTORY_SPOOL_DIRECTORY=/data/historian-spool
ISPF_VARIABLE_HISTORY_SPOOL_MAX_BYTES=34359738368
ISPF_VARIABLE_HISTORY_SPOOL_FSYNC=true
ISPF_VARIABLE_HISTORY_SPOOL_REPLAY_BATCH=32
ISPF_VARIABLE_HISTORY_SPOOL_REPLAY_INTERVAL_MS=20
"""


def is_zero(value):
    return value in (0, "0")


def log(message):
    print(message, flush=True)


def guard(name):
    if name not in ALLOWLIST:
        raise SystemExit(f"refusing to control {name}")


def sh(args, check=True, input_text=None):
    if args and args[0] == "docker" and len(args) > 1 and args[1] in ("stop", "rm", "kill", "start", "restart"):
        for name in args[2:]:
            if not name.startswith("-"):
                guard(name)
    proc = subprocess.run(args, input=input_text, text=True, capture_output=True)
    if check and proc.returncode != 0:
        raise RuntimeError((proc.stderr or proc.stdout or "")[-2000:])
    return proc


def http(method, path, body=None, token=None, query=None, timeout=60):
    url = BASE + path
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
    raise RuntimeError(f"login failed {last}")


def wait_info(deadline):
    last = None
    while time.time() < deadline:
        try:
            status, body = http("GET", "/api/v1/info", timeout=5)
            if status == 200 and body.get("version"):
                return body.get("version")
            last = (status, body)
        except Exception as ex:
            last = str(ex)
        time.sleep(2)
    raise RuntimeError(f"info failed {last}")


def history(token):
    status, body = http("GET", "/api/v1/platform/metrics", token=token)
    if status != 200:
        raise RuntimeError(f"metrics {status} {body}")
    for section in body.get("sections", []):
        if section.get("id") == "variableHistory":
            return section.get("values") or {}
    return {}


def ch(query):
    proc = sh(
        ["docker", "exec", "ispf-tck-clickhouse", "clickhouse-client",
         "--user", "default", "--password", "tck", "--query", query],
        check=False,
    )
    if proc.returncode != 0:
        text = (proc.stderr or "") + (proc.stdout or "")
        return {"error": text[:600]}
    return (proc.stdout or "").strip()


def ch_int(query):
    text = ch(query)
    if isinstance(text, dict):
        return text
    return int(text) if text.isdigit() else text


def saved_rows():
    return ch_int(
        "SELECT count() FROM default.variable_samples "
        "WHERE object_path LIKE 'root.platform.devices.tckm%'"
    )


def duplicate_rows():
    return ch_int(
        "SELECT count() - uniqExact(object_path, variable_name, field_name, sampled_at) "
        "FROM default.variable_samples "
        "WHERE object_path LIKE 'root.platform.devices.tckm%'"
    )


def set_env_key(text, key, value):
    lines = []
    found = False
    for line in text.splitlines():
        if line.startswith(key + "="):
            lines.append(f"{key}={value}")
            found = True
        else:
            lines.append(line)
    if not found:
        lines.append(f"{key}={value}")
    return "\n".join(lines) + "\n"


def apply_spool_env():
    text = open(ENV_FILE, encoding="utf-8").read()
    text = set_env_key(text, "ISPF_VARIABLE_HISTORY_STORE", "clickhouse")
    text = set_env_key(text, "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED", "false")
    text = set_env_key(text, "ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS", "0")
    text = set_env_key(text, "JAVA_TOOL_OPTIONS", "-Xms8g -Xmx16g -XX:+UseG1GC -XX:MaxGCPauseMillis=100")
    for line in STRESS.strip().splitlines():
        key, value = line.split("=", 1)
        text = set_env_key(text, key, value)
    with open(ENV_FILE, "w", encoding="utf-8") as handle:
        handle.write(text)
    os.chmod(ENV_FILE, 0o600)


def start_writer():
    sh(["docker", "stop", "ispf-tck-2"], check=False)
    sh(["docker", "rm", "-f", "ispf-tck-1"], check=False)
    os.makedirs(SPOOL_DIR, exist_ok=True)
    for name in os.listdir(SPOOL_DIR):
        path = os.path.join(SPOOL_DIR, name)
        if os.path.isfile(path):
            os.remove(path)
    sh(["docker", "update", "--memory", "12g", "--memory-swap", "12g", "ispf-tck-clickhouse"], check=False)
    apply_spool_env()
    sh([
        "docker", "run", "-d", "--name", "ispf-tck-1", "--network", "ispf-tck",
        "--add-host", "host.docker.internal:host-gateway",
        "-p", "127.0.0.1:8081:8080",
        "-v", f"{JAR}:/opt/ispf/ispf-server.jar:ro",
        "-v", f"{os.environ.get('ISPF_TCK_DRIVERS_DIR', '/home/iot-solutions/ispf-cluster/drivers')}:/opt/ispf/drivers:ro",
        "-v", f"{SPOOL_DIR}:/data/historian-spool",
        "--env-file", ENV_FILE,
        "-e", "ISPF_REPLICA_ID=replica-1",
        "eclipse-temurin:25-jre-jammy",
        "java", "-jar", "/opt/ispf/ispf-server.jar",
    ])


def wait_clickhouse(deadline):
    while time.time() < deadline:
        probe = ch("SELECT 1")
        if probe == "1":
            return
        time.sleep(2)
    raise RuntimeError("private ClickHouse did not answer")


def wait_keeper(deadline):
    query = "SELECT count() FROM system.zookeeper WHERE path = '/'"
    last = ""
    while time.time() < deadline:
        probe = sh(
            ["docker", "exec", "ispf-tck-clickhouse", "clickhouse-client",
             "--user", "default", "--password", "tck", "--query", query],
            check=False,
        )
        if probe.returncode == 0:
            return
        last = ((probe.stderr or "") + (probe.stdout or ""))[:400]
        time.sleep(2)
    logs = sh(["docker", "logs", "--tail", "40", "ispf-tck-keeper"], check=False)
    text = (logs.stdout or "") + (logs.stderr or "")
    raise RuntimeError("keeper not ready: " + last + " | " + text[-600:])


def write_text(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)


def start_single_clickhouse():
    sh(["docker", "rm", "-f", "ispf-tck-clickhouse"], check=False)
    sh([
        "docker", "run", "-d", "--name", "ispf-tck-clickhouse", "--network", "ispf-tck",
        "-p", "127.0.0.1:8124:8123", "--memory", "12g", "--memory-swap", "12g",
        "-e", "CLICKHOUSE_USER=default",
        "-e", "CLICKHOUSE_PASSWORD=tck",
        "-e", "CLICKHOUSE_DB=default",
        "clickhouse/clickhouse-server:24.8",
    ])
    wait_clickhouse(time.time() + 90)


def setup_replicas():
    """Operator-managed keeper and two replicas. ISPF only uses the stable A endpoint."""
    keeper = """<clickhouse>
  <logger><level>information</level><console>true</console></logger>
  <listen_host>0.0.0.0</listen_host>
  <keeper_server>
    <tcp_port>9181</tcp_port>
    <server_id>1</server_id>
    <log_storage_path>/var/lib/clickhouse/coordination/log</log_storage_path>
    <snapshot_storage_path>/var/lib/clickhouse/coordination/snapshots</snapshot_storage_path>
    <raft_configuration>
      <server><id>1</id><hostname>ispf-tck-keeper</hostname><port>9234</port></server>
    </raft_configuration>
  </keeper_server>
</clickhouse>
"""
    def replica_xml(name, host):
        return f"""<clickhouse>
  <listen_host>0.0.0.0</listen_host>
  <macros><shard>1</shard><replica>{name}</replica></macros>
  <interserver_http_host>{host}</interserver_http_host>
  <zookeeper><node><host>ispf-tck-keeper</host><port>9181</port></node></zookeeper>
</clickhouse>
"""
    write_text(ROOT + "/keeper/keeper.xml", keeper)
    write_text(ROOT + "/ch-a/config.d/replica.xml", replica_xml("a", "ispf-tck-clickhouse"))
    write_text(ROOT + "/ch-b/config.d/replica.xml", replica_xml("b", "ispf-tck-clickhouse-b"))
    sh(["docker", "rm", "-f", "ispf-tck-keeper", "ispf-tck-clickhouse", "ispf-tck-clickhouse-b"], check=False)
    sh([
        "docker", "run", "-d", "--name", "ispf-tck-keeper", "--network", "ispf-tck",
        "--memory", "1g",
        "--hostname", "ispf-tck-keeper",
        "-v", ROOT + "/keeper/keeper.xml:/etc/clickhouse-keeper/keeper.xml:ro",
        "clickhouse/clickhouse-server:24.8",
        "clickhouse-keeper", "--config-file=/etc/clickhouse-keeper/keeper.xml",
    ])
    for name, config, port in (
            ("ispf-tck-clickhouse", ROOT + "/ch-a/config.d", ["-p", "127.0.0.1:8124:8123"]),
            ("ispf-tck-clickhouse-b", ROOT + "/ch-b/config.d", []),
    ):
        sh([
            "docker", "run", "-d", "--name", name, "--hostname", name, "--network", "ispf-tck",
            "--memory", "8g", "--memory-swap", "8g",
            *port,
            "-e", "CLICKHOUSE_USER=default",
            "-e", "CLICKHOUSE_PASSWORD=tck",
            "-e", "CLICKHOUSE_DB=default",
            "-v", f"{config}:/etc/clickhouse-server/config.d:ro",
            "clickhouse/clickhouse-server:24.8",
        ])
    wait_clickhouse(time.time() + 120)
    wait_keeper(time.time() + 90)
    ddl = """
CREATE TABLE IF NOT EXISTS default.variable_samples (
    object_path String,
    variable_name String,
    field_name String,
    sampled_at DateTime64(3, 'UTC'),
    observed_at DateTime64(3, 'UTC'),
    value_double Nullable(Float64),
    value_text Nullable(String),
    quality Nullable(String)
) ENGINE = ReplicatedMergeTree('/clickhouse/tables/1/variable_samples', '{replica}')
PARTITION BY toYYYYMM(sampled_at)
ORDER BY (object_path, variable_name, field_name, sampled_at)
SETTINGS index_granularity = 8192, replicated_deduplication_window = 10000
"""
    created = ch(ddl)
    if isinstance(created, dict):
        raise RuntimeError(created["error"])
    created_b = sh(
        ["docker", "exec", "ispf-tck-clickhouse-b", "clickhouse-client",
         "--user", "default", "--password", "tck", "--query", ddl],
        check=False,
    )
    if created_b.returncode != 0:
        raise RuntimeError(((created_b.stderr or "") + (created_b.stdout or ""))[:600])


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
            raise RuntimeError(f"device {name} {status} {body}")
        status, body = http("POST", "/api/v1/objects/by-path/variables", {
            "name": "temperature",
            "schema": {"name": "temperature", "fields": [{"name": "value", "type": "DOUBLE"}]},
            "readable": True,
            "writable": True,
            "historyEnabled": True,
        }, token, {"path": path})
        if status not in (200, 409) and "already exists" not in json.dumps(body):
            raise RuntimeError(f"variable {name} {status} {body}")
        http("PATCH", "/api/v1/objects/by-path/variables/history", {
            "historyEnabled": True,
            "historyRetentionDays": 7,
            "historySampleMode": "ALL_VALUES",
            "telemetryPublishMode": "TELEMETRY_ONLY",
        }, token, {"path": path, "name": "temperature"})
        status, body = http("PUT", "/api/v1/drivers/runtime/configure", {
            "driverId": "mqtt",
            "pollIntervalMs": 1000,
            "configuration": {
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
            },
            "pointMappings": {"temperature": topic},
            "autoStart": True,
            "telemetryPublishMode": "TELEMETRY_ONLY",
        }, token, {"devicePath": path})
        if status not in (200, 409):
            raise RuntimeError(f"configure {name} {status} {body}")
        http("POST", "/api/v1/drivers/runtime/start", token=token, query={"devicePath": path})
        log(f"armed {name}")


def start_bench(duration):
    env = os.environ.copy()
    env["NUMERIC_PAYLOAD"] = "true"
    env["EMQTT_CPU_LIMIT"] = "4.0"
    handle = open(ROOT + "/historian-bench.log", "w", encoding="utf-8")
    return subprocess.Popen(
        ["bash", "/home/iot-solutions/ispf/loadtest/mqtt-emqtt-bench.sh",
         "--host", "127.0.0.1", "--port", "1883",
         "--devices", str(DEVICES),
         "--messages-per-second", str(TARGET),
         "--duration-seconds", str(duration),
         "--interval-ms", "1",
         "--topic-prefix", "ispf/tck",
         "--single-container"],
        text=True, stdout=handle, stderr=subprocess.STDOUT, env=env,
    )


def wait_caught_up(token, deadline):
    last = {}
    while time.time() < deadline:
        values = history(token)
        last = values
        lag = int(values.get("spoolLag") or 0)
        queued = values.get("queueSize")
        if lag == 0 and queued in (0, "0", None) and values.get("spoolQuota") is not True:
            return values
        time.sleep(2)
    return last


def load_and_outage(token):
    duration = HEALTHY_SECONDS + OUTAGE_SECONDS + 30
    before_rows = saved_rows()
    before_flushed = int(history(token).get("flushedTotal") or 0)
    bench = start_bench(duration)
    time.sleep(HEALTHY_SECONDS)
    mid_rows = saved_rows()
    mid_metrics = history(token)
    gained = None
    if isinstance(before_rows, int) and isinstance(mid_rows, int):
        gained = mid_rows - before_rows
    rate = None if gained is None else round(gained / HEALTHY_SECONDS, 1)
    mid_flushed = int(mid_metrics.get("flushedTotal") or 0)
    accepted_rate = round(max(0, mid_flushed - before_flushed) / HEALTHY_SECONDS, 1)
    log(f"healthy window rows/s={rate} accepted/s={accepted_rate}")
    log("stopping private ClickHouse")
    sh(["docker", "stop", "ispf-tck-clickhouse"])
    time.sleep(OUTAGE_SECONDS)
    sh(["docker", "start", "ispf-tck-clickhouse"])
    wait_clickhouse(time.time() + 120)
    bench.wait(timeout=duration + 60)
    token = login(time.time() + 90)
    caught = wait_caught_up(token, time.time() + 1800)
    after_rows = saved_rows()
    after_flushed = int(caught.get("flushedTotal") or 0)
    duplicates = duplicate_rows()
    accepted = after_flushed - before_flushed
    stored = None
    if isinstance(before_rows, int) and isinstance(after_rows, int):
        stored = after_rows - before_rows
    loss = None
    if stored is not None and isinstance(duplicates, int):
        loss = accepted - (stored - duplicates)
    accept_p99 = mid_metrics.get("spoolAcceptP99Ms")
    accept_p99_ms = int(accept_p99) if accept_p99 not in (None, "") else None
    peak = {
        "pass": rate is not None and rate >= RATE_MIN
        and accept_p99_ms is not None and accept_p99_ms <= ACCEPT_P99_MAX_MS
        and mid_metrics.get("store") == "clickhouse"
        and mid_metrics.get("spoolEnabled") is True,
        "healthySeconds": HEALTHY_SECONDS,
        "rowsPerSecond": rate,
        "acceptedPerSecond": accepted_rate,
        "rows": gained,
        "acceptP99Ms": mid_metrics.get("spoolAcceptP99Ms"),
        "store": mid_metrics.get("store"),
        "spoolEnabled": mid_metrics.get("spoolEnabled"),
        "targetPerSecond": TARGET,
        "sampledAt": "monotonic +1ms per series so each saved row has its own identity",
    }
    outage = {
        "pass": loss == 0 and duplicates == 0
        and is_zero(caught.get("spoolLag"))
        and is_zero(caught.get("queueSize"))
        and caught.get("spoolQuota") is not True
        and bench.returncode == 0
        and after_flushed > mid_flushed,
        "outageSeconds": OUTAGE_SECONDS,
        "accepted": accepted,
        "stored": stored,
        "loss": loss,
        "duplicates": duplicates,
        "spoolLag": caught.get("spoolLag"),
        "spoolBytes": caught.get("spoolBytes"),
        "queueSize": caught.get("queueSize"),
        "benchExit": bench.returncode,
    }
    return peak, outage


def ensure_level(token):
    status, body = http("POST", "/api/v1/objects", {
        "parentPath": "root.platform.devices",
        "name": "tck-field",
        "type": "DEVICE",
        "displayName": "solution-tck field",
    }, token)
    if status not in (200, 409) and "already exists" not in json.dumps(body):
        raise RuntimeError(f"create device {status} {body}")
    status, body = http("POST", "/api/v1/objects/by-path/variables", {
        "name": "level",
        "schema": {"name": "level", "fields": [{"name": "value", "type": "DOUBLE"}]},
        "readable": True,
        "writable": True,
        "historyEnabled": True,
    }, token, {"path": "root.platform.devices.tck-field"})
    if status not in (200, 409) and "already exists" not in json.dumps(body):
        raise RuntimeError(f"create variable {status} {body}")
    http("PATCH", "/api/v1/objects/by-path/variables/history", {
        "historyEnabled": True,
        "historyRetentionDays": 7,
        "historySampleMode": "ALL_VALUES",
    }, token, {"path": "root.platform.devices.tck-field", "name": "level"})


def crash_replay(token):
    ensure_level(token)
    count = 40
    sh(["docker", "stop", "ispf-tck-clickhouse"])
    flushed_before = int(history(token).get("flushedTotal") or 0)
    lag_before = int(history(token).get("spoolLag") or 0)
    for index in range(count):
        status, body = http(
            "PUT", "/api/v1/objects/by-path/variables",
            {"schema": {"name": "level", "fields": [{"name": "value", "type": "DOUBLE"}]},
             "rows": [{"value": 1000 + index}]},
            token,
            {"path": "root.platform.devices.tck-field", "name": "level"},
        )
        if status != 200:
            raise RuntimeError(f"write {index} {status} {body}")
    deadline = time.time() + 60
    while time.time() < deadline:
        values = history(token)
        flushed = int(values.get("flushedTotal") or 0)
        if (flushed - flushed_before >= count
                and int(values.get("spoolLag") or 0) - lag_before >= count
                and is_zero(values.get("queueSize"))):
            break
        time.sleep(0.5)
    else:
        return {"pass": False, "reason": "samples were not durable before kill"}
    sh(["docker", "kill", "--signal=KILL", "ispf-tck-1"])
    sh(["docker", "start", "ispf-tck-clickhouse"])
    wait_clickhouse(time.time() + 120)
    sh(["docker", "start", "ispf-tck-1"])
    wait_info(time.time() + 180)
    token = login(time.time() + 90)
    caught = wait_caught_up(token, time.time() + 600)
    found = ch_int(
        "SELECT count() FROM default.variable_samples "
        "WHERE object_path = 'root.platform.devices.tck-field' "
        "AND variable_name = 'level' AND value_double >= 1000 AND value_double < 1040"
    )
    unique = ch_int(
        "SELECT uniqExact(value_double) FROM default.variable_samples "
        "WHERE object_path = 'root.platform.devices.tck-field' "
        "AND variable_name = 'level' AND value_double >= 1000 AND value_double < 1040"
    )
    return {
        "pass": found == count and unique == count and is_zero(caught.get("spoolLag")),
        "expected": count,
        "found": found,
        "unique": unique,
        "spoolLag": caught.get("spoolLag"),
        "loss": None if not isinstance(found, int) else count - found,
        "duplicates": None if not isinstance(found, int) or not isinstance(unique, int) else found - unique,
    }


def ensure_year_series(token):
    status, body = http("POST", "/api/v1/objects", {
        "parentPath": "root.platform.devices",
        "name": "tck-year",
        "type": "DEVICE",
        "displayName": "year rollup series",
    }, token)
    if status not in (200, 409) and "already exists" not in json.dumps(body):
        raise RuntimeError(f"year device {status} {body}")
    status, body = http("POST", "/api/v1/objects/by-path/variables", {
        "name": "temperature",
        "schema": {"name": "temperature", "fields": [{"name": "value", "type": "DOUBLE"}]},
        "readable": True,
        "writable": True,
        "historyEnabled": True,
    }, token, {"path": "root.platform.devices.tck-year"})
    if status not in (200, 409) and "already exists" not in json.dumps(body):
        raise RuntimeError(f"year variable {status} {body}")
    http("PATCH", "/api/v1/objects/by-path/variables/history", {
        "historyEnabled": True,
        "historyRetentionDays": 400,
        "historySampleMode": "ALL_VALUES",
    }, token, {"path": "root.platform.devices.tck-year", "name": "temperature"})


def year_rollup(token):
    ensure_year_series(token)
    insert = (
        "INSERT INTO default.variable_samples "
        "SELECT 'root.platform.devices.tck-year', 'temperature', 'value', "
        "toDateTime64('2025-01-01 00:00:00', 3, 'UTC') + toIntervalMinute(number), "
        "toDateTime64('2025-01-01 00:00:00', 3, 'UTC') + toIntervalMinute(number), "
        "toFloat64(number % 100), NULL, 'GOOD' "
        f"FROM numbers({YEAR_ROWS})"
    )
    existing = ch_int(
        "SELECT count() FROM default.variable_samples "
        "WHERE object_path = 'root.platform.devices.tck-year'"
    )
    if existing != 0:
        log(f"year series already has {existing} rows")
    else:
        inserted = ch(insert)
        if isinstance(inserted, dict):
            return {"pass": False, "reason": inserted["error"], "rows": YEAR_ROWS}
    samples = []
    query = {
        "path": "root.platform.devices.tck-year",
        "name": "temperature",
        "field": "value",
        "bucket": "1d",
        "from": "2025-01-01T00:00:00Z",
        "to": "2025-12-31T23:59:59Z",
        "limit": "400",
    }
    for _ in range(30):
        started = time.perf_counter()
        status, body = http(
            "GET", "/api/v1/objects/by-path/variables/history/aggregate",
            token=token, query=query, timeout=30,
        )
        samples.append((time.perf_counter() - started) * 1000)
        if status != 200:
            return {"pass": False, "status": status, "body": body, "rows": YEAR_ROWS}
    samples.sort()
    p99 = samples[min(len(samples) - 1, max(0, math.ceil(len(samples) * 0.99) - 1))]
    buckets = len((body or {}).get("buckets") or (body or {}).get("points") or [])
    return {
        "pass": p99 <= ROLLUP_P99_MAX_MS and status == 200 and buckets >= 365,
        "rows": YEAR_ROWS,
        "bucket": "1d",
        "query": "/api/v1/objects/by-path/variables/history/aggregate bucket=1d year=2025",
        "samples": len(samples),
        "p99Ms": round(p99, 2),
        "p50Ms": round(samples[len(samples) // 2], 2),
        "buckets": buckets,
    }


def replica_failover(token):
    running = sh(["docker", "inspect", "-f", "{{.State.Running}}", "ispf-tck-clickhouse-b"], check=False)
    if running.returncode != 0 or (running.stdout or "").strip() != "true":
        return {"pass": False, "reason": "operator replica B is not running"}
    sh(["docker", "stop", "ispf-tck-clickhouse-b"])
    status, body = http(
        "PUT", "/api/v1/objects/by-path/variables",
        {"schema": {"name": "level", "fields": [{"name": "value", "type": "DOUBLE"}]},
         "rows": [{"value": 4242}]},
        token,
        {"path": "root.platform.devices.tck-field", "name": "level"},
    )
    deadline = time.time() + 30
    read_back = None
    while time.time() < deadline:
        read_status, read_body = http(
            "GET", "/api/v1/objects/by-path/variables/history",
            token=token,
            query={
                "path": "root.platform.devices.tck-field",
                "name": "level",
                "field": "value",
                "limit": "5",
            },
        )
        read_back = (read_status, read_body)
        if read_status == 200 and "4242" in json.dumps(read_body):
            break
        time.sleep(1)
    sh(["docker", "start", "ispf-tck-clickhouse-b"])
    deadline = time.time() + 60
    copied = ""
    while time.time() < deadline:
        on_b = sh(
            ["docker", "exec", "ispf-tck-clickhouse-b", "clickhouse-client",
             "--user", "default", "--password", "tck", "--query",
             "SELECT count() FROM default.variable_samples "
             "WHERE object_path = 'root.platform.devices.tck-field' AND value_double = 4242"],
            check=False,
        )
        copied = (on_b.stdout or "").strip()
        if on_b.returncode == 0 and copied.isdigit() and int(copied) >= 1:
            break
        time.sleep(1)
    return {
        "pass": status == 200 and read_back is not None and read_back[0] == 200 and copied.isdigit() and int(copied) >= 1,
        "writeStatus": status,
        "readStatus": None if read_back is None else read_back[0],
        "replicaBRows": copied,
        "stoppedReplica": "ispf-tck-clickhouse-b",
        "stableEndpoint": "http://ispf-tck-clickhouse:8123",
    }


def hardware():
    mem = open("/proc/meminfo", encoding="utf-8").read().splitlines()[:3]
    cpu = open("/proc/cpuinfo", encoding="utf-8").read()
    model = ""
    for line in cpu.splitlines():
        if line.startswith("model name"):
            model = line.split(":", 1)[1].strip()
            break
    return {"cpu": model, "meminfo": mem, "host": os.uname().nodename}


def save(result):
    with open(RESULT, "w", encoding="utf-8") as handle:
        json.dump(result, handle, indent=2)
    log(json.dumps(result))


def main():
    result = {
        "config": "clickhouse+spool",
        "gitSha": os.environ.get("ISPF_TCK_GIT_SHA"),
        "hardware": hardware(),
        "startedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    replicated = False
    try:
        sh(["docker", "stop", "ispf-tck-1", "ispf-tck-2"], check=False)
        try:
            setup_replicas()
            replicated = True
            result["clickhouseTopology"] = "replicated-merge-tree replica A endpoint, replica B, one keeper"
        except Exception as ex:
            log(f"replica setup failed, restoring the single private ClickHouse: {ex}")
            result["clickhouseTopology"] = "single MergeTree after replica setup failed"
            result["replicaSetupError"] = str(ex)[:800]
            sh(["docker", "rm", "-f", "ispf-tck-keeper", "ispf-tck-clickhouse-b"], check=False)
            start_single_clickhouse()
        start_writer()
        version = wait_info(time.time() + 180)
        result["version"] = version
        token = login(time.time() + 90)
        values = history(token)
        if values.get("store") != "clickhouse" or values.get("spoolEnabled") is not True:
            raise RuntimeError(f"writer is not on clickhouse+spool: {values}")
        arm(token)
        peak, outage = load_and_outage(token)
        result["peak"] = peak
        result["outage"] = outage
        save(result)
        result["crash"] = crash_replay(token)
        token = login(time.time() + 90)
        result["rollup"] = year_rollup(token)
        if replicated:
            result["replica"] = replica_failover(token)
        else:
            result["replica"] = {"pass": False, "reason": result.get("replicaSetupError", "replicas were not started")}
    except Exception as ex:
        result["error"] = str(ex)[-1500:]
        log(result["error"])
    phases = [result.get(name) or {"pass": False} for name in ("peak", "outage", "crash", "rollup", "replica")]
    result["pass"] = all(phase.get("pass") is True for phase in phases) and "error" not in result
    result["finishedAt"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    save(result)
    if not result["pass"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
