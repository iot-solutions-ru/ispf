#!/usr/bin/env python3
"""Strict live acceptance for platform demo matrix (no path-only fake passes)."""
from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Callable

FORBIDDEN_MIMIC_SYMBOLS = frozenset(
    {
        "label",
        "button.start",
        "button.stop",
        "button",
        "text",
        "custom.svg",
    }
)

# At least one element must use a real PID / pack symbol (not UI chrome).
GOOD_MIMIC_PREFIXES = ("pack.", "pump.", "valve.", "fan.", "tank.", "motor.", "ispf-pid.")


def http(
    base: str,
    method: str,
    path: str,
    body: dict | list | None = None,
    token: str | None = None,
    timeout: int = 90,
) -> tuple[int, Any]:
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


def get_object(base: str, token: str, path: str) -> dict | None:
    code, data = http(base, "GET", f"/api/v1/objects/by-path?path={qpath(path)}", token=token)
    return data if code == 200 and isinstance(data, dict) else None


def list_variables(base: str, token: str, path: str) -> list[dict]:
    code, data = http(base, "GET", f"/api/v1/objects/by-path/variables?path={qpath(path)}", token=token)
    if code != 200:
        return []
    if isinstance(data, list):
        return [v for v in data if isinstance(v, dict)]
    if isinstance(data, dict):
        rows = data.get("variables") or data.get("rows") or data.get("items") or []
        if isinstance(rows, list):
            return [v for v in rows if isinstance(v, dict)]
    return []


def var_map(base: str, token: str, path: str) -> dict[str, dict]:
    return {str(v.get("name")): v for v in list_variables(base, token, path) if v.get("name")}


def get_dashboard_layout(base: str, token: str, path: str) -> dict | None:
    # GET layout is on /dashboards/by-path (PUT uses .../layout).
    code, data = http(base, "GET", f"/api/v1/dashboards/by-path?path={qpath(path)}", token=token)
    if code != 200:
        return None
    layout: Any = data
    if isinstance(data, dict):
        layout = data.get("layout") or data.get("layoutJson") or data
    if isinstance(layout, str):
        try:
            layout = json.loads(layout)
        except json.JSONDecodeError:
            return None
    return layout if isinstance(layout, dict) else None


def get_mimic_diagram(base: str, token: str, path: str) -> dict | None:
    # GET diagram is on /mimics/by-path (PUT uses .../diagram).
    code, data = http(base, "GET", f"/api/v1/mimics/by-path?path={qpath(path)}", token=token)
    if code != 200:
        return None
    diagram: Any = data
    if isinstance(data, dict):
        diagram = data.get("diagram") or data.get("diagramJson") or data
    if isinstance(diagram, str):
        try:
            diagram = json.loads(diagram)
        except json.JSONDecodeError:
            return None
    return diagram if isinstance(diagram, dict) else None


def get_workflow_bpmn(base: str, token: str, path: str) -> str:
    code, data = http(base, "GET", f"/api/v1/workflows/by-path?path={qpath(path)}", token=token)
    if code != 200 or not isinstance(data, dict):
        code2, data2 = http(base, "GET", f"/api/v1/workflows/by-path/bpmn?path={qpath(path)}", token=token)
        if code2 == 200:
            if isinstance(data2, dict):
                return str(data2.get("bpmnXml") or data2.get("bpmn") or "")
            return str(data2)
        return ""
    return str(data.get("bpmnXml") or data.get("bpmn") or "")


def get_operator_ui(base: str, token: str, app_id: str) -> dict | None:
    code, data = http(base, "GET", f"/api/v1/operator-apps/{app_id}/ui", token=token)
    return data if code == 200 and isinstance(data, dict) else None


def get_acl_entries(base: str, token: str, path: str) -> list[dict]:
    code, data = http(base, "GET", f"/api/v1/objects/by-path/acl?path={qpath(path)}", token=token)
    if code != 200 or not isinstance(data, dict):
        return []
    entries = data.get("entries") or []
    return [e for e in entries if isinstance(e, dict)] if isinstance(entries, list) else []


