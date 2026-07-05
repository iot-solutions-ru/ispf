#!/usr/bin/env python3
"""Diagnose 16-driver shared-topic setup: driver health + broker subscriber fan-out."""
from __future__ import annotations

import json
import subprocess
import time
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8000"
SHARED_TOPIC = "ispf/loadtest/shared/temperature"
DEVICES = 16
MQTT_CID = "ispf-lab-mqtt-1"


def login() -> str:
    req = urllib.request.Request(
        f"{BASE}/api/v1/auth/login",
        data=json.dumps({"username": "admin", "password": "admin"}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    return json.load(urllib.request.urlopen(req, timeout=30))["token"]


def api_get(path: str, token: str) -> dict | list | None:
    qpath = path if path.startswith("http") else f"{BASE}{path}"
    req = urllib.request.Request(qpath, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.load(resp)
    except Exception as ex:
        return {"error": str(ex)}


def mosquitto_sys(topic: str) -> int | None:
    try:
        out = subprocess.check_output(
            [
                "docker",
                "exec",
                MQTT_CID,
                "mosquitto_sub",
                "-h",
                "localhost",
                "-t",
                topic,
                "-C",
                "1",
                "-W",
                "5",
            ],
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=15,
        ).strip()
        return int(out) if out.isdigit() else None
    except Exception:
        return None


def publish_n(n: int) -> None:
    for i in range(n):
        subprocess.run(
            [
                "docker",
                "exec",
                MQTT_CID,
                "mosquitto_pub",
                "-h",
                "localhost",
                "-t",
                SHARED_TOPIC,
                "-m",
                json.dumps({"value": 42.0 + i, "seq": i, "ts": "2026-07-05T08:00:00Z"}),
            ],
            check=True,
            capture_output=True,
            timeout=10,
        )


def metrics_snapshot(token: str) -> dict[str, object]:
    data = api_get("/api/v1/platform/metrics", token)
    out: dict[str, object] = {}
    if not isinstance(data, dict):
        return out
    for section in data.get("sections", []):
        sid = section.get("id")
        if sid in ("automation", "eventJournal", "drivers"):
            out[sid] = section.get("values") or {}
    return out


def driver_status(token: str, path: str) -> dict:
    q = urllib.parse.quote(path, safe="")
    data = api_get(f"/api/v1/drivers/runtime/status?devicePath={q}", token)
    return data if isinstance(data, dict) else {"error": data}


def driver_configure(token: str, path: str) -> dict:
    q = urllib.parse.quote(path, safe="")
    data = api_get(f"/api/v1/drivers/runtime/status?devicePath={q}", token)
    return data if isinstance(data, dict) else {"error": data}


def read_point_mapping(token: str, path: str) -> str:
    q = urllib.parse.quote(path, safe="")
    data = api_get(f"/api/v1/objects/by-path/manifest?path={q}", token)
    if not isinstance(data, dict):
        return "?"
    for section in data.get("sections", []) if isinstance(data.get("sections"), list) else []:
        if section.get("id") == "driver":
            cfg = section.get("values") or {}
            mappings = cfg.get("pointMappings") or {}
            if isinstance(mappings, dict) and mappings:
                return str(next(iter(mappings.values())))
            conf = cfg.get("configuration") or {}
            return str(conf.get("ingressCoalesceEnabled", conf.get("brokerUrl", "?")))
    return "?"


def pg_device_count() -> str:
    try:
        out = subprocess.check_output(
            [
                "docker",
                "exec",
                "ispf-lab-postgres-1",
                "psql",
                "-U",
                "ispf",
                "-d",
                "ispf",
                "-t",
                "-A",
                "-c",
                "SELECT COUNT(*) FROM object_nodes WHERE path LIKE 'root.platform.devices.loadtest-mqtt-dev-%';",
            ],
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=20,
        ).strip()
        return out
    except Exception as ex:
        return f"error: {ex}"


def jvm_threads() -> str:
    try:
        out = subprocess.check_output(
            [
                "docker",
                "exec",
                "ispf-lab-ispf-server-1",
                "sh",
                "-c",
                "grep Threads /proc/1/status; "
                "for t in /proc/1/task/*; do cat \"$t/comm\" 2>/dev/null; done "
                "| sort | uniq -c | sort -rn | head -8",
            ],
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=180,
        )
        return out.strip()
    except Exception as ex:
        return f"error: {ex}"


def main() -> None:
    token = login()
    pad = 5

    print("=== DB: loadtest mqtt device objects ===")
    print("count", pg_device_count())

    print("\n=== Broker $SYS (before probe) ===")
    sys_topics = [
        "$SYS/broker/clients/connected",
        "$SYS/broker/subscriptions/count",
        "$SYS/broker/messages/received",
        "$SYS/broker/messages/sent",
    ]
    before_sys: dict[str, int | None] = {}
    for t in sys_topics:
        before_sys[t] = mosquitto_sys(t)
        print(f"  {t}: {before_sys[t]}")

    print("\n=== Drivers 1..100 status summary ===")
    by_status: dict[str, int] = {}
    connected = 0
    running = 0
    errors: dict[str, int] = {}
    for i in range(1, 101):
        path = f"root.platform.devices.loadtest-mqtt-dev-{i:0{pad}d}"
        st = driver_status(token, path)
        if st.get("error"):
            by_status["NO_STATUS"] = by_status.get("NO_STATUS", 0) + 1
            continue
        s = st.get("status") or "?"
        by_status[s] = by_status.get(s, 0) + 1
        if st.get("connected"):
            connected += 1
        if s == "RUNNING":
            running += 1
        err = (st.get("lastError") or "").strip()
        if err:
            errors[err] = errors.get(err, 0) + 1
    print("status_counts", by_status)
    print(f"connected_true={connected} running={running}")
    if errors:
        print("error_counts", errors)

    print(f"\n=== Ghost drivers 17..20 (objects may be gone) ===")
    for i in (17, 18, 19, 20, 50):
        path = f"root.platform.devices.loadtest-mqtt-dev-{i:0{pad}d}"
        st = driver_status(token, path)
        print(
            f"  dev-{i:05d} status={st.get('status')} connected={st.get('connected')} "
            f"err={(st.get('lastError') or '')[:80]!r}"
        )

    print(f"\n=== Drivers 1..{DEVICES} detail ===")
    for i in range(1, DEVICES + 1):
        path = f"root.platform.devices.loadtest-mqtt-dev-{i:0{pad}d}"
        st = driver_status(token, path)
        topic = read_point_mapping(token, path)
        print(
            f"  {i:02d} status={st.get('status')} connected={st.get('connected')} "
            f"mode={st.get('telemetryPublishMode')} "
            f"err={(st.get('lastError') or '')[:60]!r} topic={topic!r}"
        )

    print("\n=== Fan-out probe: 10 publishes, quiet window ===")
    time.sleep(2)
    rx0 = mosquitto_sys("$SYS/broker/messages/received") or 0
    tx0 = mosquitto_sys("$SYS/broker/messages/sent") or 0
    before_m = metrics_snapshot(token)
    fired0 = int((before_m.get("automation") or {}).get("eventsFiredTotal") or 0)
    publish_n(10)
    time.sleep(4)

    rx1 = mosquitto_sys("$SYS/broker/messages/received") or 0
    tx1 = mosquitto_sys("$SYS/broker/messages/sent") or 0
    after_m = metrics_snapshot(token)
    fired1 = int((after_m.get("automation") or {}).get("eventsFiredTotal") or 0)

    pub_delta = rx1 - rx0
    sent_delta = tx1 - tx0
    fired_delta = fired1 - fired0
    eff_subs = (sent_delta / pub_delta) if pub_delta else 0
    eff_journal = (fired_delta / pub_delta) if pub_delta else 0

    print(f"  publish_delta={pub_delta} broker_sent_delta={sent_delta} eventsFiredTotal_delta={fired_delta}")
    print(f"  effective_broker_subscribers={eff_subs:.1f} (expect {DEVICES})")
    print(f"  effective_journal_per_publish={eff_journal:.1f} (expect {DEVICES})")
    print(f"  capture_vs_broker={(fired_delta / sent_delta * 100):.1f}%" if sent_delta else "  capture_vs_broker=n/a")

    ej = after_m.get("eventJournal") or {}
    auto = after_m.get("automation") or {}
    print("\n=== ISPF queues (after probe) ===")
    for k in (
        "eventJournalQueueSize",
        "eventJournalSyncFallbackTotal",
        "eventsFired",
        "eventsFiredTotal",
    ):
        if k in ej:
            print(f"  eventJournal.{k}={ej[k]}")
    for k in ("eventJournalQueueSize", "telemetryIngressPending"):
        if k in auto:
            print(f"  automation.{k}={auto[k]}")

    print("\n=== JVM thread snapshot ===")
    print(jvm_threads())

    print("\n=== emqtt / sweep processes ===")
    try:
        ps = subprocess.check_output(
            "pgrep -af 'emqtt_bench|lab-shared-topic' || true",
            shell=True,
            text=True,
            timeout=10,
        ).strip()
        print(ps or "(none)")
    except Exception as ex:
        print(f"error: {ex}")


if __name__ == "__main__":
    main()
