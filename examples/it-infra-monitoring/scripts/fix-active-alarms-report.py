#!/usr/bin/env python3
"""Fix itm-active-alarms report + sync hub.activeAlarmsFeed from live topology state."""
import json
import urllib.parse
import urllib.request
from datetime import datetime, timezone

BASE = "http://127.0.0.1:8080"
HUB = "root.platform.devices.itm.hub"
SITE = "m11"
NETWORK = f"root.platform.devices.itm.sites.{SITE}.network"
ISP = f"root.platform.devices.itm.sites.{SITE}.isp"
SECTIONS = f"root.platform.devices.itm.sites.{SITE}.sections"
REPORT = "root.platform.reports.itm-active-alarms"

CANDIDATES = [
    *((f"{NETWORK}.{nid}", label) for nid, label in [
        ("tp12", "ТП12"), ("tp14", "ТП14"), ("tp16", "ТП16"),
        ("cpu5", "ЦПУ5"), ("deu19", "ДЭУ19"), ("deu19tp12", "ДЭУ19-ТП12"),
        ("tspu5deu19", "ЦПУ5-ДЭУ19"), ("tp16tspu5", "ТП16-ЦПУ5"), ("pkadtp16", "ПКАД-ТП16"),
    ]),
    *((f"{SECTIONS}.section{i}.sw-section{i}", f"Участок {i}") for i in range(1, 7)),
    (f"{ISP}.isp-rostelecom", "Ростелеком"),
    (f"{ISP}.isp-megafon", "Мегафон/Westcall"),
]


def req(method, path, token=None, body=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(r) as resp:
        raw = resp.read()
        return resp.status, json.loads(raw) if raw else None


def mcp_set(token, path, name, value):
    status, result = req(
        "POST",
        "/api/v1/ai/mcp",
        token,
        {
            "jsonrpc": "2.0",
            "id": f"{path}:{name}",
            "method": "tools/call",
            "params": {
                "name": "set_variable",
                "arguments": {"path": path, "name": name, "value": value},
            },
        },
    )
    if result.get("error"):
        raise RuntimeError(result["error"])
    text = (result.get("result") or {}).get("content", [{}])[0].get("text")
    parsed = json.loads(text) if text else result.get("result")
    if isinstance(parsed, dict) and parsed.get("status") == "ERROR":
        raise RuntimeError(parsed.get("error", "set_variable failed"))


def main():
    _, login = req("POST", "/api/v1/auth/login", body={"username": "admin", "password": "admin"})
    token = login["token"]

    # 1) Ensure activeAlarmsFeed exists
    _, vars_ = req(
        "GET",
        f"/api/v1/objects/by-path/variables?path={urllib.parse.quote(HUB)}",
        token,
    )
    names = {v["name"] for v in vars_}
    schema = {
        "name": "activeAlarmsFeed",
        "fields": [
            {
                "name": "items",
                "type": "RECORD_LIST",
                "nestedSchema": {
                    "name": "alarmItem",
                    "fields": [
                        {"name": "ts", "type": "STRING"},
                        {"name": "severity", "type": "STRING"},
                        {"name": "kind", "type": "STRING"},
                        {"name": "source", "type": "STRING"},
                        {"name": "message", "type": "STRING"},
                        {"name": "objectPath", "type": "STRING"},
                    ],
                },
            }
        ],
    }
    if "activeAlarmsFeed" not in names:
        status, _ = req(
            "POST",
            f"/api/v1/objects/by-path/variables?path={urllib.parse.quote(HUB)}",
            token,
            {
                "name": "activeAlarmsFeed",
                "description": "Active topology / device alarms as a list (empty when healthy)",
                "group": "status",
                "readable": True,
                "writable": True,
                "historyEnabled": False,
                "schema": schema,
                "value": {"schema": schema, "rows": [{"items": []}]},
            },
        )
        print("created activeAlarmsFeed", status)
    else:
        # make writable if needed
        req(
            "PATCH",
            f"/api/v1/objects/by-path/variables?path={urllib.parse.quote(HUB)}&name=activeAlarmsFeed",
            token,
            {"writable": True},
        )
        print("activeAlarmsFeed exists")

    # 2) Reconfigure report
    columns = [
        {"field": "severity", "label": "Уровень"},
        {"field": "source", "label": "Источник"},
        {"field": "message", "label": "Сообщение"},
        {"field": "objectPath", "label": "Объект"},
        {"field": "ts", "label": "Время"},
    ]
    status, saved = req(
        "PUT",
        f"/api/v1/reports/by-path/tree-variables-definition?path={urllib.parse.quote(REPORT)}",
        token,
        {
            "title": "Активные аварии",
            "devicePathPattern": HUB,
            "variableName": "activeAlarmsFeed",
            "columns": columns,
            "maxRows": 500,
            "refreshIntervalMs": 5000,
        },
    )
    print("report", status, saved.get("variableName"), saved.get("devicePathPattern"))

    # 3) Scan live topology → alarm feed
    paths = [p for p, _ in CANDIDATES]
    q = urllib.parse.quote(",".join(paths))
    _, batch = req("GET", f"/api/v1/objects/variables/batch?paths={q}", token)
    ts = datetime.now(timezone.utc).isoformat()
    items = []
    for path, source in CANDIDATES:
        vars_ = batch.get(path) or []
        by_name = {v["name"]: v for v in vars_}
        status_row = ((by_name.get("status") or {}).get("value") or {}).get("rows") or [{}]
        status_row = status_row[0] if status_row else {}
        if status_row.get("online") is False:
            items.append({
                "ts": ts,
                "severity": "WARNING",
                "kind": "node",
                "source": source,
                "message": f"Узел offline: {source}",
                "objectPath": path,
            })
        link_row = ((by_name.get("linkStatus") or {}).get("value") or {}).get("rows") or [{}]
        link_row = link_row[0] if link_row else {}
        if link_row.get("value") in (0, "0"):
            items.append({
                "ts": ts,
                "severity": "CRITICAL",
                "kind": "link",
                "source": source,
                "message": f"Линк down: {source}",
                "objectPath": path,
            })

    mcp_set(token, HUB, "activeAlarmsFeed", {"items": items})
    mcp_set(token, HUB, "kpiActiveAlarms", {"value": len(items)})
    print(f"synced {len(items)} active alarms:")
    for item in items:
        print(f"  {item['severity']} {item['message']}")

    # 4) Preview report
    _, preview = req(
        "POST",
        f"/api/v1/reports/by-path/run?path={urllib.parse.quote(REPORT)}",
        token,
        {},
    )
    print("report rowCount:", preview.get("rowCount"))
    for row in (preview.get("rows") or [])[:8]:
        print(" ", row.get("severity"), row.get("message"))


if __name__ == "__main__":
    main()
