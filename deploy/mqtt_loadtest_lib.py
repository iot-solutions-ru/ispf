"""Shared helpers for MQTT ingress load testing."""

from __future__ import annotations

import json
from urllib.parse import quote

import requests

PREFIX = "loadtest-mqtt"
MODEL_NAME = "mqtt-sensor-v1"
TOPIC_PREFIX = "ispf/loadtest"

PARSE_TEMPERATURE_BINDING = [
    {
        "id": "parse-mqtt-temperature",
        "name": "Parse MQTT temperature payload",
        "enabled": True,
        "order": 10,
        "activators": {
            "onStartup": True,
            "onVariableChange": [{"objectPath": "self", "variableName": "temperature"}],
            "onEvent": None,
            "periodicMs": 0,
        },
        "condition": "",
        "expression": "double(self.temperature.raw)",
        "target": {"variableName": "temperature", "field": "value"},
    }
]


def device_name(index: int, pad: int = 5) -> str:
    return f"{PREFIX}-dev-{index:0{pad}d}"


def device_path(index: int, pad: int = 5) -> str:
    return f"root.platform.devices.{device_name(index, pad)}"


def mqtt_topic(index: int, pad: int = 5) -> str:
    return f"{TOPIC_PREFIX}/{index:0{pad}d}/temperature"


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
        kwargs.setdefault("timeout", self.timeout)
        return self.session.request(method, f"{self.base}{path}", **kwargs)


def resolve_model_id(client: Client, model_name: str = MODEL_NAME) -> str:
    r = client.request("GET", f"/api/v1/models/by-name/{model_name}")
    r.raise_for_status()
    return r.json()["id"]


def list_mqtt_loadtest_devices(client: Client) -> list[str]:
    paths: list[str] = []
    items = client.request("GET", "/api/v1/objects?parent=root.platform.devices&lite=true").json()
    for item in items:
        path = item.get("path", "")
        if f".{PREFIX}-dev-" in path:
            paths.append(path)
    return sorted(paths)


def delete_device(client: Client, path: str) -> None:
    client.request("POST", f"/api/v1/drivers/runtime/stop?devicePath={quote(path, safe='')}")
    r = client.request("DELETE", f"/api/v1/objects/by-path?path={quote(path, safe='')}")
    if r.status_code not in (200, 204, 404):
        raise RuntimeError(f"delete {path}: HTTP {r.status_code} {r.text[:160]}")


def ensure_device(client: Client, index: int, pad: int, model_id: str) -> str:
    path = device_path(index, pad)
    name = device_name(index, pad)
    body = {
        "parentPath": "root.platform.devices",
        "name": name,
        "type": "DEVICE",
        "displayName": f"MQTT load test device {index}",
        "description": "MQTT ingress load test (mosquitto + mqtt driver)",
    }
    r = client.request("POST", "/api/v1/objects", json=body)
    if r.status_code == 409:
        status = driver_status(client, path)
        if status and status.get("driverId") == "mqtt":
            client.request(
                "POST",
                f"/api/v1/models/{model_id}/apply?objectPath={quote(path, safe='')}",
            )
            return path
        delete_device(client, path)
        r = client.request("POST", "/api/v1/objects", json=body)
    if r.status_code >= 400:
        raise RuntimeError(f"create device {name}: HTTP {r.status_code} {r.text[:200]}")
    client.request(
        "POST",
        f"/api/v1/models/{model_id}/apply?objectPath={quote(path, safe='')}",
    ).raise_for_status()
    return path


def put_binding_rules(client: Client, path: str) -> None:
    r = client.request(
        "PUT",
        f"/api/v1/objects/by-path/binding-rules?path={quote(path, safe='')}",
        json=PARSE_TEMPERATURE_BINDING,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"binding rules {path}: HTTP {r.status_code} {r.text[:200]}")


def prepare_for_mqtt_driver(client: Client, path: str) -> None:
    """Model defaults driverId to virtual; set mqtt before first configure."""
    payload = {
        "schema": {"name": "driverId", "fields": [{"name": "value", "type": "STRING"}]},
        "rows": [{"value": "mqtt"}],
    }
    r = client.request(
        "PUT",
        f"/api/v1/objects/by-path/variables?path={quote(path, safe='')}&name=driverId",
        json=payload,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"set driverId mqtt on {path}: HTTP {r.status_code} {r.text[:160]}")


