#!/usr/bin/env python3
"""Certification stand for one plant fork: two ISPF nodes, NATS, a private ClickHouse.

The production lab database and the production ClickHouse are not used.
Durations match the plan: ClickHouse stopped for 10 minutes, ERP receiver down for 30 minutes.
"""

import json
import os
import subprocess
import time
import urllib.error
import urllib.request

ROOT = "/home/iot-solutions/ispf-tck"
JAR = ROOT + "/ispf-server.jar"
ENV_FILE = ROOT + "/node.env"
RESULT = ROOT + "/result.json"
ERP_PORT = 8090
CH_OUTAGE_SECONDS = int(os.environ.get("SOLUTION_TCK_CH_OUTAGE_SECONDS", "600"))
ERP_OUTAGE_SECONDS = int(os.environ.get("SOLUTION_TCK_ERP_OUTAGE_SECONDS", "1800"))


def sh(args, check=True, input_text=None):
    proc = subprocess.run(args, input=input_text, text=True, capture_output=True)
    if check and proc.returncode != 0:
        raise RuntimeError((proc.stderr or proc.stdout or "").strip()[-2000:])
    return proc


def env_of(container, key):
    return sh(["docker", "exec", container, "printenv", key]).stdout.strip()


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
            parsed = {"raw": raw[:300]}
        return ex.code, parsed


def wait_clickhouse(deadline):
    while time.time() < deadline:
        probe = sh(
            ["docker", "exec", "ispf-tck-clickhouse", "clickhouse-client",
             "--user", "default", "--password", "tck", "--query", "SELECT 1"],
            check=False,
        )
        if probe.returncode == 0 and probe.stdout.strip() == "1":
            return
        time.sleep(2)
    raise RuntimeError("private ClickHouse did not accept the tck password")


def wait_info(port, deadline):
    while time.time() < deadline:
        try:
            status, body = http("GET", f"http://127.0.0.1:{port}/api/v1/info")
            if status == 200 and body.get("version"):
                return body
        except Exception:
            pass
        time.sleep(3)
    raise RuntimeError(f"node :{port} did not become ready")


def psql(sql):
    return sh(
        ["docker", "exec", "-i", "ispf-db-postgres-1", "psql", "-U", "ispf", "-d", "ispf_solution_tck", "-v", "ON_ERROR_STOP=1", "-tA"],
        input_text=sql,
    ).stdout.strip()


def log(message):
    print(message, flush=True)


def login(port, deadline):
    while time.time() < deadline:
        status, body = http(
            "POST",
            f"http://127.0.0.1:{port}/api/v1/auth/login",
            {"username": "admin", "password": "admin"},
        )
        if status == 200 and body.get("token"):
            return body["token"]
        time.sleep(2)
    raise RuntimeError(f"login on :{port} failed")


