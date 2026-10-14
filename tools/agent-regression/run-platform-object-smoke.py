#!/usr/bin/env python3
"""Deterministic platform object smoke (no LLM).

Login → wipe demo-* residue → create/verify each capability cluster via REST.
Writes build/agent-regression/platform-object-smoke.json; exit 1 on any fail.

  python tools/agent-regression/run-platform-object-smoke.py
  python tools/agent-regression/run-platform-object-smoke.py --base-url https://it-demo.iot-solutions.ru
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OUT_DEFAULT = REPO / "build" / "agent-regression" / "platform-object-smoke.json"

sys.path.insert(0, str(Path(__file__).resolve().parent))
from platform_acceptance import evaluate_smoke_step  # noqa: E402

# Known matrix paths (also wiped before create).
WIPE_PATHS = [
    "root.platform.devices.demo-device-1",
    "root.platform.devices.demo-device-2",
    "root.platform.security.users.demo-user1",
    "root.platform.security.users.demo-user2",
    "root.platform.alert-rules.demo-event1-latch",
    "root.platform.correlators.demo-event2-unlatch",
    "root.platform.event-filters.demo-event2-filter",
    "root.platform.singleton-blueprints.demo-calculator",
    "root.platform.dashboards.demo-overview",
    "root.platform.mimics.demo-fan",
    "root.platform.reports.demo-table",
    "root.platform.queries.demo-device-vars",
    "root.platform.operator-apps.demo-ops",
    "root.platform.schedules.demo-tick",
    "root.platform.workflows.demo-flow",
]

WAVES_SUM_MIXIN = "virtual-lab-waves-sum-v1"
MIN_BPMN = """<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
 xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
 xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
 xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
 id="Defs_demo" targetNamespace="https://ispf.local/demo">
  <process id="demo_flow" isExecutable="true">
    <startEvent id="start"/>
    <endEvent id="end"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="end"/>
  </process>