def widget_is_bound(w: dict) -> bool:
    if w.get("objectPath") and w.get("variableName"):
        return True
    bindings = w.get("bindings")
    if isinstance(bindings, dict) and bindings:
        return True
    series = w.get("series")
    if isinstance(series, list) and series:
        for s in series:
            if isinstance(s, dict) and s.get("objectPath") and s.get("variableName"):
                return True
    return False


CheckFn = Callable[[str, str], tuple[bool, str]]


def check_device(base: str, token: str, path: str = "root.platform.devices.demo-device-1") -> tuple[bool, str]:
    obj = get_object(base, token, path)
    if not obj:
        return False, f"missing {path}"
    if obj.get("type") != "DEVICE":
        return False, f"{path} type={obj.get('type')} want DEVICE"
    vars_ = var_map(base, token, path)
    need = ("sineWave", "deviceActive")
    missing = [n for n in need if n not in vars_]
    if missing:
        return False, f"{path} missing vars {missing}"
    return True, f"{path} DEVICE vars={len(vars_)}"


def check_two_devices(base: str, token: str) -> tuple[bool, str]:
    details = []
    for name in ("demo-device-1", "demo-device-2"):
        ok, detail = check_device(base, token, f"root.platform.devices.{name}")
        details.append(detail)
        if not ok:
            return False, "; ".join(details)
    return True, "; ".join(details)


def check_users_acl(base: str, token: str) -> tuple[bool, str]:
    for u in ("demo-user1", "demo-user2"):
        if not get_object(base, token, f"root.platform.security.users.{u}"):
            return False, f"missing user {u}"
    ok_dev, detail_dev = check_device(base, token)
    if not ok_dev:
        return False, detail_dev
    entries = get_acl_entries(base, token, "root.platform.devices.demo-device-1")
    hit = any(
        e.get("principalType") == "USER"
        and e.get("principalId") == "demo-user1"
        and str(e.get("permission") or "").upper() in ("EDITOR", "WRITE", "OWNER")
        for e in entries
    )
    if not hit:
        return False, f"ACL missing demo-user1 EDITOR; entries={entries}"
    return True, f"users+ACL ok entries={len(entries)}"


def check_alert_correlator(base: str, token: str) -> tuple[bool, str]:
    a = get_object(base, token, "root.platform.alert-rules.demo-event1-latch")
    c = get_object(base, token, "root.platform.correlators.demo-event2-unlatch")
    if not a:
        return False, "missing alert demo-event1-latch"
    if not c:
        return False, "missing correlator demo-event2-unlatch"
    if a.get("type") != "ALERT":
        return False, f"alert type={a.get('type')} want ALERT"
    if c.get("type") != "CORRELATOR":
        return False, f"correlator type={c.get('type')} want CORRELATOR"
    ok_dev, detail = check_device(base, token)
    if not ok_dev:
        return False, detail
    return True, "alert+correlator+device ok"


def check_event_filter(base: str, token: str) -> tuple[bool, str]:
    obj = get_object(base, token, "root.platform.event-filters.demo-event2-filter")
    if not obj:
        return False, "missing event-filter"
    if obj.get("type") != "EVENT_FILTER":
        return False, f"event-filter type={obj.get('type')} want EVENT_FILTER"
    return True, f"event-filter type={obj.get('type')}"


def check_singleton_hub(base: str, token: str) -> tuple[bool, str]:
    path = "root.platform.singleton-blueprints.demo-calculator"
    obj = get_object(base, token, path)
    if not obj:
        return False, f"missing {path}"
    if obj.get("type") == "DEVICE":
        return False, "hub is DEVICE (forbidden)"
    if not path.startswith("root.platform.singleton-blueprints."):
        return False, "hub not under singleton-blueprints"
    return True, f"hub type={obj.get('type')}"


