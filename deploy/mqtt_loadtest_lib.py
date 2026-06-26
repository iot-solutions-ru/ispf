"""Shared helpers for MQTT ingress load testing."""

from __future__ import annotations

from loadtest_cleanup_lib import delete_loadtest_alert_rules
from urllib.parse import quote

import requests

PREFIX = "loadtest-mqtt"
GATEWAY_NAME = "loadtest-mqtt-gateway"
GATEWAY_MODEL_NAME = "mqtt-gateway-v1"
MODEL_NAME = "mqtt-sensor-v1"
SENSOR_NAME_PREFIX = "loadtest-mqtt-sensor-"
TOPIC_PREFIX = "ispf/loadtest"
GATEWAY_TOPIC_FILTER = "ispf/loadtest/+/temperature"

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

GATEWAY_DISPATCH_BINDING = [
    {
        "id": "dispatch-on-ingress",
        "name": "Dispatch MQTT ingress to child sensor",
        "enabled": True,
        "order": 10,
        "activators": {
            "onStartup": False,
            "onVariableChange": [{"objectPath": "self", "variableName": "lastIngress"}],
            "onEvent": None,
            "periodicMs": 0,
            "async": True,
        },
        "condition": "",
        "expression": "callFunction(dispatchTelemetry, lastIngress)",
        "target": {"variableName": "dispatchStatus", "field": "ok"},
    }
]


def device_name(index: int, pad: int = 5) -> str:
    return f"{PREFIX}-dev-{index:0{pad}d}"


def sensor_name(index: int, pad: int = 5) -> str:
    return f"{SENSOR_NAME_PREFIX}{index:0{pad}d}"


def gateway_path() -> str:
    return f"root.platform.devices.{GATEWAY_NAME}"


def gateway_sensors_path() -> str:
    return f"{gateway_path()}.sensors"


def sensor_path(index: int, pad: int = 5) -> str:
    return f"{gateway_sensors_path()}.{sensor_name(index, pad)}"


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
    if paths:
        return sorted(paths)
    sensors_parent = gateway_sensors_path()
    try:
        items = client.request("GET", f"/api/v1/objects?parent={sensors_parent}&lite=true").json()
        for item in items:
            path = item.get("path", "")
            if path and item.get("type") == "DEVICE":
                paths.append(path)
    except Exception:
        pass
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


def configure_child_telemetry_policy(
    client: Client,
    path: str,
    telemetry_coalesce_ms: int | None = None,
) -> None:
    """TELEMETRY_ONLY on gateway child sensors — historian lane only, no automation."""
    body: dict = {
        "driverId": "virtual",
        "pollIntervalMs": 5000,
        "telemetryPublishMode": "TELEMETRY_ONLY",
        "autoStart": False,
    }
    if telemetry_coalesce_ms is not None and telemetry_coalesce_ms > 0:
        body["telemetryCoalesceMs"] = telemetry_coalesce_ms
    r = client.request(
        "PUT",
        f"/api/v1/drivers/runtime/configure?devicePath={quote(path, safe='')}",
        json=body,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"configure child telemetry {path}: HTTP {r.status_code} {r.text[:200]}")


def put_binding_rules(client: Client, path: str) -> None:
    r = client.request(
        "PUT",
        f"/api/v1/objects/by-path/binding-rules?path={quote(path, safe='')}",
        json=PARSE_TEMPERATURE_BINDING,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"binding rules {path}: HTTP {r.status_code} {r.text[:200]}")


