#!/usr/bin/env python3
"""Two-sample thread CPU diagnostics on local ISPF (run on VPS)."""
import json
import subprocess
import time
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(method, path, token=None, data=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(f"{BASE}{path}", data=body, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def main():
    login = api("POST", "/api/v1/auth/login", data={"username": "admin", "password": "admin"})
    token = login["token"]
    info = api("GET", "/api/v1/info", token)
    probe = api("GET", "/api/v1/platform/diagnostics/metrics-probe", token)

    api("GET", "/api/v1/platform/metrics", token)
    time.sleep(6)
    metrics = api("GET", "/api/v1/platform/metrics", token)

    diag = metrics.get("diagnostics", {})
    detail = diag.get("detail", {})
    automation = metrics.get("automation", {})
    drivers = metrics.get("drivers", {})

    print("=== SNAPSHOT ===")
    print(json.dumps({
        "version": info.get("version"),
        "clusterEnabled": info.get("clusterEnabled"),
        "jobConsumerActive": info.get("jobConsumerActive"),
        "metricsProbe": probe,
        "processCpuPercent": diag.get("processCpuPercent"),
        "pressureScore": diag.get("pressureScore"),
        "topSuspect": diag.get("topSuspect"),
        "activeDrivers": drivers.get("activeDrivers"),
        "threadSampleReady": detail.get("threadSampleReady"),
        "threadWindowSec": detail.get("threadSampleWindowSeconds"),
        "threadAttributedPct": detail.get("threadCpuAttributedPercent"),
    }, indent=2))

    print("\n=== THREAD GROUPS ===")
    for g in detail.get("threadGroups", []):
        print(f"  {g.get('prefix', '?'):28} threads={g.get('threadCount', 0):2} cpu={g.get('cpuPercentDelta')}%")

    print("\n=== TOP THREADS ===")
    for t in detail.get("topThreads", [])[:10]:
        print(f"  {t.get('cpuPercentDelta')}%  {t.get('name')}")

    print("\n=== QUEUES / RATES ===")
    for k in [
        "objectChangeQueueSize",
        "objectChangeQueueByLane",
        "objectChangeWorkersByLane",
        "eventJournalQueueSize",
        "variableHistoryQueueSize",
        "eventsPerSecond",
        "alertEvaluationsTotal",
        "alertFiresTotal",
        "workflowInstancesRunning",
    ]:
        if k in automation:
            print(f"  {k}={automation[k]}")

    print("\n=== SUSPECTS ===")
    for s in diag.get("suspects", [])[:8]:
        print(f"  [{s.get('score')}] {s.get('category')}/{s.get('code')}: {s.get('title')} — {s.get('detail')}")

    print("\n=== RUNNING WORKFLOWS ===")
    for w in detail.get("runningWorkflows", [])[:5]:
        print(f"  {w.get('workflowPath')} runningSeconds={w.get('runningSeconds')}")

    print("\n=== DOCKER ===")
    subprocess.run(
        ["docker", "stats", "--no-stream", "--format", "{{.Name}} CPU={{.CPUPerc}} MEM={{.MemUsage}}", "ispf-vps-replica-1"],
        check=False,
    )


if __name__ == "__main__":
    main()