def check_mixin_waves(base: str, token: str) -> tuple[bool, str]:
    details = []
    for name in ("demo-device-1", "demo-device-2"):
        path = f"root.platform.devices.{name}"
        ok, detail = check_device(base, token, path)
        if not ok:
            return False, detail
        vars_ = var_map(base, token, path)
        if "sumWaves" not in vars_:
            return False, f"{path} missing sumWaves after mixin"
        details.append(f"{name}:sumWaves")
    return True, ", ".join(details)


def check_historian(base: str, token: str) -> tuple[bool, str]:
    path = "root.platform.devices.demo-device-1"
    ok, detail = check_device(base, token, path)
    if not ok:
        return False, detail
    vars_ = var_map(base, token, path)
    bad = []
    for name in ("sineWave", "sawtoothWave"):
        v = vars_.get(name) or {}
        if not v.get("historyEnabled"):
            bad.append(name)
    if bad:
        return False, f"historyEnabled=false for {bad}"
    return True, "sineWave+sawtoothWave historyEnabled"


def check_dashboard_bound(base: str, token: str) -> tuple[bool, str]:
    path = "root.platform.dashboards.demo-overview"
    if not get_object(base, token, path):
        return False, f"missing {path}"
    layout = get_dashboard_layout(base, token, path)
    if not layout:
        return False, "no layout"
    widgets = layout.get("widgets") or []
    if not isinstance(widgets, list) or len(widgets) < 2:
        return False, f"widgetCount={len(widgets) if isinstance(widgets, list) else 0} want>=2"
    cols = layout.get("columns")
    rh = layout.get("rowHeight")
    if cols is not None and int(cols) < 24:
        return False, f"columns={cols} too small"
    if rh is not None and int(rh) < 4:
        return False, f"rowHeight={rh} too small"
    unbound = []
    bound_ok = 0
    for w in widgets:
        if not isinstance(w, dict):
            continue
        wtype = str(w.get("type") or "")
        if wtype in ("label", "markdown", "spacer", "text"):
            continue
        if widget_is_bound(w):
            bound_ok += 1
        else:
            unbound.append(w.get("id") or wtype)
    if bound_ok < 2:
        return False, f"boundWidgets={bound_ok} unbound={unbound}"
    # require at least one binding to demo-device-1
    bound_to_demo = False
    for w in widgets:
        if isinstance(w, dict) and "demo-device-1" in str(w.get("objectPath") or ""):
            bound_to_demo = True
            break
    if not bound_to_demo:
        return False, "no widget bound to demo-device-1"
    return True, f"widgets={len(widgets)} bound={bound_ok} cols={cols} rh={rh}"


def check_mimic_pack(base: str, token: str) -> tuple[bool, str]:
    path = "root.platform.mimics.demo-fan"
    if not get_object(base, token, path):
        return False, f"missing {path}"
    diagram = get_mimic_diagram(base, token, path)
    if not diagram:
        return False, "no diagram"
    elements = diagram.get("elements") or []
    if not isinstance(elements, list) or len(elements) < 1:
        return False, "elementCount=0"
    symbols = [str(e.get("symbolId") or "") for e in elements if isinstance(e, dict)]
    forbidden = [s for s in symbols if s.lower() in FORBIDDEN_MIMIC_SYMBOLS or s.startswith("button.")]
    if forbidden:
        return False, f"forbidden symbolIds={forbidden}"
    good = [s for s in symbols if s.startswith(GOOD_MIMIC_PREFIXES) or s in ("pump.centrifugal",)]
    if not good:
        return False, f"no pack/PID symbols; got {symbols}"
    # require a binding on at least one element
    bound = False
    for e in elements:
        if not isinstance(e, dict):
            continue
        b = e.get("bindings")
        if isinstance(b, dict) and b:
            bound = True
            break
    if not bound:
        return False, "no element bindings"
    return True, f"elements={len(elements)} good={good}"


def check_report(base: str, token: str) -> tuple[bool, str]:
    path = "root.platform.reports.demo-table"
    obj = get_object(base, token, path)
    if not obj:
        return False, f"missing {path}"
    if obj.get("type") != "REPORT":
        return False, f"type={obj.get('type')}"
    return True, "report ok"