def put_gateway_binding_rules(client: Client, path: str) -> None:
    r = client.request(
        "PUT",
        f"/api/v1/objects/by-path/binding-rules?path={quote(path, safe='')}",
        json=GATEWAY_DISPATCH_BINDING,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"gateway binding rules {path}: HTTP {r.status_code} {r.text[:200]}")


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
    point_mappings: dict[str, str] | None = None,
    configuration: dict[str, str] | None = None,
) -> dict:
    driver_configuration = {"brokerUrl": broker_url, "topicPrefix": ""}
    if configuration:
        driver_configuration.update(configuration)
    mappings = point_mappings if point_mappings is not None else {"temperature": topic}
    body: dict = {
        "driverId": "mqtt",
        "pollIntervalMs": poll_ms,
        "configuration": driver_configuration,
        "pointMappings": mappings,
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
        "eventName": "thresholdExceeded",
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


def variable_history_metrics(client: Client) -> dict:
    r = client.request("GET", "/api/v1/platform/metrics")
    r.raise_for_status()
    for section in r.json().get("sections", []):
        if section.get("id") == "variableHistory":
            return section.get("values") or {}
    return {}


def variable_history_sample_count(client: Client) -> int:
    return int(variable_history_metrics(client).get("sampleCount") or 0)


def event_history_count(client: Client) -> int:
    return int(automation_metrics(client).get("eventHistoryRecords") or 0)


def alert_fires_count(client: Client) -> int:
    return int(automation_metrics(client).get("alertFiresTotal") or 0)


def automation_queue_metrics(client: Client) -> dict:
    m = automation_metrics(client)
    return {
        "eventJournalQueueSize": int(m.get("eventJournalQueueSize") or 0),
        "objectChangeQueueSize": int(m.get("objectChangeQueueSize") or 0),
        "objectChangeTelemetryQueueSize": int(m.get("objectChangeTelemetryQueueSize") or 0),
        "objectChangeAutomationQueueSize": int(m.get("objectChangeAutomationQueueSize") or 0),
    }


def reapply_mqtt_coalesce(
    client: Client,
    device_paths: list[str],
    broker_url: str,
    coalesce_ms: int,
    topics: list[str] | None = None,
    poll_ms: int = 5000,
    historian_only: bool = True,
) -> int:
    """Reconfigure running mqtt loadtest devices with a new per-device coalesce override."""
    started = 0
    resolved_topics = topics or []
    pad = 5
    mode = "TELEMETRY_ONLY" if historian_only else None
    for index, path in enumerate(sorted(device_paths), start=1):
        if resolved_topics:
            topic = resolved_topics[(index - 1) % len(resolved_topics)]
        else:
            topic = mqtt_topic(index, pad)
        client.request("POST", f"/api/v1/drivers/runtime/stop?devicePath={quote(path, safe='')}")
        configure_mqtt_driver(
            client,
            path,
            broker_url,
            topic,
            telemetry_publish_mode=mode,
            telemetry_coalesce_ms=coalesce_ms,
            poll_ms=poll_ms,
            auto_start=False,
        )
        if start_mqtt_driver(client, path):
            started += 1
    return started


def reapply_gateway_coalesce(
    client: Client,
    gateway_device_path: str,
    sensor_paths: list[str],
    broker_url: str,
    coalesce_ms: int,
    topics: list[str] | None = None,
    poll_ms: int = 5000,
) -> int:
    """Reconfigure gateway + child sensors with new per-device telemetryCoalesceMs."""
    for path in sorted(sensor_paths):
        configure_child_telemetry_policy(client, path, telemetry_coalesce_ms=coalesce_ms)
    client.request(
        "POST",
        f"/api/v1/drivers/runtime/stop?devicePath={quote(gateway_device_path, safe='')}",
    )
    configure_mqtt_gateway_driver(
        client,
        gateway_device_path,
        broker_url,
        topics=topics,
        telemetry_coalesce_ms=coalesce_ms,
        poll_ms=poll_ms,
        auto_start=False,
    )
    return 1 if start_mqtt_driver(client, gateway_device_path) else 0


def _create_object(
    client: Client,
    parent_path: str,
    name: str,
    obj_type: str,
    display_name: str,
    description: str,
) -> str:
    path = f"{parent_path}.{name}"
    body = {
        "parentPath": parent_path,
        "name": name,
        "type": obj_type,
        "displayName": display_name,
        "description": description,
    }
    r = client.request("POST", "/api/v1/objects", json=body)
    if r.status_code == 409:
        return path
    if r.status_code >= 400:
        raise RuntimeError(f"create {name}: HTTP {r.status_code} {r.text[:200]}")
    return path


def _set_string_variable(client: Client, path: str, name: str, value: str) -> None:
    payload = {
        "schema": {"name": name, "fields": [{"name": "value", "type": "STRING"}]},
        "rows": [{"value": value}],
    }
    r = client.request(
        "PUT",
        f"/api/v1/objects/by-path/variables?path={quote(path, safe='')}&name={name}",
        json=payload,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"set {name} on {path}: HTTP {r.status_code} {r.text[:160]}")


def delete_gateway_tree(client: Client) -> None:
    """Remove prior gateway loadtest tree (stale bindings/models break orchestrator)."""
    gw = gateway_path()
    sensors_parent = gateway_sensors_path()
    try:
        children = client.request(
            "GET",
            f"/api/v1/objects?parent={quote(sensors_parent, safe='')}&lite=true",
        ).json()
        for item in children:
            path = item.get("path", "")
            if path and item.get("type") == "DEVICE":
                delete_device(client, path)
    except Exception:
        pass
    if driver_status(client, gw):
        delete_device(client, gw)
    else:
        client.request("DELETE", f"/api/v1/objects/by-path?path={quote(gw, safe='')}")
    client.request("DELETE", f"/api/v1/objects/by-path?path={quote(sensors_parent, safe='')}")


def ensure_gateway_tree(
    client: Client,
    device_count: int,
    pad: int,
    gateway_model_id: str,
    sensor_model_id: str,
    child_coalesce_ms: int | None = None,
) -> tuple[str, list[str]]:
    delete_gateway_tree(client)
    gw = gateway_path()
    sensors_parent = gateway_sensors_path()
    _create_object(
        client,
        "root.platform.devices",
        GATEWAY_NAME,
        "DEVICE",
        "MQTT loadtest gateway",
        "Single MQTT connection orchestrator for load test",
    )
    _create_object(client, gw, "sensors", "CUSTOM", "Sensors", "Child sensors for gateway load test")
    client.request(
        "POST",
        f"/api/v1/models/{gateway_model_id}/apply?objectPath={quote(gw, safe='')}",
    ).raise_for_status()
    put_gateway_binding_rules(client, gw)
    _set_string_variable(client, gw, "sensorParentPath", sensors_parent)

    sensor_paths: list[str] = []
    for index in range(1, device_count + 1):
        name = sensor_name(index, pad)
        path = _create_object(
            client,
            sensors_parent,
            name,
            "DEVICE",
            f"MQTT loadtest sensor {index}",
            "MQTT gateway child sensor (no driver)",
        )
        client.request(
            "POST",
            f"/api/v1/models/{sensor_model_id}/apply?objectPath={quote(path, safe='')}",
        ).raise_for_status()
        put_binding_rules(client, path)
        configure_child_telemetry_policy(client, path, telemetry_coalesce_ms=child_coalesce_ms)
        sensor_paths.append(path)
    return gw, sensor_paths


def configure_mqtt_gateway_driver(
    client: Client,
    path: str,
    broker_url: str,
    topics: list[str] | None = None,
    telemetry_coalesce_ms: int | None = None,
    poll_ms: int = 5000,
    auto_start: bool = True,
) -> dict:
    if topics:
        point_mappings = {f"t{index}": topic for index, topic in enumerate(topics)}
    else:
        point_mappings = {"ingress": GATEWAY_TOPIC_FILTER}
    return configure_mqtt_driver(
        client,
        path,
        broker_url,
        GATEWAY_TOPIC_FILTER,
        telemetry_publish_mode="TELEMETRY_ONLY",
        telemetry_coalesce_ms=telemetry_coalesce_ms,
        poll_ms=poll_ms,
        auto_start=auto_start,
        point_mappings=point_mappings,
        configuration={"ingressVariable": "lastIngress", "ingressTopicLanes": "true"},
    )


def seed_mqtt_gateway_devices(
    client: Client,
    device_count: int,
    broker_url: str,
    poll_ms: int = 5000,
    topics: list[str] | None = None,
    telemetry_coalesce_ms: int | None = None,
) -> tuple[str, list[str]]:
    """1× mqtt gateway + N child sensors (orchestrator load test)."""
    pad = max(5, len(str(device_count)))
    gateway_model_id = resolve_model_id(client, GATEWAY_MODEL_NAME)
    sensor_model_id = resolve_model_id(client, MODEL_NAME)
    gw, sensor_paths = ensure_gateway_tree(
        client,
        device_count,
        pad,
        gateway_model_id,
        sensor_model_id,
        child_coalesce_ms=telemetry_coalesce_ms,
    )

    delete_loadtest_alert_rules(client)

    configure_mqtt_gateway_driver(
        client,
        gw,
        broker_url,
        topics=topics,
        telemetry_coalesce_ms=telemetry_coalesce_ms,
        poll_ms=poll_ms,
        auto_start=False,
    )
    print(f"  gateway {gw}: 1 mqtt driver, {device_count} child sensors, orchestrator dispatchTelemetry")

    if start_mqtt_driver(client, gw):
        print(f"  mqtt gateway driver started (broker must be reachable from ISPF server)")
    else:
        print("  WARN: mqtt gateway driver failed to start")
    return gw, sensor_paths


def seed_mqtt_devices(
    client: Client,
    device_count: int,
    broker_url: str,
    condition_expr: str,
    telemetry_mix_ratio: float = 0.0,
    poll_ms: int = 5000,
    topics: list[str] | None = None,
    telemetry_coalesce_ms: int | None = None,
    historian_only: bool = True,
) -> list[str]:
    pad = max(5, len(str(device_count)))
    model_id = resolve_model_id(client)
    paths: list[str] = []
    if historian_only:
        telemetry_only_count = device_count
    else:
        telemetry_only_count = int(device_count * telemetry_mix_ratio) if telemetry_mix_ratio > 0 else 0
    resolved_topics = topics or []

    for index in range(1, device_count + 1):
        path = ensure_device(client, index, pad, model_id)
        put_binding_rules(client, path)
        prepare_for_mqtt_driver(client, path)
        if historian_only:
            mode = "TELEMETRY_ONLY"
        elif telemetry_mix_ratio > 0 and index <= telemetry_only_count:
            mode = "TELEMETRY_ONLY"
        else:
            mode = None
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
            telemetry_coalesce_ms=telemetry_coalesce_ms,
            poll_ms=poll_ms,
            auto_start=False,
        )
        if not historian_only:
            ensure_alert_rule(client, path, index, condition_expr)
        paths.append(path)
        if index % 10 == 0 or index == device_count:
            mode_note = "TELEMETRY_ONLY" if historian_only else "FULL/mixed"
            print(f"  seeded {index}/{device_count} mqtt devices ({mode_note}, subscribe {topic!r})")

    if historian_only:
        removed = delete_loadtest_alert_rules(client)
        if removed:
            print(f"  removed {removed} stale loadtest alert rules")

    started = 0
    for path in paths:
        if start_mqtt_driver(client, path):
            started += 1
    print(f"  mqtt drivers started: {started}/{len(paths)} (broker must be reachable from ISPF server)")
    return paths