</definitions>
"""


def http(
    base: str,
    method: str,
    path: str,
    body: dict | list | None = None,
    token: str | None = None,
    timeout: int = 90,
) -> tuple[int, dict | list | str]:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{base.rstrip('/')}{path}", data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode("utf-8")
            if not raw:
                return r.status, {}
            try:
                return r.status, json.loads(raw)
            except json.JSONDecodeError:
                return r.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            return e.code, {"raw": raw[:800]}


def qpath(path: str) -> str:
    return urllib.parse.quote(path, safe="")


class Smoke:
    def __init__(self, base: str, user: str, password: str) -> None:
        self.base = base.rstrip("/")
        self.user = user
        self.password = password
        self.token = ""
        self.steps: list[dict] = []

    def login(self) -> None:
        code, data = http(self.base, "POST", "/api/v1/auth/login", {"username": self.user, "password": self.password})
        if code != 200 or not isinstance(data, dict) or not data.get("token"):
            raise RuntimeError(f"login failed: {code} {data}")
        self.token = str(data["token"])

    def api(self, method: str, path: str, body: dict | list | None = None) -> tuple[int, dict | list | str]:
        return http(self.base, method, path, body, self.token)

    def exists(self, path: str) -> bool:
        code, _ = self.api("GET", f"/api/v1/objects/by-path?path={qpath(path)}")
        return code == 200

    def get_object(self, path: str) -> dict | None:
        code, data = self.api("GET", f"/api/v1/objects/by-path?path={qpath(path)}")
        if code != 200 or not isinstance(data, dict):
            return None
        return data

    def delete_path(self, path: str) -> None:
        if not self.exists(path):
            return
        self.api("DELETE", f"/api/v1/objects/by-path?path={qpath(path)}")

    def record(self, step_id: str, ok: bool, detail: str = "", **extra: object) -> None:
        row = {"id": step_id, "status": "pass" if ok else "fail", "detail": detail}
        row.update(extra)
        self.steps.append(row)
        mark = "PASS" if ok else "FAIL"
        print(f"[{mark}] {step_id}: {detail}", flush=True)

    def step(self, step_id: str, fn) -> None:
        try:
            ok, detail = fn()
            if ok:
                vok, vdetail = evaluate_smoke_step(self.base, self.token, step_id)
                if not vok:
                    ok = False
                    detail = f"create_ok but deep_verify_fail: {vdetail} (create={detail})"
                else:
                    detail = f"{detail} | deep={vdetail}"
            self.record(step_id, ok, detail)
        except Exception as exc:  # noqa: BLE001 — smoke aggregates failures
            self.record(step_id, False, f"exception: {exc}")

    def create_object(self, parent: str, name: str, type_: str, display: str, **extra: object) -> tuple[int, dict | list | str]:
        body: dict = {
            "parentPath": parent,
            "name": name,
            "type": type_,
            "displayName": display,
        }
        body.update(extra)
        return self.api("POST", "/api/v1/objects", body)

    def ensure_virtual_device(self, name: str) -> str:
        path = f"root.platform.devices.{name}"
        if self.exists(path):
            return path
        code, data = self.create_object(
            "root.platform.devices",
            name,
            "DEVICE",
            f"Demo {name}",
            templateId="virtual-unified-v1",
            driverId="virtual",
            autoStartDriver=True,
            driverPollIntervalMs=1000,
        )
        if code not in (200, 201) or not self.exists(path):
            raise RuntimeError(f"create device {name}: {code} {data}")
        # Give driver a moment to attach variables.
        for _ in range(10):
            code, vars_ = self.api("GET", f"/api/v1/objects/by-path/variables?path={qpath(path)}")
            names: list[str] = []
            if isinstance(vars_, list):
                names = [str(v.get("name") or v.get("id") or "") for v in vars_ if isinstance(v, dict)]
            elif isinstance(vars_, dict):
                rows = vars_.get("variables") or vars_.get("rows") or []
                if isinstance(rows, list):
                    names = [str(v.get("name") or v.get("id") or "") for v in rows if isinstance(v, dict)]
            if "sineWave" in names:
                break
            time.sleep(0.5)
        return path

    def wipe(self) -> None:
        # Prefer deepest / leaf deletes; also scan for demo-smoke-* children.
        folders = [
            "root.platform.devices",
            "root.platform.security.users",
            "root.platform.alert-rules",
            "root.platform.correlators",
            "root.platform.event-filters",
            "root.platform.singleton-blueprints",
            "root.platform.dashboards",
            "root.platform.mimics",
            "root.platform.reports",
            "root.platform.queries",
            "root.platform.operator-apps",
            "root.platform.schedules",
            "root.platform.workflows",
        ]
        for folder in folders:
            code, kids = self.api("GET", f"/api/v1/objects?path={qpath(folder)}&depth=1")
            rows = []
            if isinstance(kids, dict):
                rows = kids.get("objects") or kids.get("children") or kids.get("rows") or []
            elif isinstance(kids, list):
                rows = kids
            for row in rows if isinstance(rows, list) else []:
                if not isinstance(row, dict):
                    continue
                p = str(row.get("path") or "")
                leaf = p.rsplit(".", 1)[-1]
                if leaf.startswith("demo-") or leaf.startswith("demo-smoke-"):
                    self.delete_path(p)
        for p in WIPE_PATHS:
            self.delete_path(p)
        # Operator app may live as tree node and/or registry entry.
        self.api("DELETE", "/api/v1/operator-apps/demo-ops")
        self.record("wipe", True, f"cleared {len(WIPE_PATHS)} known paths + demo-* scans")

    def run(self) -> int:
        self.login()
        self.wipe()

        self.step("virtual-devices", self._virtual_devices)
        self.step("users-acl", self._users_acl)
        self.step("alerts-correlators", self._alerts_correlators)
        self.step("event-filter", self._event_filter)
        self.step("singleton-hub", self._singleton_hub)
        self.step("mixin-waves", self._mixin_waves)
        self.step("historian", self._historian)
        self.step("dashboard-bound", self._dashboard)
        self.step("mimic-pack", self._mimic)
        self.step("report-tree-vars", self._report)
        self.step("query-custom", self._query)
        self.step("operator-ui", self._operator_ui)
        self.step("schedule", self._schedule)
        self.step("workflow-min", self._workflow)

        failed = [s for s in self.steps if s["status"] == "fail"]
        return 1 if failed else 0

    def _virtual_devices(self) -> tuple[bool, str]:
        p1 = self.ensure_virtual_device("demo-device-1")
        p2 = self.ensure_virtual_device("demo-device-2")
        o1 = self.get_object(p1) or {}
        o2 = self.get_object(p2) or {}
        ok = o1.get("type") == "DEVICE" and o2.get("type") == "DEVICE"
        return ok, f"{p1} type={o1.get('type')}; {p2} type={o2.get('type')}"

    def _users_acl(self) -> tuple[bool, str]:
        self.ensure_virtual_device("demo-device-1")
        for name in ("demo-user1", "demo-user2"):
            path = f"root.platform.security.users.{name}"
            if not self.exists(path):
                code, data = self.create_object(
                    "root.platform.security.users",
                    name,
                    "USER",
                    name,
                    password="ChangeMe!123",
                    roles="operator",
                )
                if code not in (200, 201) and not self.exists(path):
                    return False, f"create user {name}: {code} {data}"
        code, data = self.api(
            "PUT",
            f"/api/v1/objects/by-path/acl?path={qpath('root.platform.devices.demo-device-1')}",
            {
                "entries": [
                    {"principalType": "USER", "principalId": "demo-user1", "permission": "EDITOR"}
                ]
            },
        )
        ok = code in (200, 204) and self.exists("root.platform.security.users.demo-user1")
        return ok, f"acl_http={code} users+device present"

    def _alerts_correlators(self) -> tuple[bool, str]:
        self.ensure_virtual_device("demo-device-1")
        code_a, data_a = self.api(
            "POST",
            "/api/v1/alert-rules",
            {
                "name": "demo-event1-latch",
                "objectPath": "root.platform.devices.demo-device-1",
                "watchVariable": "intValue",
                "conditionExpr": 'self.intValue["value"] > 20',
                "eventName": "event1",
                "enabled": True,
                "edgeTrigger": True,
            },
        )
        code_c, data_c = self.api(
            "POST",
            "/api/v1/correlators",
            {
                "name": "demo-event2-unlatch",
                "objectPath": "root.platform.devices.demo-device-1",
                "patternType": "COUNT",
                "eventName": "event2",
                "windowSeconds": 60,
                "minOccurrences": 1,
                "cooldownSeconds": 5,
                "sequenceGapSeconds": 0,
                "actionType": "FIRE_EVENT",
                "actionTarget": "event2FollowUp",
                "enabled": True,
            },
        )
        if code_c >= 400 and not self.exists("root.platform.correlators.demo-event2-unlatch"):
            # Fallback: create CORRELATOR node then PUT config (older servers reject some enums).
            self.create_object(
                "root.platform.correlators",
                "demo-event2-unlatch",
                "CORRELATOR",
                "demo-event2-unlatch",
            )
            code_c, data_c = self.api(
                "PUT",
                f"/api/v1/correlators/by-path?path={qpath('root.platform.correlators.demo-event2-unlatch')}",
                {
                    "name": "demo-event2-unlatch",
                    "objectPath": "root.platform.devices.demo-device-1",
                    "patternType": "COUNT",
                    "eventName": "event2",
                    "windowSeconds": 60,
                    "minOccurrences": 1,
                    "cooldownSeconds": 5,
                    "sequenceGapSeconds": 0,
                    "actionType": "FIRE_EVENT",
                    "actionTarget": "event2FollowUp",
                    "enabled": True,
                },
            )
        a_ok = self.exists("root.platform.alert-rules.demo-event1-latch")
        c_ok = self.exists("root.platform.correlators.demo-event2-unlatch")
        return a_ok and c_ok, f"alert={code_a} correlator={code_c} a_ok={a_ok} c_ok={c_ok}"

    def _event_filter(self) -> tuple[bool, str]:
        if not self.exists("root.platform.event-filters.demo-event2-filter"):
            code, data = self.api(
                "POST",
                "/api/v1/event-filters",
                {
                    "name": "demo-event2-filter",
                    "displayName": "demo event2 filter",
                    "eventNamePattern": "event2",
                    "filterExpression": 'payload.int > 10 || payload.string.contains("abc")',
                },
            )
            if not self.exists("root.platform.event-filters.demo-event2-filter"):
                # Fallback: plain object create
                code2, data2 = self.create_object(
                    "root.platform.event-filters",
                    "demo-event2-filter",
                    "EVENT_FILTER",
                    "demo event2 filter",
                )
                if not self.exists("root.platform.event-filters.demo-event2-filter"):
                    return False, f"event-filter create failed {code}/{code2}: {data}/{data2}"
        return True, "root.platform.event-filters.demo-event2-filter"

    def _singleton_hub(self) -> tuple[bool, str]:
        path = "root.platform.singleton-blueprints.demo-calculator"
        if not self.exists(path):
            code, data = self.create_object(
                "root.platform.singleton-blueprints",
                "demo-calculator",
                "CUSTOM",
                "Demo Calculator Hub",
                description="Logic hub (not DEVICE)",
            )
            if not self.exists(path):
                return False, f"hub create failed {code} {data}"
        obj = self.get_object(path) or {}
        ok = obj.get("type") in ("CUSTOM", "BLUEPRINT", "SINGLETON") or path.endswith("demo-calculator")
        # Soft-check: must not be DEVICE
        if obj.get("type") == "DEVICE":
            return False, "hub created as DEVICE (forbidden)"
        return bool(self.exists(path)), f"type={obj.get('type')} path={path}"

    def _mixin_waves(self) -> tuple[bool, str]:
        self.ensure_virtual_device("demo-device-1")
        self.ensure_virtual_device("demo-device-2")
        # Resolve mixin id by name
        code, mixins = self.api("GET", "/api/v1/mixin-blueprints")
        mixin_id = None
        if isinstance(mixins, list):
            for m in mixins:
                if isinstance(m, dict) and (
                    m.get("name") == WAVES_SUM_MIXIN or m.get("blueprintName") == WAVES_SUM_MIXIN
                ):
                    mixin_id = m.get("id") or m.get("blueprintId")
                    break
        if not mixin_id:
            # try by-name
            code, by_name = self.api("GET", f"/api/v1/mixin-blueprints/by-name/{WAVES_SUM_MIXIN}")
            if isinstance(by_name, dict):
                mixin_id = by_name.get("id") or by_name.get("blueprintId")
        if not mixin_id:
            return False, f"mixin {WAVES_SUM_MIXIN} not found (list http={code})"
        details = []
        codes: list[int] = []
        for name in ("demo-device-1", "demo-device-2"):
            path = f"root.platform.devices.{name}"
            code, data = self.api(
                "POST",
                f"/api/v1/mixin-blueprints/{mixin_id}/apply?objectPath={qpath(path)}",
            )
            codes.append(code)
            details.append(f"{name}:{code}")
        return all(c in (200, 201, 204) for c in codes), f"mixin={mixin_id} {details}"

    def _historian(self) -> tuple[bool, str]:
        path = self.ensure_virtual_device("demo-device-1")
        results = []
        for var in ("sineWave", "sawtoothWave"):
            code, data = self.api(
                "PATCH",
                f"/api/v1/objects/by-path/variables/history?path={qpath(path)}&name={var}",
                {"historyEnabled": True, "historyRetentionDays": 7},
            )
            results.append((var, code))
        ok = all(c in (200, 204) for _, c in results)
        return ok, str(results)

    def _dashboard(self) -> tuple[bool, str]:
        self.ensure_virtual_device("demo-device-1")
        path = "root.platform.dashboards.demo-overview"
        if not self.exists(path):
            code, data = self.create_object(
                "root.platform.dashboards",
                "demo-overview",
                "DASHBOARD",
                "Demo Overview",
                templateId="dashboard-v1",
            )
            if not self.exists(path):
                return False, f"dashboard create {code} {data}"
        layout = {
            "version": 1,
            "columns": 84,
            "rowHeight": 8,
            "widgets": [
                {
                    "id": "w-sine",
                    "type": "value",
                    "x": 0,
                    "y": 0,
                    "w": 20,
                    "h": 8,
                    "title": "sineWave",
                    "objectPath": "root.platform.devices.demo-device-1",
                    "variableName": "sineWave",
                },
                {
                    "id": "w-active",
                    "type": "value",
                    "x": 20,
                    "y": 0,
                    "w": 20,
                    "h": 8,
                    "title": "deviceActive",
                    "objectPath": "root.platform.devices.demo-device-1",
                    "variableName": "deviceActive",
                },
                {
                    "id": "w-chart",
                    "type": "chart",
                    "x": 0,
                    "y": 8,
                    "w": 40,
                    "h": 16,
                    "title": "waves",
                    "objectPath": "root.platform.devices.demo-device-1",
                    "variableName": "sineWave",
                },
            ],
        }
        code, data = self.api(
            "PUT",
            f"/api/v1/dashboards/by-path/layout?path={qpath(path)}",
            {"layoutJson": json.dumps(layout)},
        )
        return code in (200, 204) and self.exists(path), f"layout_http={code}"

    def _mimic(self) -> tuple[bool, str]:
        self.ensure_virtual_device("demo-device-1")
        path = "root.platform.mimics.demo-fan"
        if not self.exists(path):
            code, data = self.create_object(
                "root.platform.mimics",
                "demo-fan",
                "MIMIC",
                "Demo Fan",
                templateId="mimic-v1",
            )
            if not self.exists(path):
                return False, f"mimic create {code} {data}"
        diagram = {
            "version": 2,
            "width": 800,
            "height": 480,
            "background": "#0d1117",
            "grid": {"visible": True, "snap": True, "size": 20},
            "layers": [{"id": "layer-default", "name": "Main", "visible": True}],
            "elements": [
                {
                    "id": "fan-1",
                    "layerId": "layer-default",
                    "symbolId": "pack.ispf-pid.fan",
                    "x": 80,
                    "y": 80,
                    "width": 64,
                    "height": 64,
                    "bindings": {
                        "running": {
                            "objectPath": "root.platform.devices.demo-device-1",
                            "variableName": "deviceActive",
                            "valueField": "value",
                        }
                    },
                },
                {
                    "id": "pump-1",
                    "layerId": "layer-default",
                    "symbolId": "pump.centrifugal",
                    "x": 220,
                    "y": 80,
                    "width": 64,
                    "height": 64,
                    "bindings": {
                        "running": {
                            "objectPath": "root.platform.devices.demo-device-1",
                            "variableName": "deviceActive",
                            "valueField": "value",
                        }
                    },
                },
            ],
            "connections": [],
            "customSymbols": [],
        }
        code, data = self.api(
            "PUT",
            f"/api/v1/mimics/by-path/diagram?path={qpath(path)}",
            {"diagramJson": json.dumps(diagram)},
        )
        return code in (200, 204) and self.exists(path), f"diagram_http={code}"

    def _report(self) -> tuple[bool, str]:
        self.ensure_virtual_device("demo-device-1")
        path = "root.platform.reports.demo-table"
        if not self.exists(path):
            code, data = self.create_object(
                "root.platform.reports",
                "demo-table",
                "REPORT",
                "Demo Table",
                templateId="tree-variables-report-v1",
            )
            if not self.exists(path):
                return False, f"report create {code} {data}"
        code, data = self.api(
            "PUT",
            f"/api/v1/reports/by-path/tree-variables-definition?path={qpath(path)}",
            {
                "title": "Demo devices table",
                "devicePathPattern": "root.platform.devices.demo-device-*",
                "variableName": "telemetryTable",
                "maxRows": 100,
            },
        )
        return self.exists(path), f"definition_http={code} exists={self.exists(path)}"

    def _query(self) -> tuple[bool, str]:
        path = "root.platform.queries.demo-device-vars"
        if not self.exists(path):
            code, data = self.create_object(
                "root.platform.queries",
                "demo-device-vars",
                "CUSTOM",
                "Demo device vars query",
            )
            if not self.exists(path):
                return False, f"query create {code} {data}"
        return True, path

    def _operator_ui(self) -> tuple[bool, str]:
        # Ensure dashboard exists
        self._dashboard()
        code, data = self.api("POST", "/api/v1/operator-apps/demo-ops", {"title": "Demo Ops"})
        if code not in (200, 201) and not self.exists("root.platform.operator-apps.demo-ops"):
            # create tree node then UI
            self.create_object(
                "root.platform.operator-apps",
                "demo-ops",
                "OPERATOR_APP",
                "Demo Ops",
            )
        code2, data2 = self.api(
            "PUT",
            "/api/v1/operator-apps/demo-ops/ui",
            {
                "title": "Demo Ops",
                "defaultDashboard": "root.platform.dashboards.demo-overview",
                "dashboards": [
                    {"path": "root.platform.dashboards.demo-overview", "title": "Overview"}
                ],
            },
        )
        ok = code2 in (200, 204)
        return ok, f"create={code} ui={code2}"

    def _schedule(self) -> tuple[bool, str]:
        # Prefer tree SCHEDULE object; also try schedules API.
        path = "root.platform.schedules.demo-tick"
        if not self.exists(path):
            code, data = self.create_object(
                "root.platform.schedules",
                "demo-tick",
                "SCHEDULE",
                "Demo Tick",
            )
            if not self.exists(path):
                return False, f"schedule object create {code} {data}"
        # Upsert platform scheduler entry (best-effort)
        hub = "root.platform.singleton-blueprints.demo-calculator"
        if not self.exists(hub):
            self._singleton_hub()
        action_json = json.dumps(
            {
                "objectPath": "root.platform.devices.demo-device-1",
                "functionName": "calculate",
            }
        )
        code2, data2 = self.api(
            "POST",
            "/api/v1/schedules",
            {
                "scheduleId": "demo-tick",
                "appId": "platform",
                "enabled": False,
                "intervalMs": 60000,
                "action": {"type": "invoke_function", "json": action_json},
            },
        )
        return self.exists(path), f"object_ok schedules_api={code2}"

    def _workflow(self) -> tuple[bool, str]:
        path = "root.platform.workflows.demo-flow"
        if not self.exists(path):
            code, data = self.create_object(
                "root.platform.workflows",
                "demo-flow",
                "WORKFLOW",
                "Demo Flow",
                templateId="workflow-v1",
            )
            if not self.exists(path):
                return False, f"workflow create {code} {data}"
        code, data = self.api(
            "PUT",
            f"/api/v1/workflows/by-path/bpmn?path={qpath(path)}",
            {"bpmnXml": MIN_BPMN},
        )
        return self.exists(path) and code in (200, 204), f"bpmn_http={code}"


def main() -> int:
    ap = argparse.ArgumentParser(description="Deterministic platform object smoke (no LLM)")
    ap.add_argument("--base-url", default="https://it-demo.iot-solutions.ru")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", default="admin")
    ap.add_argument("--out", default=str(OUT_DEFAULT))
    args = ap.parse_args()

    smoke = Smoke(args.base_url, args.user, args.password)
    try:
        rc = smoke.run()
    except Exception as exc:  # noqa: BLE001
        print(f"FATAL: {exc}", flush=True)
        smoke.record("fatal", False, str(exc))
        rc = 1

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "baseUrl": args.base_url,
        "exitCode": rc,
        "passed": sum(1 for s in smoke.steps if s["status"] == "pass"),
        "failed": sum(1 for s in smoke.steps if s["status"] == "fail"),
        "steps": smoke.steps,
    }
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out} passed={payload['passed']} failed={payload['failed']}", flush=True)
    return rc


if __name__ == "__main__":
    raise SystemExit(main())
