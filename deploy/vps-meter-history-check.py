#!/usr/bin/env python3
import json
import subprocess
import sys
import urllib.error
import urllib.request

API = "http://127.0.0.1:8080"


def api(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def psql(sql: str) -> str:
    return subprocess.check_output(
        ["docker", "exec", "ispf-postgres", "psql", "-U", "ispf", "-d", "ispf", "-t", "-A", "-c", sql],
        text=True,
    ).strip()


def main() -> int:
    token = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})["token"]
    paths = [
        "root.platform.instances.meter-00001",
        "root.platform.instances.meter-0001",
        "root.platform.instances.meter-05000",
        "root.platform.instances.meter-10000",
    ]
    for path in paths:
        print(f"\n=== {path} ===")
        try:
            vars_list = api("GET", f"/api/v1/objects/by-path/variables?path={path}", token=token)
        except urllib.error.HTTPError as e:
            print("  object missing or forbidden:", e.code)
            continue
        temp = next((v for v in vars_list if v.get("name") == "temperature"), None)
        if not temp:
            print("  no temperature variable")
            continue
        print("  historyEnabled:", temp.get("historyEnabled"))
        val = (temp.get("value") or {}).get("rows", [{}])[0].get("value")
        print("  current value:", val)
        try:
            hist = api(
                "GET",
                f"/api/v1/objects/by-path/variables/history?path={path}&name=temperature&field=value&limit=10",
                token=token,
            )
            samples = hist.get("samples", [])
            print("  history API samples:", len(samples))
            if samples:
                print("  oldest:", samples[0])
                print("  newest:", samples[-1])
        except urllib.error.HTTPError as e:
            print("  history API error:", e.code, e.read().decode()[:200])

    print("\n=== DB aggregates ===")
    queries = {
        "instances": "SELECT count(*) FROM object_nodes WHERE path LIKE 'root.platform.instances.meter-%';",
        "history_enabled_vars": (
            "SELECT count(*) FROM object_variables "
            "WHERE object_path LIKE 'root.platform.instances.meter-%' "
            "AND name='temperature' AND history_enabled=true;"
        ),
        "samples_1h": (
            "SELECT count(*) FROM variable_samples "
            "WHERE object_path LIKE 'root.platform.instances.meter-%' "
            "AND sampled_at > now() - interval '1 hour';"
        ),
        "objects_with_samples": (
            "SELECT count(DISTINCT object_path) FROM variable_samples "
            "WHERE object_path LIKE 'root.platform.instances.meter-%';"
        ),
    }
    for label, sql in queries.items():
        print(f"  {label}: {psql(sql)}")

    print("\n  top5 by sample count:")
    rows = psql(
        "SELECT object_path || ' ' || count(*)::text FROM variable_samples "
        "WHERE object_path LIKE 'root.platform.instances.meter-%' "
        "GROUP BY object_path ORDER BY count(*) DESC LIMIT 5;"
    )
    for line in rows.splitlines():
        if line.strip():
            print("   ", line)

    metrics = api("GET", "/api/v1/platform/metrics", token=token)
    for section in metrics.get("sections", []):
        if section.get("id") == "variableHistory":
            print("\n=== variableHistory metrics ===")
            print(json.dumps(section.get("values"), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
