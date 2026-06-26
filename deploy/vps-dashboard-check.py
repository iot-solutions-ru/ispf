#!/usr/bin/env python3
"""Inspect dashboard widgets and verify bindings for temperature-instances."""
import json
import sys
import urllib.request

API = "http://127.0.0.1:8080"
DASH = "root.platform.dashboards.temperature-instances"


def api(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def main() -> int:
    token = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})["token"]
    dash = api("GET", f"/api/v1/dashboards/by-path?path={DASH}", token=token)
    print("dashboard title:", dash.get("title"))
    print("refreshIntervalMs:", dash.get("refreshIntervalMs"))
    layout = dash.get("layout") or json.loads(dash.get("layoutJson") or "{}")
    widgets = layout.get("widgets") or []
    print("widgets:", len(widgets))
    for i, w in enumerate(widgets):
        print(f"\n--- widget {i} type={w.get('type')} title={w.get('title')!r} ---")
        for key in (
            "objectPath", "parentPath", "variableName", "valueField", "selectionKey",
            "rowSelectionKey", "namePattern", "historyRange", "columnsJson", "chartType",
        ):
            if key in w and w[key] not in (None, "", "[]"):
                val = w[key]
                if key == "columnsJson" and isinstance(val, str):
                    try:
                        val = json.loads(val)
                    except json.JSONDecodeError:
                        pass
                print(f"  {key}: {json.dumps(val, ensure_ascii=False) if isinstance(val, (dict, list)) else val}")

    # Resolve chart binding
    chart = next((w for w in widgets if w.get("type") == "chart"), None)
    table = next((w for w in widgets if w.get("type") in ("objectTable", "object-table")), None)
    if chart:
        sel_key = chart.get("selectionKey")
        obj_path = chart.get("objectPath")
        if sel_key and not obj_path:
            print(f"\nChart uses selectionKey={sel_key!r} — needs table row selection")
        if obj_path:
            check_path(token, obj_path, chart.get("variableName", "temperature"), chart.get("valueField", "value"))
    if table:
        parent = table.get("parentPath")
        if parent:
            try:
                children = api("GET", f"/api/v1/objects?parentPath={parent}", token=token)
            except Exception as e:
                print(f"\nTable parent fetch ERROR: {e}")
                children = []
            filtered = [
                c for c in children
                if (not table.get("namePattern") or match_pattern(c.get("name", ""), table["namePattern"]))
            ]
            print(f"\nTable parent {parent}: {len(children)} children, {len(filtered)} after namePattern")
            if filtered:
                sample = filtered[0]["path"]
                print("  sample row:", sample)
                vars_list = api("GET", f"/api/v1/objects/by-path/variables?path={sample}", token=token)
                temp = next((v for v in vars_list if v.get("name") == "temperature"), None)
                if temp:
                    row = (temp.get("value") or {}).get("rows", [{}])[0]
                    print("  temperature:", row, "historyEnabled:", temp.get("historyEnabled"))
                else:
                    print("  NO temperature variable")
                # batch limit test
                paths = [c["path"] for c in filtered[:55]]
                paths_param = ",".join(paths)
                try:
                    batch = api("GET", f"/api/v1/objects/variables/batch?paths={paths_param}", token=token)
                    print(f"  batch {len(paths)} paths: OK, keys={len(batch)}")
                except Exception as e:
                    print(f"  batch {len(paths)} paths: FAIL {e}")
                if sel_key := table.get("selectionKey"):
                    print(f"  selectionKey={sel_key!r} (chart/value bind to selected row)")
                    hist = api(
                        "GET",
                        f"/api/v1/objects/by-path/variables/history?path={sample}&name=temperature&field=value&limit=5&range=1h",
                        token=token,
                    )
                    print("  history 1h samples:", len(hist.get("samples", [])))
    return 0


def match_pattern(name: str, pattern: str) -> bool:
    import fnmatch
    return fnmatch.fnmatch(name, pattern)


def check_path(token: str, path: str, var: str, field: str) -> None:
    print(f"\nChart target {path} var={var} field={field}")
    try:
        vars_list = api("GET", f"/api/v1/objects/by-path/variables?path={path}", token=token)
    except Exception as e:
        print("  ERROR:", e)
        return
    v = next((x for x in vars_list if x.get("name") == var), None)
    if not v:
        print("  variable missing")
        return
    print("  historyEnabled:", v.get("historyEnabled"))
    print("  value:", (v.get("value") or {}).get("rows", [{}])[0])
    try:
        hist = api(
            "GET",
            f"/api/v1/objects/by-path/variables/history?path={path}&name={var}&field={field}&limit=5",
            token=token,
        )
        print("  history samples:", len(hist.get("samples", [])))
    except Exception as e:
        print("  history error:", e)


if __name__ == "__main__":
    raise SystemExit(main())
