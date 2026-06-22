#!/usr/bin/env python3
"""Load test ISPF on VPS: seed devices/dashboards, then measure API throughput."""

from __future__ import annotations

import argparse
import json
import random
import statistics
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Callable
from urllib.parse import quote

import requests

PREFIX = "loadtest"
TEMPLATE_ID = "virtual-lab-v1"
LAB_MODELS = ("virtual-lab-v1", "virtual-lab-waves-sum-v1")

INT_VALUE_BODY = {
    "schema": {
        "name": "integerValue",
        "fields": [{"name": "value", "type": "INTEGER"}],
    },
}


def fixture_pad(count: int) -> int:
    return max(5, len(str(count)))


def device_name(index: int, pad: int) -> str:
    return f"{PREFIX}-dev-{index:0{pad}d}"


def dashboard_name(index: int, pad: int) -> str:
    return f"{PREFIX}-dash-{index:0{pad}d}"


def workflow_name(index: int, pad: int) -> str:
    return f"{PREFIX}-wf-{index:0{pad}d}"


def report_name(index: int, pad: int) -> str:
    return f"{PREFIX}-report-{index:0{pad}d}"


def alert_rule_name(index: int, pad: int) -> str:
    return f"loadtest alert {index:0{pad}d}"


def correlator_name(index: int, pad: int) -> str:
    return f"loadtest correlator {index:0{pad}d}"


MINIMAL_BPMN = """<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <process id="loadtest-process" isExecutable="true">
    <startEvent id="start"/>
    <endEvent id="end"/>
    <sequenceFlow sourceRef="start" targetRef="end"/>
  </process>
</definitions>"""

EVENT1_PAYLOAD = {"rows": [{"int": 7, "string": "loadtest"}]}


@dataclass
class Fixtures:
    device_paths: list[str]
    dashboard_paths: list[str]
    alert_rule_paths: list[str]
    correlator_paths: list[str]
    workflow_paths: list[str]
    report_paths: list[str]


@dataclass
class Sample:
    ok: bool
    ms: float
    op: str
    status: int = 0


@dataclass
class PhaseResult:
    concurrency: int
    duration_sec: float
    samples: list[Sample] = field(default_factory=list)

    def summary(self) -> dict:
        ok = [s for s in self.samples if s.ok]
        fail = [s for s in self.samples if not s.ok]
        lat = [s.ms for s in ok]
        elapsed = max(self.duration_sec, 0.001)
        by_op: dict[str, list[float]] = {}
        fail_by_op: dict[str, int] = {}
        fail_by_status: dict[str, int] = {}
        for s in ok:
            by_op.setdefault(s.op, []).append(s.ms)
        for s in fail:
            fail_by_op[s.op] = fail_by_op.get(s.op, 0) + 1
            key = str(s.status) if s.status else "exc"
            fail_by_status[key] = fail_by_status.get(key, 0) + 1
        return {
            "concurrency": self.concurrency,
            "total": len(self.samples),
            "ok": len(ok),
            "fail": len(fail),
            "rps": round(len(ok) / elapsed, 1),
            "fail_rate_pct": round(100 * len(fail) / max(len(self.samples), 1), 2),
            "latency_ms": percentile(lat),
            "by_op": {op: percentile(vals) for op, vals in sorted(by_op.items())},
            "fail_by_op": dict(sorted(fail_by_op.items())),
            "fail_by_status": dict(sorted(fail_by_status.items())),
        }


def percentile(values: list[float]) -> dict:
    if not values:
        return {"p50": 0, "p95": 0, "p99": 0, "avg": 0, "max": 0}
    values = sorted(values)
    n = len(values)

    def pct(p: float) -> float:
        idx = min(n - 1, max(0, int(round(p * (n - 1)))))
        return round(values[idx], 1)

    return {
        "p50": pct(0.50),
        "p95": pct(0.95),
        "p99": pct(0.99),
        "avg": round(statistics.mean(values), 1),
        "max": round(max(values), 1),
    }


class Client:
    def __init__(self, base_url: str, host_header: str | None, timeout: float):
        self.base = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"Accept": "application/json"})
        if host_header:
            self.session.headers["Host"] = host_header
        self.token: str | None = None

    def login(self, username: str, password: str) -> None:
        r = self.session.post(
            f"{self.base}/api/v1/auth/login",
            json={"username": username, "password": password},
            timeout=self.timeout,
        )
        r.raise_for_status()
        self.token = r.json()["token"]
        self.session.headers["Authorization"] = f"Bearer {self.token}"

    def request(self, method: str, path: str, **kwargs) -> requests.Response:
        url = f"{self.base}{path}"
        kwargs.setdefault("timeout", self.timeout)
        return self.session.request(method, url, **kwargs)