def configure_mqtt_driver(
    client: Client,
    path: str,
    broker_url: str,
    topic: str,
    telemetry_publish_mode: str | None = None,
    telemetry_coalesce_ms: int | None = None,
    poll_ms: int = 5000,
    auto_start: bool = True,
) -> dict:
    body: dict = {
        "driverId": "mqtt",
        "pollIntervalMs": poll_ms,
        "configuration": {"brokerUrl": broker_url, "topicPrefix": ""},
        "pointMappings": {"temperature": topic},
        "autoStart": auto_start,
    }
    if telemetry_publish_mode:
        body["telemetryPublishMode"] = telemetry_publish_mode
    if telemetry_coalesce_ms is not None and telemetry_coalesce_ms > 0:
        body["telemetryCoalesceMs"] = telemetry_coalesce_ms
    r = client.request(
        "PUT",
        f"/api/v1/drivers/runtime/configure?devicePath={quote(path, safe='')}",
        json=body,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"configure mqtt driver {path}: HTTP {r.status_code} {r.text[:200]}")
    return r.json()


def start_mqtt_driver(client: Client, path: str) -> dict | None:
    r = client.request("POST", f"/api/v1/drivers/runtime/start?devicePath={quote(path, safe='')}")
    if r.status_code >= 400:
        return None
    return r.json()


def ensure_alert_rule(client: Client, path: str, index: int, condition_expr: str) -> None:
    name = f"mqtt loadtest alert {index:05d}"
    body = {
        "name": name,
        "objectPath": path,
        "watchVariable": "temperature",
        "conditionExpr": condition_expr,
        "eventName": "event1",
        "payloadVariable": "temperature",
        "enabled": True,
        "edgeTrigger": False,
    }
    existing = client.request("GET", "/api/v1/alert-rules").json()
    by_name = {item.get("name", ""): item.get("id", "") for item in existing if isinstance(existing, list)}
    if name in by_name and by_name[name]:
        r = client.request(
            "PUT",
            f"/api/v1/alert-rules/by-path?path={quote(by_name[name], safe='')}",
            json=body,
        )
    else:
        r = client.request("POST", "/api/v1/alert-rules", json=body)
    if r.status_code not in (200, 201, 409):
        raise RuntimeError(f"alert rule {name}: HTTP {r.status_code} {r.text[:200]}")


def read_temperature_raw(client: Client, path: str) -> str | None:
    r = client.request(
        "GET",
        f"/api/v1/objects/by-path/variables/detail?path={quote(path, safe='')}&name=temperature",
    )
    if r.status_code != 200:
        return None
    data = r.json()
    value = data.get("value") if isinstance(data, dict) else None
    if not value:
        return None
    rows = value.get("rows") or []
    if not rows:
        return None
    row = rows[0]
    if row.get("raw") is not None:
        return str(row.get("raw"))
    if row.get("value") is not None:
        return str(row.get("value"))
    return None


def driver_status(client: Client, path: str) -> dict | None:
    r = client.request(
        "GET",
        f"/api/v1/drivers/runtime/status?devicePath={quote(path, safe='')}",
    )
    if r.status_code != 200:
        return None
    return r.json()


def automation_metrics(client: Client) -> dict:
    r = client.request("GET", "/api/v1/platform/metrics")
    r.raise_for_status()
    for section in r.json().get("sections", []):
        if section.get("id") == "automation":
            return section.get("values") or {}
    return {}


def event_history_count(client: Client) -> int:
    return int(automation_metrics(client).get("eventHistoryRecords") or 0)


def alert_fires_count(client: Client) -> int:
    return int(automation_metrics(client).get("alertFiresTotal") or 0)


def seed_mqtt_devices(
    client: Client,
    device_count: int,
    broker_url: str,
    condition_expr: str,
    telemetry_mix_ratio: float = 0.0,
    poll_ms: int = 5000,
    topics: list[str] | None = None,
) -> list[str]:
    pad = max(5, len(str(device_count)))
    model_id = resolve_model_id(client)
    paths: list[str] = []
    telemetry_only_count = int(device_count * telemetry_mix_ratio) if telemetry_mix_ratio > 0 else 0
    resolved_topics = topics or []

    for index in range(1, device_count + 1):
        path = ensure_device(client, index, pad, model_id)
        put_binding_rules(client, path)
        prepare_for_mqtt_driver(client, path)
        mode = "TELEMETRY_ONLY" if index <= telemetry_only_count else None
        if resolved_topics:
            topic = resolved_topics[(index - 1) % len(resolved_topics)]
        else:
            topic = mqtt_topic(index, pad)
        configure_mqtt_driver(
            client,
            path,
            broker_url,
            topic,
            telemetry_publish_mode=mode,
            poll_ms=poll_ms,
            auto_start=False,
        )
        ensure_alert_rule(client, path, index, condition_expr)
        paths.append(path)
        if index % 10 == 0 or index == device_count:
            print(f"  seeded {index}/{device_count} mqtt devices (subscribe {topic!r})")

    started = 0
    for path in paths:
        if start_mqtt_driver(client, path):
            started += 1
    print(f"  mqtt drivers started: {started}/{len(paths)} (broker must be reachable from ISPF server)")
    return paths
