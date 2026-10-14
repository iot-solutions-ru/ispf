#!/usr/bin/env python3
"""One-shot generator for platform-* scenario JSON (idempotent overwrite)."""
from __future__ import annotations

import json
from pathlib import Path

OUT = Path(__file__).resolve().parent / "scenarios"

SCENARIOS = [
    dict(
        id="platform-virtual-devices",
        title="Two virtual-unified devices",
        tags=["object:device", "capability:virtual"],
        prompt=(
            "Create two virtual unified lab devices named demo-device-1 and demo-device-2 under devices. "
            "Start their drivers so telemetry variables like sineWave and intValue exist."
        ),
        tools=["create_virtual_device", "list_variables"],
        paths=[
            "root.platform.devices.demo-device-1",
            "root.platform.devices.demo-device-2",
        ],
        surfaces=["explorer", "drivers", "ai-studio"],
    ),
    dict(
        id="platform-users-acl",
        title="Demo users and object ACL",
        tags=["object:user", "capability:acl"],
        prompt=(
            "Create two operator users demo-user1 and demo-user2. "
            "Give demo-user1 EDITOR access on root.platform.devices.demo-device-1 "
            "(create the device first if missing)."
        ),
        tools=["create_object", "set_object_acl", "create_virtual_device"],
        paths=[
            "root.platform.security.users.demo-user1",
            "root.platform.security.users.demo-user2",
            "root.platform.devices.demo-device-1",
        ],
        surfaces=["explorer", "ai-studio"],
    ),
    dict(
        id="platform-alerts-correlators",
        title="Alert latch and correlator unlatch",
        tags=["object:alert", "object:correlator"],
        prompt=(
            "On demo-device-1 (create virtual-unified if needed) configure an alert that latches on event1 "
            "and a correlator that unlatches or follows up when event2 fires with intValue greater than 20. "
            "Put rules under alert-rules and correlators with demo- prefix names "
            "demo-event1-latch and demo-event2-unlatch."
        ),
        tools=["create_virtual_device", "configure_alert", "configure_correlator"],
        paths=[
            "root.platform.devices.demo-device-1",
            "root.platform.alert-rules.demo-event1-latch",
            "root.platform.correlators.demo-event2-unlatch",
        ],
        surfaces=["explorer", "alarms", "ai-studio"],
    ),
    dict(
        id="platform-event-filter",
        title="Event filter for event2",
        tags=["object:event-filter"],
        prompt=(
            "Create an event filter demo-event2-filter under event-filters for event2 "
            "where int is greater than 10 or string contains abc. Use demo-device-1 as context if needed."
        ),
        tools=["create_object"],
        paths=["root.platform.event-filters.demo-event2-filter"],
        surfaces=["explorer", "ai-studio"],
    ),
    dict(
        id="platform-singleton-hub",
        title="Singleton calculator hub",
        tags=["object:singleton", "capability:function"],
        prompt=(
            "Create a CUSTOM singleton hub named demo-calculator under root.platform.singleton-blueprints "
            "(never as DEVICE). Deploy a calculator function on it that can invoke calculate on demo-device-1. "
            "Create the virtual device if missing."
        ),
        tools=["create_object", "deploy_tree_function", "create_virtual_device"],
        paths=[
            "root.platform.singleton-blueprints.demo-calculator",
            "root.platform.devices.demo-device-1",
        ],
        surfaces=["explorer", "ai-studio"],
    ),
    dict(
        id="platform-mixin-waves",
        title="Apply waves-sum mixin",
        tags=["capability:mixin"],
        prompt=(
            "Ensure demo-device-1 and demo-device-2 exist as virtual-unified devices. "
            "Apply the virtual-lab-waves-sum-v1 mixin so each has sumWaves from sineWave and sawtoothWave."
        ),
        tools=["create_virtual_device", "list_mixin_blueprints", "apply_mixin_blueprint"],
        paths=[
            "root.platform.devices.demo-device-1",
            "root.platform.devices.demo-device-2",
        ],
        surfaces=["explorer", "ai-studio"],
    ),
    dict(
        id="platform-historian",
        title="Enable wave historian",
        tags=["capability:historian"],
        prompt=(
            "On demo-device-1 enable history for sineWave and sawtoothWave so charts can work. "
            "Create the virtual device first if it does not exist."
        ),
        tools=["create_virtual_device", "configure_variable_history", "list_variables"],
        paths=["root.platform.devices.demo-device-1"],
        surfaces=["explorer", "ai-studio"],
    ),
    dict(
        id="platform-dashboard-bound",
        title="Bound overview dashboard",
        tags=["object:dashboard"],
        prompt=(
            "Create dashboard demo-overview under dashboards with a presentable layout "
            "columns=84 rowHeight=8. Bind at least value widgets for sineWave and deviceActive "
            "on demo-device-1 plus a chart. Create the device if needed. Do not leave unbound placeholders."
        ),
        tools=["create_object", "create_virtual_device", "set_dashboard_layout", "list_variables"],
        paths=[
            "root.platform.dashboards.demo-overview",
            "root.platform.devices.demo-device-1",
        ],
        surfaces=["dashboard", "explorer", "ai-studio"],
    ),
    dict(
        id="platform-mimic-pack",
        title="Fan mimic with pack symbols",
        tags=["object:mimic"],
        prompt=(
            "Create mimic demo-fan under mimics. Save a diagram using real pack symbols such as "
            "pack.ispf-pid.fan and pump.centrifugal from the platform symbol catalog. Bind running to "
            "demo-device-1.deviceActive. Never invent label or button.start symbolIds. "
            "Create the device if needed."
        ),
        tools=["create_object", "create_virtual_device", "list_mimic_symbols", "save_mimic_diagram"],
        paths=[
            "root.platform.mimics.demo-fan",
            "root.platform.devices.demo-device-1",
        ],
        surfaces=["mimic", "explorer", "ai-studio"],
    ),
    dict(
        id="platform-report-tree-vars",
        title="Tree-variables report",
        tags=["object:report"],
        prompt=(
            "Create report demo-table under reports using tree-variables style for devices matching "
            "demo-device-* and variable telemetryTable or similar table variable. Create demo devices if needed."
        ),
        tools=["configure_report", "create_virtual_device"],
        paths=["root.platform.reports.demo-table"],
        surfaces=["explorer", "ai-studio"],
    ),
    dict(
        id="platform-query-custom",
        title="Custom object query",
        tags=["object:query"],
        prompt=(
            "Create a CUSTOM query object demo-device-vars under root.platform.queries that can list "
            "writable variables for demo virtual devices."
        ),
        tools=["create_object"],
        paths=["root.platform.queries.demo-device-vars"],
        surfaces=["explorer", "ai-studio"],
    ),
    dict(
        id="platform-operator-ui",
        title="Operator app for demo overview",
        tags=["object:operator-app"],
        prompt=(
            "Create dashboard demo-overview if missing with a simple bound layout to demo-device-1. "
            "Register operator app demo-ops with that dashboard as default so an operator can open it "
            "without the object tree."
        ),
        tools=["create_object", "create_virtual_device", "set_dashboard_layout", "configure_operator_ui"],
        paths=[
            "root.platform.dashboards.demo-overview",
            "root.platform.operator-apps.demo-ops",
        ],
        surfaces=["operator", "dashboard", "ai-studio"],
    ),
    dict(
        id="platform-schedule",
        title="Platform schedule tick",
        tags=["object:schedule"],
        prompt=(
            "Create or update a platform schedule demo-tick that periodically invokes a harmless function "
            "on demo-calculator hub or demo-device-1. Create prerequisites if missing."
        ),
        tools=["configure_platform_schedule", "create_object"],
        paths=["root.platform.schedules.demo-tick"],
        surfaces=["schedules", "explorer", "ai-studio"],
    ),
    dict(
        id="platform-workflow-min",
        title="Minimal workflow shell",
        tags=["object:workflow"],
        prompt=(
            "Create a WORKFLOW object demo-flow under workflows and save a minimal valid BPMN that starts "
            "and ends. Keep it tiny; no production process required."
        ),
        tools=["create_object", "save_workflow_bpmn"],
        paths=["root.platform.workflows.demo-flow"],
        surfaces=["explorer", "work-queue", "ai-studio"],
    ),
]


def main() -> None:
    for s in SCENARIOS:
        doc = {
            "id": s["id"],
            "version": "1",
            "domain": "platform",
            "lane": "agent",
            "kind": "platform-primitive",
            "tags": s["tags"],
            "title": s["title"],
            "description": f"Platform demo matrix: {s['title']}",
            "prompt": s["prompt"],
            "acceptance": {
                "requiredTools": s["tools"],
                "requiredObjectPaths": s["paths"],
                "operatorSurfaces": s["surfaces"],
            },
        }
        path = OUT / f"{s['id']}.json"
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("wrote", path.name)
    print("total", len(SCENARIOS))


if __name__ == "__main__":
    main()