def check_query(base: str, token: str) -> tuple[bool, str]:
    path = "root.platform.queries.demo-device-vars"
    obj = get_object(base, token, path)
    if not obj:
        return False, f"missing {path}"
    return True, f"query type={obj.get('type')}"


def check_operator_ui(base: str, token: str) -> tuple[bool, str]:
    ui = get_operator_ui(base, token, "demo-ops")
    if not ui:
        # tree node alone is not enough
        if get_object(base, token, "root.platform.operator-apps.demo-ops"):
            return False, "operator app node exists but /ui missing defaultDashboard"
        return False, "missing operator app demo-ops"
    default = ui.get("defaultDashboard") or ui.get("defaultDashboardPath")
    if not default:
        return False, f"no defaultDashboard in ui keys={list(ui.keys())[:12]}"
    if "demo-overview" not in str(default):
        return False, f"defaultDashboard={default} want demo-overview"
    ok_dash, detail = check_dashboard_bound(base, token)
    if not ok_dash:
        return False, f"operator ok but dashboard weak: {detail}"
    return True, f"defaultDashboard={default}"


def check_schedule(base: str, token: str) -> tuple[bool, str]:
    path = "root.platform.schedules.demo-tick"
    obj = get_object(base, token, path)
    if not obj:
        return False, f"missing {path}"
    if obj.get("type") != "SCHEDULE":
        return False, f"type={obj.get('type')} want SCHEDULE"
    return True, f"schedule type={obj.get('type')}"


def check_workflow(base: str, token: str) -> tuple[bool, str]:
    path = "root.platform.workflows.demo-flow"
    obj = get_object(base, token, path)
    if not obj:
        return False, f"missing {path}"
    if obj.get("type") != "WORKFLOW":
        return False, f"type={obj.get('type')}"
    bpmn = get_workflow_bpmn(base, token, path)
    if not bpmn or "startEvent" not in bpmn:
        return False, f"bpmn missing/empty len={len(bpmn)}"
    return True, f"workflow bpmnLen={len(bpmn)}"


SCENARIO_CHECKS: dict[str, CheckFn] = {
    "platform-virtual-devices": check_two_devices,
    "platform-users-acl": check_users_acl,
    "platform-alerts-correlators": check_alert_correlator,
    "platform-event-filter": check_event_filter,
    "platform-singleton-hub": check_singleton_hub,
    "platform-mixin-waves": check_mixin_waves,
    "platform-historian": check_historian,
    "platform-dashboard-bound": check_dashboard_bound,
    "platform-mimic-pack": check_mimic_pack,
    "platform-report-tree-vars": check_report,
    "platform-query-custom": check_query,
    "platform-operator-ui": check_operator_ui,
    "platform-schedule": check_schedule,
    "platform-workflow-min": check_workflow,
}

# Map smoke step ids → same deep checks
SMOKE_CHECKS: dict[str, CheckFn] = {
    "virtual-devices": check_two_devices,
    "users-acl": check_users_acl,
    "alerts-correlators": check_alert_correlator,
    "event-filter": check_event_filter,
    "singleton-hub": check_singleton_hub,
    "mixin-waves": check_mixin_waves,
    "historian": check_historian,
    "dashboard-bound": check_dashboard_bound,
    "mimic-pack": check_mimic_pack,
    "report-tree-vars": check_report,
    "query-custom": check_query,
    "operator-ui": check_operator_ui,
    "schedule": check_schedule,
    "workflow-min": check_workflow,
}


def evaluate_scenario(base: str, token: str, scenario_id: str) -> tuple[bool, str]:
    fn = SCENARIO_CHECKS.get(scenario_id)
    if not fn:
        return False, f"no deep check registered for {scenario_id}"
    return fn(base, token)


def evaluate_smoke_step(base: str, token: str, step_id: str) -> tuple[bool, str]:
    fn = SMOKE_CHECKS.get(step_id)
    if not fn:
        return True, "no deep check"
    return fn(base, token)