def widget_specs(device_path: str, idx: int) -> list[dict]:
    """Mixed widget types for one dashboard."""
    w = idx % 6
    if w == 0:
        return [
            {"id": "v1", "type": "value", "title": "Sine", "x": 0, "y": 0, "w": 3, "h": 2,
             "objectPath": device_path, "variableName": "sineWave", "valueField": "value", "decimals": 2},
            {"id": "g1", "type": "gauge", "title": "Sum", "x": 3, "y": 0, "w": 3, "h": 2,
             "objectPath": device_path, "variableName": "sumWaves", "valueField": "value", "min": 0, "max": 30},
            {"id": "c1", "type": "chart", "title": "Saw", "x": 0, "y": 2, "w": 6, "h": 4,
             "objectPath": device_path, "variableName": "sawtoothWave", "valueField": "value",
             "chartStyle": "line", "maxPoints": 60, "historyRange": "live", "refreshIntervalMs": 2000},
        ]
    if w == 1:
        return [
            {"id": "p1", "type": "pie-chart", "title": "Table pie", "x": 0, "y": 0, "w": 6, "h": 4,
             "objectPath": device_path, "variableName": "table", "labelField": "string", "valueField": "int"},
            {"id": "i1", "type": "indicator", "title": "Alarm", "x": 6, "y": 0, "w": 3, "h": 2,
             "objectPath": device_path, "variableName": "alarmLatched", "valueField": "value"},
        ]
    if w == 2:
        return [
            {"id": "ef1", "type": "event-feed", "title": "Events", "x": 0, "y": 0, "w": 6, "h": 4,
             "objectPath": device_path, "eventNamesJson": '["event1","event2"]', "maxItems": 25,
             "payloadFilterExpr": "int > 5"},
            {"id": "ht1", "type": "history-table", "title": "Sine history", "x": 6, "y": 0, "w": 6, "h": 4,
             "objectPath": device_path, "variableName": "sineWave", "valueField": "value", "decimals": 2},
        ]
    if w == 3:
        return [
            {"id": "ve1", "type": "variable-editor", "title": "Editor", "x": 0, "y": 0, "w": 6, "h": 4,
             "objectPath": device_path, "variablesJson": '["intValue","floatValue","sumIntFloat"]'},
            {"id": "pr1", "type": "progress", "title": "Int progress", "x": 6, "y": 0, "w": 4, "h": 2,
             "objectPath": device_path, "variableName": "intValue", "valueField": "value", "min": 0, "max": 100},
        ]
    if w == 4:
        return [
            {"id": "ff1", "type": "function-form", "title": "Calculate", "x": 0, "y": 0, "w": 5, "h": 4,
             "objectPath": device_path, "functionName": "calculate", "buttonLabel": "Run",
             "fieldsJson": '[{"name":"inputA","label":"A","type":"number"},{"name":"inputB","label":"B","type":"number"}]'},
            {"id": "sp1", "type": "sparkline", "title": "Sine spark", "x": 5, "y": 0, "w": 4, "h": 2,
             "objectPath": device_path, "variableName": "sineWave", "valueField": "value", "maxPoints": 40},
        ]
    return [
        {"id": "cg1", "type": "card-grid", "title": "Metrics", "x": 0, "y": 0, "w": 8, "h": 4,
         "cardsJson": json.dumps([
             {"title": "Sine", "objectPath": device_path, "variableName": "sineWave", "valueField": "value"},
             {"title": "Sum", "objectPath": device_path, "variableName": "sumIntFloat", "valueField": "value"},
             {"title": "Table sum", "objectPath": device_path, "variableName": "tableIntSum", "valueField": "value"},
         ])},
        {"id": "sb1", "type": "status-badge", "title": "Online", "x": 8, "y": 0, "w": 4, "h": 2,
         "objectPath": device_path, "variableName": "status", "valueField": "online",
         "trueLabel": "Online", "falseLabel": "Offline"},
    ]


def build_layout(device_path: str, idx: int) -> str:
    layout = {"columns": 12, "rowHeight": 72, "widgets": widget_specs(device_path, idx)}
    return json.dumps(layout, separators=(",", ":"))


def resolve_model_ids(client: Client) -> dict[str, str]:
    ids: dict[str, str] = {}
    for name in LAB_MODELS:
        r = client.request("GET", f"/api/v1/models/by-name/{name}")
        r.raise_for_status()
        ids[name] = r.json()["id"]
    return ids


def apply_lab_models(client: Client, object_path: str, model_ids: dict[str, str]) -> None:
    """Legacy repair for objects created before server-side template apply."""
    for name in LAB_MODELS:
        model_id = model_ids[name]
        r = client.request(
            "POST",
            f"/api/v1/models/{model_id}/apply?objectPath={quote(object_path, safe='')}",
        )
        if r.status_code >= 400:
            print(f"  WARN apply {name} on {object_path}: HTTP {r.status_code} {r.text[:80]}")