def main():
    os.makedirs(ROOT, exist_ok=True)
    pg_password = env_of("ispf-enterprise-l", "SPRING_DATASOURCE_PASSWORD")
    sh(["docker", "network", "create", "ispf-tck"], check=False)
    exists = sh(
        ["docker", "exec", "ispf-db-postgres-1", "psql", "-U", "ispf", "-d", "postgres", "-tA", "-c",
         "SELECT 1 FROM pg_database WHERE datname = 'ispf_solution_tck'"],
    ).stdout.strip()
    if exists != "1":
        sh(["docker", "exec", "ispf-db-postgres-1", "psql", "-U", "ispf", "-d", "postgres", "-c",
            "CREATE DATABASE ispf_solution_tck"])

    sh(["docker", "rm", "-f", "ispf-tck-clickhouse", "ispf-tck-nats", "ispf-tck-1", "ispf-tck-2"], check=False)
    if not sh(["docker", "images", "-q", "nats:2.10-alpine"]).stdout.strip():
        sh(["docker", "pull", "nats:2.10-alpine"])
    sh([
        "docker", "run", "-d", "--name", "ispf-tck-clickhouse", "--network", "ispf-tck",
        "-p", "127.0.0.1:8124:8123", "--memory", "4g",
        "-e", "CLICKHOUSE_USER=default",
        "-e", "CLICKHOUSE_PASSWORD=tck",
        "-e", "CLICKHOUSE_DB=default",
        "clickhouse/clickhouse-server:24.8",
    ])
    sh([
        "docker", "run", "-d", "--name", "ispf-tck-nats", "--network", "ispf-tck",
        "-p", "127.0.0.1:4222:4222", "nats:2.10-alpine", "-js",
    ])
    log("waiting for private ClickHouse")
    wait_clickhouse(time.time() + 90)
    log("clickhouse ready")
    node_env = f"""SPRING_DATASOURCE_URL=jdbc:postgresql://{os.environ.get("ISPF_TCK_PG_HOST", "host.docker.internal")}:5432/ispf_solution_tck
SPRING_DATASOURCE_USERNAME=ispf
SPRING_DATASOURCE_PASSWORD={pg_password}
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_PROFILES_ACTIVE=local
SPRING_FLYWAY_ENABLED=true
ISPF_BOOTSTRAP_FIXTURES_ENABLED=false
ISPF_AI_PROVIDER=noop
ISPF_SECURITY_TOKEN_AUTH_ENABLED=true
ISPF_SECURITY_RBAC_ENABLED=true
ISPF_CLUSTER_ENABLED=true
ISPF_CLUSTER_DRIVER_LOCK_TTL_SECONDS=3
ISPF_CLUSTER_DRIVER_LOCK_RENEW_SECONDS=1
ISPF_CLUSTER_DRIVER_FAILOVER_SCAN_SECONDS=1
ISPF_CLUSTER_DRIVER_LOCK_RENEW_MS=1000
ISPF_CLUSTER_DRIVER_FAILOVER_SCAN_MS=1000
ISPF_NATS_ENABLED=true
ISPF_NATS_URL=nats://ispf-tck-nats:4222
ISPF_NATS_JETSTREAM_ENABLED=true
ISPF_NATS_REPLICA_EVENTS=true
ISPF_REDIS_ENABLED=false
ISPF_CLUSTER_LIVE_VARIABLE_SYNC=false
ISPF_CLUSTER_PATH_INTEREST=false
ISPF_REPLICA_PROFILE=io
ISPF_REPLICA_CAPABILITIES=drivers,http-public,ws,schedulers,config-write
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true
ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=0
ISPF_RUNTIME_TELEMETRY_COALESCE_MS=0
ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL=http://ispf-tck-clickhouse:8123
ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE=default
ISPF_VARIABLE_HISTORY_CLICKHOUSE_USERNAME=default
ISPF_VARIABLE_HISTORY_CLICKHOUSE_PASSWORD=tck
ISPF_VARIABLE_HISTORY_CATCHUP_MAX_BYTES=8589934592
ISPF_DRIVER_PACKS_DIR=/opt/ispf/drivers
JAVA_TOOL_OPTIONS=-Xms1g -Xmx4g
"""
    with open(ENV_FILE, "w", encoding="utf-8") as handle:
        handle.write(node_env)
    os.chmod(ENV_FILE, 0o600)
    for name, port, replica in (("ispf-tck-1", "8081", "replica-1"), ("ispf-tck-2", "8082", "replica-2")):
        command = [
            "docker", "run", "-d", "--name", name, "--network", "ispf-tck",
            "--add-host", "host.docker.internal:host-gateway",
            "-p", f"127.0.0.1:{port}:8080",
            "-v", f"{JAR}:/opt/ispf/ispf-server.jar:ro",
            "-v", f"{os.environ.get('ISPF_TCK_DRIVERS_DIR', '/home/iot-solutions/ispf-tck/drivers')}:/opt/ispf/drivers:ro",
            "--env-file", ENV_FILE,
            "-e", f"ISPF_REPLICA_ID={replica}",
        ]
        if replica == "replica-1":
            command.extend([
                "-e",
                f"ISPF_APPLICATION_OUTBOX_HTTP_ENDPOINTS=sap=http://host.docker.internal:{ERP_PORT}/inbox,erp=http://host.docker.internal:{ERP_PORT}/inbox",
            ])
        command.extend([
            "eclipse-temurin:25-jre-jammy",
            "java", "-jar", "/opt/ispf/ispf-server.jar",
        ])
        sh(command)
        log(f"started {name}")
    log("waiting for nodes")
    info = wait_info(8081, time.time() + 420)
    wait_info(8082, time.time() + 420)
    token = login(8081, time.time() + 60)
    chain_status, chain = http("GET", "http://127.0.0.1:8081/api/v1/audit/verify-chain", token=token)
    result = {
        "version": info.get("version"),
        "verifyChainBefore": chain,
        "verifyChainHttp": chain_status,
        "chOutageSeconds": CH_OUTAGE_SECONDS,
        "erpOutageSeconds": ERP_OUTAGE_SECONDS,
    }
    with open(RESULT, "w", encoding="utf-8") as handle:
        json.dump(result, handle, indent=2)
    print(json.dumps(result))


if __name__ == "__main__":
    main()
