#!/usr/bin/env python3
"""Generate deploy/grafana/ispf-automation-pipeline.json."""

from __future__ import annotations

import json
from pathlib import Path

DS = {"type": "prometheus", "uid": "${DS_PROMETHEUS}"}
RATE = "$__rate_interval"


def ts(panel_id: int, title: str, x: int, y: int, w: int, h: int, targets: list[dict], unit: str = "ops", stack: bool = False) -> dict:
    return {
        "id": panel_id,
        "type": "timeseries",
        "title": title,
        "gridPos": {"x": x, "y": y, "w": w, "h": h},
        "datasource": DS,
        "fieldConfig": {
            "defaults": {
                "unit": unit,
                "custom": {
                    "drawStyle": "line",
                    "lineWidth": 1,
                    "fillOpacity": 10,
                    "stacking": {"mode": "normal" if stack else "none"},
                },
            },
            "overrides": [],
        },
        "options": {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}},
        "targets": targets,
    }


def stat(panel_id: int, title: str, x: int, y: int, w: int, h: int, expr: str, unit: str = "short") -> dict:
    return {
        "id": panel_id,
        "type": "stat",
        "title": title,
        "gridPos": {"x": x, "y": y, "w": w, "h": h},
        "datasource": DS,
        "fieldConfig": {
            "defaults": {
                "unit": unit,
                "thresholds": {
                    "mode": "absolute",
                    "steps": [{"color": "green", "value": None}, {"color": "red", "value": 80}],
                },
            },
            "overrides": [],
        },
        "options": {"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "value", "graphMode": "area"},
        "targets": [{"expr": expr, "refId": "A"}],
    }


def row(panel_id: int, title: str, y: int) -> dict:
    return {
        "id": panel_id,
        "type": "row",
        "title": title,
        "gridPos": {"x": 0, "y": y, "w": 24, "h": 1},
        "collapsed": False,
        "panels": [],
    }


def main() -> None:
    panels: list[dict] = []
    y = 0

    panels.append(row(100, "Throughput (events & automation)", y))
    y += 1
    panels += [
        ts(
            1,
            "Events fired /s by source",
            0,
            y,
            12,
            8,
            [{"expr": f"sum by (source) (rate(ispf_events_fired_total[{RATE}]))", "legendFormat": "{{source}}", "refId": "A"}],
            stack=True,
        ),
        ts(
            2,
            "Alert fires /s",
            12,
            y,
            6,
            8,
            [{"expr": f"rate(ispf_alert_fires_total[{RATE}])", "legendFormat": "alert fires", "refId": "A"}],
        ),
        ts(
            3,
            "Alert evaluations /s",
            18,
            y,
            6,
            8,
            [{"expr": f"rate(ispf_alert_evaluations_total[{RATE}])", "legendFormat": "evaluations", "refId": "A"}],
        ),
    ]
    y += 8
    panels += [
        ts(
            4,
            "Correlator triggers /s",
            0,
            y,
            12,
            7,
            [{"expr": f"rate(ispf_correlator_triggers_total[{RATE}])", "legendFormat": "correlators", "refId": "A"}],
        ),
        ts(
            5,
            "Workflow starts /s by trigger",
            12,
            y,
            12,
            7,
            [
                {
                    "expr": f"sum by (trigger) (rate(ispf_workflow_starts_total[{RATE}]))",
                    "legendFormat": "{{trigger}}",
                    "refId": "A",
                }
            ],
            stack=True,
        ),
    ]
    y += 7

    panels.append(row(101, "Object-change bus (dual lane)", y))
    y += 1
    panels += [
        ts(
            10,
            "Queue depth by lane",
            0,
            y,
            8,
            8,
            [{"expr": "ispf_object_change_queue_size", "legendFormat": "{{lane}}", "refId": "A"}],
            unit="short",
        ),
        ts(
            11,
            "Active workers by lane",
            8,
            y,
            8,
            8,
            [{"expr": "ispf_object_change_workers_active", "legendFormat": "{{lane}}", "refId": "A"}],
            unit="short",
        ),
        ts(
            12,
            "Processed /s",
            16,
            y,
            8,
            8,
            [{"expr": f"rate(ispf_object_change_processed_total[{RATE}])", "legendFormat": "processed", "refId": "A"}],
        ),
    ]
    y += 8
    panels += [
        stat(13, "Queue drops /s", 0, y, 6, 4, f"rate(ispf_object_change_queue_dropped_total[{RATE}])", "ops"),
        stat(14, "Total queue (all lanes)", 6, y, 6, 4, 'ispf_object_change_queue_size{lane="total"}'),
        stat(15, "Automation queue", 12, y, 6, 4, 'ispf_object_change_queue_size{lane="automation"}'),
        stat(16, "Telemetry queue", 18, y, 6, 4, 'ispf_object_change_queue_size{lane="telemetry"}'),
    ]
    y += 4

    panels.append(row(102, "Event journal", y))
    y += 1
    panels += [
        ts(
            20,
            "Journal async queue size",
            0,
            y,
            8,
            7,
            [{"expr": "ispf_event_journal_queue_size", "legendFormat": "queue", "refId": "A"}],
            unit="short",
        ),
        ts(
            21,
            "Journal flushed /s",
            8,
            y,
            8,
            7,
            [{"expr": f"rate(ispf_event_journal_flushed_total[{RATE}])", "legendFormat": "flushed", "refId": "A"}],
        ),
        ts(
            22,
            "Sync fallback /s (queue full)",
            16,
            y,
            8,
            7,
            [
                {
                    "expr": f"rate(ispf_event_journal_queue_full_sync_fallback_total[{RATE}])",
                    "legendFormat": "sync fallback",
                    "refId": "A",
                }
            ],
        ),
    ]
    y += 7
    panels += [
        stat(23, "Event history records", 0, y, 8, 4, "ispf_event_history_records"),
        stat(24, "Workflow instances running", 8, y, 8, 4, "ispf_workflow_instances_running"),
        stat(25, "Variable history samples", 16, y, 8, 4, "ispf_variable_history_samples"),
    ]
    y += 4

    panels.append(row(103, "Drivers & database", y))
    y += 1
    panels += [
        ts(
            30,
            "Drivers active / connected",
            0,
            y,
            12,
            7,
            [
                {"expr": "ispf_drivers_active", "legendFormat": "active", "refId": "A"},
                {"expr": "ispf_drivers_connected", "legendFormat": "connected", "refId": "B"},
            ],
            unit="short",
        ),
        ts(
            31,
            "DB pool connections",
            12,
            y,
            12,
            7,
            [
                {"expr": "ispf_database_connections_active", "legendFormat": "active", "refId": "A"},
                {"expr": "ispf_database_connections_idle", "legendFormat": "idle", "refId": "B"},
                {"expr": "ispf_database_connections_awaiting", "legendFormat": "awaiting", "refId": "C"},
            ],
            unit="short",
        ),
    ]

    dashboard = {
        "annotations": {"list": []},
        "editable": True,
        "fiscalYearStartMonth": 0,
        "graphTooltip": 1,
        "id": None,
        "links": [],
        "liveNow": False,
        "panels": panels,
        "refresh": "10s",
        "schemaVersion": 39,
        "tags": ["ispf", "automation", "prometheus", "otlp"],
        "templating": {
            "list": [
                {
                    "current": {"selected": False, "text": "Prometheus", "value": "Prometheus"},
                    "hide": 0,
                    "includeAll": False,
                    "label": "Datasource",
                    "multi": False,
                    "name": "DS_PROMETHEUS",
                    "options": [],
                    "query": "prometheus",
                    "refresh": 1,
                    "regex": "",
                    "skipUrlSync": False,
                    "type": "datasource",
                }
            ]
        },
        "time": {"from": "now-1h", "to": "now"},
        "timepicker": {},
        "timezone": "browser",
        "title": "ISPF Automation Pipeline",
        "uid": "ispf-automation-pipeline",
        "version": 1,
        "description": (
            "All ISPF automation pipeline Micrometer metrics. "
            "Prometheus pull: /actuator/prometheus. "
            "OTLP push: ISPF → OTel Collector → Prometheus exporter :8889."
        ),
    }

    out = Path(__file__).with_name("ispf-automation-pipeline.json")
    out.write_text(json.dumps(dashboard, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {out} ({len(panels)} panels)")


if __name__ == "__main__":
    main()