def create_device(
    client: Client,
    index: int,
    pad: int,
    driver_count: int,
    poll_ms: int,
    model_ids: dict[str, str],
    repair_legacy: bool,
) -> str | None:
    parent = "root.platform.devices"
    name = device_name(index, pad)
    path = f"{parent}.{name}"
    with_driver = index <= driver_count
    body: dict = {
        "parentPath": parent,
        "name": name,
        "type": "DEVICE",
        "displayName": f"Load test device {index}",
        "description": "Generated by vps-load-test.py",
        "templateId": TEMPLATE_ID,
    }
    if with_driver:
        body.update({
            "driverId": "virtual",
            "driverPollIntervalMs": poll_ms,
            "autoStartDriver": True,
        })
    r = client.request("POST", "/api/v1/objects", json=body)
    if r.status_code == 409:
        if repair_legacy:
            apply_lab_models(client, path, model_ids)
        return path
    if r.status_code >= 400:
        print(f"  WARN device {name}: HTTP {r.status_code} {r.text[:120]}")
        return None
    return path


def seed_fixtures(
    client: Client,
    device_count: int,
    dashboard_count: int,
    driver_count: int,
    poll_ms: int,
    seed_workers: int = 1,
    repair_legacy: bool = False,
) -> tuple[list[str], list[str]]:
    pad = fixture_pad(device_count)
    dash_pad = fixture_pad(dashboard_count)
    device_paths: list[str] = []
    parent = "root.platform.devices"
    model_ids = resolve_model_ids(client)
    print(f"Creating {device_count} devices ({driver_count} with virtual driver, workers={seed_workers})...")
    started = time.perf_counter()

    def client_factory() -> Client:
        c = Client(client.base, client.session.headers.get("Host"), client.timeout)
        c.token = client.token
        c.session.headers["Authorization"] = f"Bearer {client.token}"
        return c

    progress_step = max(50, device_count // 100)
    done = 0

    if seed_workers <= 1:
        for i in range(1, device_count + 1):
            path = create_device(client, i, pad, driver_count, poll_ms, model_ids, repair_legacy)
            if path:
                device_paths.append(path)
            if i % progress_step == 0 or i == device_count:
                elapsed = time.perf_counter() - started
                rate = i / max(elapsed, 0.001)
                print(f"  devices: {i}/{device_count} ({rate:.1f}/s)")
    else:
        lock = __import__("threading").Lock()

        def seed_one(index: int) -> str | None:
            return create_device(
                client_factory(), index, pad, driver_count, poll_ms, model_ids, repair_legacy
            )

        with ThreadPoolExecutor(max_workers=seed_workers) as pool:
            futures = [pool.submit(seed_one, i) for i in range(1, device_count + 1)]
            for f in as_completed(futures):
                path = f.result()
                if path:
                    with lock:
                        device_paths.append(path)
                with lock:
                    done += 1
                    if done % progress_step == 0 or done == device_count:
                        elapsed = time.perf_counter() - started
                        rate = done / max(elapsed, 0.001)
                        print(f"  devices: {done}/{device_count} ({rate:.1f}/s)")

    device_paths.sort()
    elapsed = time.perf_counter() - started
    print(f"  seeded devices in {elapsed:.0f}s ({len(device_paths)} paths)")

    dashboard_paths: list[str] = []
    dash_parent = "root.platform.dashboards"
    print(f"Creating {dashboard_count} dashboards with mixed widgets...")
    for i in range(1, dashboard_count + 1):
        name = dashboard_name(i, dash_pad)
        path = f"{dash_parent}.{name}"
        device_path = device_paths[(i - 1) % len(device_paths)] if device_paths else ""
        r = client.request(
            "POST",
            "/api/v1/objects",
            json={
                "parentPath": dash_parent,
                "name": name,
                "type": "DASHBOARD",
                "displayName": f"Load dashboard {i:03d}",
                "description": "Load test dashboard",
                "templateId": "dashboard-v1",
            },
        )
        if r.status_code not in (200, 201, 409):
            print(f"  WARN dashboard {name}: HTTP {r.status_code}")
            continue
        dashboard_paths.append(path)
        layout = build_layout(device_path, i)
        lr = client.request(
            "PUT",
            f"/api/v1/dashboards/by-path/layout?path={quote(path, safe='')}",
            json={"layoutJson": layout},
        )
        if lr.status_code >= 400:
            print(f"  WARN layout {name}: HTTP {lr.status_code}")

    print(f"Seeded {len(device_paths)} devices, {len(dashboard_paths)} dashboards")
    return device_paths, dashboard_paths


def seed_workflows(client: Client, count: int, pad: int) -> list[str]:
    paths: list[str] = []
    parent = "root.platform.workflows"
    print(f"Creating {count} workflows...")
    for i in range(1, count + 1):
        name = workflow_name(i, pad)
        path = f"{parent}.{name}"
        r = client.request(
            "POST",
            "/api/v1/objects",
            json={
                "parentPath": parent,
                "name": name,
                "type": "WORKFLOW",
                "displayName": f"Load workflow {i}",
                "templateId": "workflow-v1",
            },
        )
        if r.status_code not in (200, 201, 409):
            print(f"  WARN workflow {name}: HTTP {r.status_code}")
            continue
        paths.append(path)
        br = client.request(
            "PUT",
            f"/api/v1/workflows/by-path/bpmn?path={quote(path, safe='')}",
            json={"bpmnXml": MINIMAL_BPMN},
        )
        if br.status_code >= 400:
            print(f"  WARN workflow bpmn {name}: HTTP {br.status_code}")
        sr = client.request(
            "PUT",
            f"/api/v1/workflows/by-path/status?path={quote(path, safe='')}",
            json={"status": "ACTIVE"},
        )
        if sr.status_code >= 400:
            print(f"  WARN workflow status {name}: HTTP {sr.status_code}")
        if i % max(10, count // 10) == 0:
            print(f"  workflows: {i}/{count}")
    return paths


def seed_alert_rules(client: Client, count: int, pad: int, device_paths: list[str]) -> list[str]:
    paths: list[str] = []
    if not device_paths:
        return paths
    print(f"Creating {count} alert rules...")
    for i in range(1, count + 1):
        name = alert_rule_name(i, pad)
        device = device_paths[(i - 1) % len(device_paths)]
        threshold = 40 + (i % 50)
        r = client.request(
            "POST",
            "/api/v1/alert-rules",
            json={
                "name": name,
                "objectPath": device,
                "watchVariable": "intValue",
                "conditionExpr": f'self.intValue["value"] > {threshold}',
                "eventName": "event1",
                "payloadVariable": "intValue",
                "enabled": True,
                "edgeTrigger": True,
            },
        )
        if r.status_code not in (200, 201, 409):
            print(f"  WARN alert {name}: HTTP {r.status_code} {r.text[:80]}")
            continue
        rule_path = r.json().get("id") if r.status_code != 409 else None
        if not rule_path:
            lr = client.request("GET", "/api/v1/alert-rules")
            if lr.ok:
                for item in lr.json():
                    if item.get("name") == name:
                        rule_path = item.get("id")
                        break
        if rule_path:
            paths.append(rule_path)
        if i % max(20, count // 10) == 0:
            print(f"  alert-rules: {i}/{count}")
    return paths


def seed_correlators(
    client: Client,
    count: int,
    pad: int,
    device_paths: list[str],
    workflow_paths: list[str],
) -> list[str]:
    paths: list[str] = []
    if not device_paths:
        return paths
    print(f"Creating {count} correlators...")
    actions = ["RUN_WORKFLOW", "FIRE_EVENT", "SET_VARIABLE"]
    for i in range(1, count + 1):
        name = correlator_name(i, pad)
        device = device_paths[(i - 1) % len(device_paths)]
        action = actions[i % len(actions)]
        action_target = ""
        if action == "RUN_WORKFLOW" and workflow_paths:
            action_target = workflow_paths[(i - 1) % len(workflow_paths)]
        elif action == "FIRE_EVENT":
            action_target = "event2"
        elif action == "SET_VARIABLE":
            action_target = "alarmLatched=true"
        body = {
            "name": name,
            "objectPath": device,
            "patternType": "COUNT",
            "eventName": "event1",
            "secondEventName": "",
            "windowSeconds": 120,
            "minOccurrences": 1,
            "cooldownSeconds": 10,
            "sequenceGapSeconds": 0,
            "actionType": action,
            "actionTarget": action_target,
            "payloadFilterExpr": "",
            "enabled": True,
        }
        r = client.request("POST", "/api/v1/correlators", json=body)
        if r.status_code not in (200, 201, 409):
            print(f"  WARN correlator {name}: HTTP {r.status_code} {r.text[:80]}")
            continue
        corr_path = r.json().get("id") if r.status_code != 409 else None
        if not corr_path:
            lr = client.request("GET", "/api/v1/correlators")
            if lr.ok:
                for item in lr.json():
                    if item.get("name") == name:
                        corr_path = item.get("id")
                        break
        if corr_path:
            paths.append(corr_path)
        if i % max(20, count // 10) == 0:
            print(f"  correlators: {i}/{count}")
    return paths


def seed_reports(client: Client, count: int, pad: int) -> list[str]:
    paths: list[str] = []
    parent = "root.platform.reports"
    print(f"Creating {count} reports...")
    for i in range(1, count + 1):
        name = report_name(i, pad)
        path = f"{parent}.{name}"
        r = client.request(
            "POST",
            "/api/v1/objects",
            json={
                "parentPath": parent,
                "name": name,
                "type": "REPORT",
                "displayName": f"Load report {i}",
                "templateId": "report-v1",
            },
        )
        if r.status_code not in (200, 201, 409):
            print(f"  WARN report {name}: HTTP {r.status_code}")
            continue
        paths.append(path)
    return paths


def seed_automation(
    client: Client,
    device_paths: list[str],
    workflow_count: int,
    alert_count: int,
    correlator_count: int,
    report_count: int,
) -> tuple[list[str], list[str], list[str], list[str]]:
    pad = max(5, len(str(max(workflow_count, alert_count, correlator_count, report_count, 1))))
    workflow_paths = seed_workflows(client, workflow_count, pad) if workflow_count else []
    alert_paths = seed_alert_rules(client, alert_count, pad, device_paths) if alert_count else []
    correlator_paths = seed_correlators(
        client, correlator_count, pad, device_paths, workflow_paths
    ) if correlator_count else []
    report_paths = seed_reports(client, report_count, pad) if report_count else []
    print(
        f"Automation fixtures: {len(workflow_paths)} workflows, {len(alert_paths)} alert-rules, "
        f"{len(correlator_paths)} correlators, {len(report_paths)} reports"
    )
    return workflow_paths, alert_paths, correlator_paths, report_paths


def make_ops(fixtures: Fixtures) -> list[tuple[str, float, Callable[[Client], None]]]:
    ops: list[tuple[str, float, Callable[[Client], None]]] = []

    def add(weight: float, name: str, fn: Callable[[Client], None]) -> None:
        ops.append((name, weight, fn))

    device_paths = fixtures.device_paths
    dashboard_paths = fixtures.dashboard_paths
    alert_rule_paths = fixtures.alert_rule_paths
    correlator_paths = fixtures.correlator_paths
    workflow_paths = fixtures.workflow_paths
    report_paths = fixtures.report_paths

    def pick_device() -> str:
        return random.choice(device_paths)

    def pick_dashboard() -> str:
        return random.choice(dashboard_paths)

    def pick_alert() -> str:
        return random.choice(alert_rule_paths)

    def pick_correlator() -> str:
        return random.choice(correlator_paths)

    def pick_workflow() -> str:
        return random.choice(workflow_paths)

    def pick_report() -> str:
        return random.choice(report_paths)

    # Object tree reads
    add(0.08, "list_devices", lambda c: c.request(
        "GET", "/api/v1/objects?parent=root.platform.devices&lite=true"
    ).raise_for_status())
    add(0.03, "list_workflows_tree", lambda c: c.request(
        "GET", "/api/v1/objects?parent=root.platform.workflows&lite=true"
    ).raise_for_status())
    add(0.03, "list_alert_rules_tree", lambda c: c.request(
        "GET", "/api/v1/objects?parent=root.platform.alert-rules&lite=true"
    ).raise_for_status())
    add(0.03, "list_correlators_tree", lambda c: c.request(
        "GET", "/api/v1/objects?parent=root.platform.correlators&lite=true"
    ).raise_for_status())
    add(0.02, "list_reports_tree", lambda c: c.request(
        "GET", "/api/v1/objects?parent=root.platform.reports&lite=true"
    ).raise_for_status())
    add(0.02, "list_schedules_tree", lambda c: c.request(
        "GET", "/api/v1/objects?parent=root.platform.schedules&lite=true"
    ).raise_for_status())

    # Device / dashboard reads
    add(0.06, "get_object", lambda c: c.request(
        "GET", f"/api/v1/objects/by-path?path={quote(pick_device(), safe='')}"
    ).raise_for_status())
    add(0.06, "list_variables", lambda c: c.request(
        "GET", f"/api/v1/objects/by-path/variables?path={quote(pick_device(), safe='')}"
    ).raise_for_status())
    add(0.04, "variable_detail", lambda c: c.request(
        "GET",
        f"/api/v1/objects/by-path/variables/detail?path={quote(pick_device(), safe='')}&name=sineWave",
    ).raise_for_status())
    add(0.04, "variable_history", lambda c: c.request(
        "GET",
        f"/api/v1/objects/by-path/variables/history?path={quote(pick_device(), safe='')}"
        f"&name=sineWave&limit=30",
    ).raise_for_status())
    add(0.04, "get_dashboard", lambda c: c.request(
        "GET", f"/api/v1/dashboards/by-path?path={quote(pick_dashboard(), safe='')}"
    ).raise_for_status())

    # Automation reads
    if alert_rule_paths:
        add(0.04, "list_alert_rules", lambda c: c.request("GET", "/api/v1/alert-rules").raise_for_status())
        add(0.04, "get_alert_rule", lambda c: c.request(
            "GET", f"/api/v1/alert-rules/by-path?path={quote(pick_alert(), safe='')}"
        ).raise_for_status())
    if correlator_paths:
        add(0.04, "list_correlators", lambda c: c.request("GET", "/api/v1/correlators").raise_for_status())
        add(0.04, "get_correlator", lambda c: c.request(
            "GET", f"/api/v1/correlators/by-path?path={quote(pick_correlator(), safe='')}"
        ).raise_for_status())
    if workflow_paths:
        add(0.05, "get_workflow", lambda c: c.request(
            "GET", f"/api/v1/workflows/by-path?path={quote(pick_workflow(), safe='')}"
        ).raise_for_status())
    if report_paths:
        add(0.03, "get_report", lambda c: c.request(
            "GET", f"/api/v1/reports/by-path?path={quote(pick_report(), safe='')}"
        ).raise_for_status())

    # Automation / device writes (drive alerts, correlators, workflows)
    add(0.08, "write_variable", lambda c: c.request(
        "PUT",
        f"/api/v1/objects/by-path/variables?path={quote(pick_device(), safe='')}&name=intValue",
        json={**INT_VALUE_BODY, "rows": [{"value": random.randint(50, 99)}]},
    ).raise_for_status())
    add(0.07, "fire_event", lambda c: c.request(
        "POST",
        f"/api/v1/events/fire?objectPath={quote(pick_device(), safe='')}&eventName=event1",
        json=EVENT1_PAYLOAD,
    ).raise_for_status())
    if workflow_paths:
        add(0.06, "run_workflow", lambda c: c.request(
            "POST",
            f"/api/v1/workflows/by-path/run?path={quote(pick_workflow(), safe='')}"
            f"&triggerObjectPath={quote(pick_device(), safe='')}",
        ).raise_for_status())
    add(0.04, "invoke_calculate", lambda c: c.request(
        "POST",
        f"/api/v1/objects/by-path/functions/invoke?path={quote(pick_device(), safe='')}&name=calculate",
        json={"rows": [{"inputA": random.random() * 10, "inputB": random.random() * 10}]},
    ).raise_for_status())

    # Events, work queue, drivers, platform
    add(0.04, "list_events", lambda c: c.request(
        "GET", "/api/v1/events?limit=30"
    ).raise_for_status())
    add(0.04, "list_events_device", lambda c: c.request(
        "GET", f"/api/v1/events?objectPath={quote(pick_device(), safe='')}&limit=20"
    ).raise_for_status())
    add(0.04, "work_queue", lambda c: c.request(
        "GET", "/api/v1/work-queue?limit=30"
    ).raise_for_status())
    add(0.03, "driver_status", lambda c: c.request(
        "GET", f"/api/v1/drivers/runtime/status?devicePath={quote(pick_device(), safe='')}"
    ).raise_for_status())
    add(0.02, "list_models", lambda c: c.request("GET", "/api/v1/models").raise_for_status())
    add(0.02, "function_invocations", lambda c: c.request(
        "GET", "/api/v1/platform/function-invocations?limit=20"
    ).raise_for_status())
    add(0.02, "platform_metrics", lambda c: c.request("GET", "/api/v1/platform/metrics").raise_for_status())
    add(0.01, "info", lambda c: c.request("GET", "/api/v1/info").raise_for_status())
    return ops


def run_phase(
    client_factory: Callable[[], Client],
    ops: list[tuple[str, float, Callable[[Client], None]]],
    concurrency: int,
    duration_sec: float,
) -> PhaseResult:
    names = [n for n, _, _ in ops]
    weights = [w for _, w, _ in ops]
    end = time.perf_counter() + duration_sec
    result = PhaseResult(concurrency=concurrency, duration_sec=duration_sec)
    lock_end = end
    sample_lock = threading.Lock()

    def worker() -> None:
        c = client_factory()
        local = random.Random()
        while time.perf_counter() < lock_end:
            op_name = local.choices(names, weights=weights, k=1)[0]
            fn = next(f for n, _, f in ops if n == op_name)
            t0 = time.perf_counter()
            try:
                fn(c)
                ms = (time.perf_counter() - t0) * 1000
                with sample_lock:
                    result.samples.append(Sample(True, ms, op_name))
            except Exception as exc:
                ms = (time.perf_counter() - t0) * 1000
                status = getattr(getattr(exc, "response", None), "status_code", 0) or 0
                with sample_lock:
                    result.samples.append(Sample(False, ms, op_name, status))

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(worker) for _ in range(concurrency)]
        for f in as_completed(futures):
            f.result()
    result.duration_sec = time.perf_counter() - started
    return result


def fetch_platform_snapshot(client: Client) -> dict:
    out: dict = {}
    try:
        info = client.request("GET", "/api/v1/info").json()
        out["version"] = info.get("version")
    except Exception as exc:
        out["info_error"] = str(exc)
    try:
        metrics = client.request("GET", "/api/v1/platform/metrics").json()
        for section in metrics.get("sections", []):
            sid = section.get("id")
            if sid in ("runtime", "database", "drivers", "variableHistory"):
                out[sid] = section.get("values", {})
    except Exception as exc:
        out["metrics_error"] = str(exc)
    try:
        health = client.request("GET", "/actuator/health").json()
        out["health"] = health.get("status")
    except Exception as exc:
        out["health_error"] = str(exc)
    return out


def print_summary(phase_results: list[dict], snapshot_before: dict, snapshot_after: dict) -> None:
    print("\n" + "=" * 72)
    print("LOAD TEST SUMMARY")
    print("=" * 72)
    print(f"Server version: {snapshot_before.get('version', '?')}")
    print(f"Health before/after: {snapshot_before.get('health')} -> {snapshot_after.get('health')}")
    if "drivers" in snapshot_after:
        d = snapshot_after["drivers"]
        print(f"Active drivers after test: {d.get('activeDrivers')} / {d.get('deviceObjects')} devices")
    print()
    print(f"{'Conc':>5} {'RPS':>8} {'OK':>7} {'Fail':>6} {'Fail%':>7} {'p50':>8} {'p95':>8} {'p99':>8} {'max':>8}")
    print("-" * 72)
    sustainable = 0
    for row in phase_results:
        lat = row["latency_ms"]
        ok = row["fail_rate_pct"] < 2 and lat["p95"] < 3000
        if ok:
            sustainable = row["concurrency"]
        print(
            f"{row['concurrency']:5d} {row['rps']:8.1f} {row['ok']:7d} {row['fail']:6d} "
            f"{row['fail_rate_pct']:6.2f}% {lat['p50']:8.1f} {lat['p95']:8.1f} {lat['p99']:8.1f} {lat['max']:8.1f}"
        )
    print("-" * 72)
    best = max(phase_results, key=lambda r: r["rps"] if r["fail_rate_pct"] < 5 else 0)
    print(f"\nPeak throughput (fail<5%): {best['rps']} req/s at concurrency={best['concurrency']}")
    print(f"Recommended sustainable concurrency: ~{sustainable} clients (fail<2%, p95<3s)")
    print("\nPer-operation p95 (last phase):")
    for op, lat in phase_results[-1].get("by_op", {}).items():
        print(f"  {op:18s} p95={lat['p95']:7.1f}ms avg={lat['avg']:7.1f}ms n={lat.get('n', '?')}")


def main() -> int:
    parser = argparse.ArgumentParser(description="ISPF VPS load test")
    parser.add_argument("--base-url", default="https://ispf.iot-solutions.ru")
    parser.add_argument("--host-header", default="")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--devices", type=int, default=60)
    parser.add_argument("--dashboards", type=int, default=15)
    parser.add_argument("--drivers", type=int, default=20, help="Devices with auto-started virtual driver")
    parser.add_argument("--workflows", type=int, default=50, help="Workflow fixtures to seed")
    parser.add_argument("--alert-rules", type=int, default=200, help="Alert rule fixtures to seed")
    parser.add_argument("--correlators", type=int, default=100, help="Event correlator fixtures to seed")
    parser.add_argument("--reports", type=int, default=20, help="Report object fixtures to seed")
    parser.add_argument("--poll-ms", type=int, default=3000)
    parser.add_argument("--phase-seconds", type=int, default=45)
    parser.add_argument("--concurrency", default="5,10,20,30,40")
    parser.add_argument("--skip-seed", action="store_true", help="Skip device/dashboard seed")
    parser.add_argument("--skip-automation-seed", action="store_true", help="Skip workflow/alert/correlator/report seed")
    parser.add_argument("--seed-only", action="store_true", help="Seed fixtures and exit")
    parser.add_argument("--seed-workers", type=int, default=0, help="Parallel device seed workers (0=auto)")
    parser.add_argument("--repair-legacy", action="store_true", help="Re-apply models on 409 conflicts")
    parser.add_argument("--timeout", type=float, default=30.0)
    args = parser.parse_args()

    admin = Client(args.base_url, args.host_header, args.timeout)
    admin.login(args.username, args.password)

    pad = fixture_pad(args.devices)
    dash_pad = fixture_pad(args.dashboards)
    seed_workers = args.seed_workers or (8 if args.devices >= 1000 else 1)

    snapshot_before = fetch_platform_snapshot(admin)
    print("Before:", json.dumps(snapshot_before, indent=2))

    device_paths: list[str] = [
        f"root.platform.devices.{device_name(i, pad)}" for i in range(1, args.devices + 1)
    ]
    dashboard_paths: list[str] = [
        f"root.platform.dashboards.{dashboard_name(i, dash_pad)}" for i in range(1, args.dashboards + 1)
    ]

    if not args.skip_seed:
        device_paths, dashboard_paths = seed_fixtures(
            admin,
            args.devices,
            args.dashboards,
            args.drivers,
            args.poll_ms,
            seed_workers=seed_workers,
            repair_legacy=args.repair_legacy,
        )
        time.sleep(3)

    workflow_paths: list[str] = []
    alert_rule_paths: list[str] = []
    correlator_paths: list[str] = []
    report_paths: list[str] = []

    if not args.skip_automation_seed:
        workflow_paths, alert_rule_paths, correlator_paths, report_paths = seed_automation(
            admin,
            device_paths,
            args.workflows,
            args.alert_rules,
            args.correlators,
            args.reports,
        )
        time.sleep(3)
    else:
        # Resolve existing loadtest automation paths for skip-automation mode
        for listing, prefix, bucket in (
            ("/api/v1/objects?parent=root.platform.workflows&lite=true", f"{PREFIX}-wf-", workflow_paths),
            ("/api/v1/alert-rules", "loadtest alert", alert_rule_paths),
            ("/api/v1/correlators", "loadtest correlator", correlator_paths),
            ("/api/v1/objects?parent=root.platform.reports&lite=true", f"{PREFIX}-report-", report_paths),
        ):
            try:
                items = admin.request("GET", listing).json()
                if isinstance(items, list):
                    for item in items:
                        if listing.endswith("alert-rules") or listing.endswith("correlators"):
                            name = item.get("name", "")
                            path = item.get("id", "")
                            if "loadtest" in name.lower() and path:
                                bucket.append(path)
                        else:
                            path = item.get("path", "")
                            if prefix.replace(" ", "-") in path or prefix in path:
                                bucket.append(path)
            except Exception:
                pass

    fixtures = Fixtures(
        device_paths=device_paths,
        dashboard_paths=dashboard_paths,
        alert_rule_paths=alert_rule_paths,
        correlator_paths=correlator_paths,
        workflow_paths=workflow_paths,
        report_paths=report_paths,
    )

    if args.seed_only:
        print(
            f"Seed complete: {len(device_paths)} devices, {len(dashboard_paths)} dashboards, "
            f"{len(workflow_paths)} workflows, {len(alert_rule_paths)} alert-rules, "
            f"{len(correlator_paths)} correlators, {len(report_paths)} reports"
        )
        return 0

    if not device_paths:
        print("No device fixtures available", file=sys.stderr)
        return 1

    ops = make_ops(fixtures)
    levels = [int(x.strip()) for x in args.concurrency.split(",") if x.strip()]

    def client_factory() -> Client:
        c = Client(args.base_url, args.host_header, args.timeout)
        c.token = admin.token
        c.session.headers["Authorization"] = f"Bearer {admin.token}"
        return c

    phase_results: list[dict] = []
    print(f"\nRunning load phases: {levels} x {args.phase_seconds}s each...")
    for conc in levels:
        print(f"\n--- concurrency={conc} ---")
        phase = run_phase(client_factory, ops, conc, args.phase_seconds)
        summary = phase.summary()
        phase_results.append(summary)
        lat = summary["latency_ms"]
        print(
            f"  rps={summary['rps']} ok={summary['ok']} fail={summary['fail']} "
            f"fail%={summary['fail_rate_pct']} p50={lat['p50']} p95={lat['p95']} p99={lat['p99']}"
        )
        if summary.get("fail_by_op"):
            print(f"  fail_by_op={summary['fail_by_op']} fail_by_status={summary.get('fail_by_status', {})}")
        time.sleep(3)

    snapshot_after = fetch_platform_snapshot(admin)
    print_summary(phase_results, snapshot_before, snapshot_after)

    report_path = f"deploy/load-test-report-{int(time.time())}.json"
    with open(report_path, "w", encoding="utf-8") as fh:
        json.dump(
            {
                "snapshot_before": snapshot_before,
                "snapshot_after": snapshot_after,
                "phases": phase_results,
                "devices": len(device_paths),
                "dashboards": len(dashboard_paths),
                "workflows": len(workflow_paths),
                "alert_rules": len(alert_rule_paths),
                "correlators": len(correlator_paths),
                "reports": len(report_paths),
                "drivers_started": args.drivers,
            },
            fh,
            indent=2,
        )
    print(f"\nFull report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
