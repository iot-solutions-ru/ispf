#!/usr/bin/env python3
"""Put the TCK nodes back on JDBC dual-write after the ClickHouse peak run."""

import os
import subprocess
import time
import urllib.request

ENV_FILE = "/home/iot-solutions/ispf-tck/node.env"
JAR = "/home/iot-solutions/ispf-tck/ispf-server.jar"


def sh(command):
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode != 0:
        raise SystemExit(result.stderr[-500:])


def main():
    backup = ENV_FILE + ".before-peak"
    if os.path.exists(backup):
        text = open(backup, encoding="utf-8").read()
        os.remove(backup)
    else:
        text = open(ENV_FILE, encoding="utf-8").read()
    text = text.replace("ISPF_VARIABLE_HISTORY_STORE=clickhouse", "ISPF_VARIABLE_HISTORY_STORE=jdbc")
    text = text.replace("ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false", "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true")
    text = text.replace(
        "JAVA_TOOL_OPTIONS=-Xms8g -Xmx16g -XX:+UseG1GC -XX:MaxGCPauseMillis=100",
        "JAVA_TOOL_OPTIONS=-Xms1g -Xmx4g",
    )
    if "ISPF_VARIABLE_HISTORY_STORE=jdbc" not in text:
        text = text.replace("ISPF_VARIABLE_HISTORY_STORE=clickhouse", "ISPF_VARIABLE_HISTORY_STORE=jdbc")
    if "ISPF_VARIABLE_HISTORY_STORE=jdbc" not in text:
        raise SystemExit("jdbc store was not restored")
    with open(ENV_FILE, "w", encoding="utf-8") as handle:
        handle.write(text)
    os.chmod(ENV_FILE, 0o600)
    subprocess.run(["docker", "rm", "-f", "ispf-tck-1"], text=True, capture_output=True)
    sh([
        "docker", "run", "-d", "--name", "ispf-tck-1", "--network", "ispf-tck",
        "--add-host", "host.docker.internal:host-gateway",
        "-p", "127.0.0.1:8081:8080",
        "-v", f"{JAR}:/opt/ispf/ispf-server.jar:ro",
        "-v", f"{os.environ.get('ISPF_TCK_DRIVERS_DIR', '/home/iot-solutions/ispf-tck/drivers')}:/opt/ispf/drivers:ro",
        "--env-file", ENV_FILE,
        "-e", "ISPF_REPLICA_ID=replica-1",
        "-e", "ISPF_APPLICATION_OUTBOX_HTTP_ENDPOINTS=sap=http://host.docker.internal:8090/inbox,erp=http://host.docker.internal:8090/inbox",
        "eclipse-temurin:25-jre-jammy",
        "java", "-jar", "/opt/ispf/ispf-server.jar",
    ])
    deadline = time.time() + 180
    while time.time() < deadline:
        try:
            with urllib.request.urlopen("http://127.0.0.1:8081/api/v1/info", timeout=5) as response:
                if response.status == 200:
                    print("replica-1 info", response.status, flush=True)
                    break
        except Exception:
            time.sleep(2)
    else:
        raise SystemExit("replica-1 did not answer")
    time.sleep(5)
    subprocess.run(["docker", "rm", "-f", "ispf-tck-2"], text=True, capture_output=True)
    sh([
        "docker", "run", "-d", "--name", "ispf-tck-2", "--network", "ispf-tck",
        "--add-host", "host.docker.internal:host-gateway",
        "-p", "127.0.0.1:8082:8080",
        "-v", f"{JAR}:/opt/ispf/ispf-server.jar:ro",
        "-v", f"{os.environ.get('ISPF_TCK_DRIVERS_DIR', '/home/iot-solutions/ispf-tck/drivers')}:/opt/ispf/drivers:ro",
        "--env-file", ENV_FILE,
        "-e", "ISPF_REPLICA_ID=replica-2",
        "eclipse-temurin:25-jre-jammy",
        "java", "-jar", "/opt/ispf/ispf-server.jar",
    ])
    deadline = time.time() + 180
    while time.time() < deadline:
        try:
            with urllib.request.urlopen("http://127.0.0.1:8082/api/v1/info", timeout=5) as response:
                if response.status == 200:
                    print("replica-2 info", response.status, flush=True)
                    break
        except Exception:
            time.sleep(2)
    else:
        raise SystemExit("replica-2 did not answer")
    names = subprocess.run(["docker", "ps", "-a"], text=True, capture_output=True)
    for line in names.stdout.splitlines():
        if "ispf-tck-" in line or "ispf-enterprise" in line:
            print(line, flush=True)


if __name__ == "__main__":
    main()
