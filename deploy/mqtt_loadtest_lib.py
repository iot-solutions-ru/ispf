"""Shared helpers for MQTT ingress load testing."""

from __future__ import annotations

from datetime import datetime, timezone

from loadtest_cleanup_lib import delete_loadtest_alert_rules
from urllib.parse import quote

import requests

PREFIX = "loadtest-mqtt"
GATEWAY_NAME = "loadtest-mqtt-gateway"
GATEWAY_INSTANCES_PARENT = "root.platform.instances"
GATEWAY_MODEL_NAME = "mqtt-gateway-v1"
GATEWAY_SENSOR_MODEL_NAME = "mqtt-gateway-sensor-v1"
MODEL_NAME = "mqtt-sensor-v1"
SENSOR_NAME_PREFIX = "loadtest-mqtt-sensor-"
TOPIC_PREFIX = "ispf/loadtest"
GATEWAY_TOPIC_FILTER = "ispf/loadtest/+/temperature"
INGRESS_VARIABLE = "lastIngress"
INGRESS_HISTORY_FIELD = "raw"

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


def gateway_instances_parent() -> str:
    return GATEWAY_INSTANCES_PARENT


def gateway_sensors_path() -> str:
    """Legacy path — pre-instances migration (gateway.sensors under devices)."""
    return f"{gateway_path()}.sensors"


def sensor_path(index: int, pad: int = 5) -> str:
    return f"{gateway_instances_parent()}.{sensor_name(index, pad)}"


def device_path(index: int, pad: int = 5) -> str:
    return f"root.platform.devices.{device_name(index, pad)}"


def mqtt_topic(index: int, pad: int = 5) -> str:
    return f"{TOPIC_PREFIX}/{index:0{pad}d}/temperature"


def mqtt_bench_ingress_configuration(
    *,
    no_l0_coalesce: bool = True,
    callback_threads: int | None = None,
    callback_queue_capacity: int | None = None,
) -> dict[str, str]:
    """Driver config for load tests (1:1 MQTT message handling on L0).

    callbackThreads / callbackQueueCapacity are omitted by default so each driver inherits
    server defaults (ISPF_DRIVER_MQTT_CALLBACK_* / runtime-settings driver.mqtt-callback-*).
    Pass explicit values only to override per device.
    """
    if not no_l0_coalesce:
        return {}
    cfg: dict[str, str] = {"ingressCoalesceEnabled": "false"}
    if callback_threads is not None:
        cfg["callbackThreads"] = str(callback_threads)
    if callback_queue_capacity is not None:
        cfg["callbackQueueCapacity"] = str(callback_queue_capacity)
    return cfg


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


def find_relative_blueprint_id(client: Client, blueprint_name: str) -> str | None:
    r = client.request("GET", "/api/v1/relative-blueprints")
    if r.status_code == 200:
        for item in r.json():
            if item.get("name") == blueprint_name:
                return item.get("id")
    r = client.request("GET", "/api/v1/models")
    if r.status_code == 200:
        for item in r.json():
            if item.get("name") == blueprint_name:
                return item.get("id")
    return None


def apply_relative_blueprint(client: Client, blueprint_id: str, object_path: str) -> None:
    r = client.request(
        "POST",
        f"/api/v1/relative-blueprints/{blueprint_id}/apply?objectPath={quote(object_path, safe='')}",
    )
    if r.status_code >= 400:
        raise RuntimeError(
            f"apply blueprint {blueprint_id} on {object_path}: HTTP {r.status_code} {r.text[:200]}"
        )


def ensure_relative_blueprint(client: Client, blueprint_name: str, body: dict) -> str:
    """Find or create a RELATIVE blueprint (prod has fixtures disabled)."""
    existing = find_relative_blueprint_id(client, blueprint_name)
    if existing:
        return existing
    payload = {k: v for k, v in body.items() if k != "type"}
    payload["name"] = blueprint_name
    create = client.request("POST", "/api/v1/relative-blueprints", json=payload)
    if create.status_code == 409 or create.status_code == 403:
        existing = find_relative_blueprint_id(client, blueprint_name)
        if existing:
            return existing
    if create.status_code >= 400:
        raise RuntimeError(
            f"create relative blueprint {blueprint_name}: HTTP {create.status_code} {create.text[:200]}"
        )
    return create.json()["id"]


MQTT_SENSOR_MODEL_BODY: dict = {
    "name": MODEL_NAME,
    "description": "MQTT temperature sensor (minimal lab model)",
    "targetObjectType": "DEVICE",
    "suitabilityExpression": "",
    "variables": [
        {
            "name": "temperature",
            "description": "Current temperature reading",
            "group": "telemetry",
            "schema": {
                "name": "temperature",
                "fields": [
                    {"name": "value", "type": "DOUBLE"},
                    {"name": "unit", "type": "STRING"},
                    {"name": "raw", "type": "STRING"},
                ],
            },
            "readable": True,
            "writable": True,
            "historyEnabled": True,
            "defaultValue": {
                "schema": {
                    "name": "temperature",
                    "fields": [
                        {"name": "value", "type": "DOUBLE"},
                        {"name": "unit", "type": "STRING"},
                        {"name": "raw", "type": "STRING"},
                    ],
                },
                "rows": [{"value": 0.0, "unit": "C", "raw": ""}],
            },
        },
        {
            "name": "threshold",
            "description": "Alarm threshold in Celsius",
            "group": "config",
            "schema": {"name": "threshold", "fields": [{"name": "value", "type": "DOUBLE"}]},
            "readable": True,
            "writable": True,
            "defaultValue": {
                "schema": {"name": "threshold", "fields": [{"name": "value", "type": "DOUBLE"}]},
                "rows": [{"value": 35.0}],
            },
        },
    ],
    "events": [
        {
            "name": "messageReceived",
            "description": "MQTT payload received (load test / ingress audit)",
            "level": "INFO",
            "schema": {
                "name": "mqttPayload",
                "fields": [{"name": "raw", "type": "STRING"}],
            },
        }
    ],
    "functions": [],
    "bindings": [],
    "parameters": {},
}

_MQTT_INGRESS_SCHEMA = {
    "name": "mqttIngress",
    "fields": [
        {"name": "topic", "type": "STRING"},
        {"name": "raw", "type": "STRING"},
    ],
}
_DISPATCH_STATUS_SCHEMA = {
    "name": "dispatchStatus",
    "fields": [
        {"name": "ok", "type": "BOOLEAN"},
        {"name": "message", "type": "STRING"},
        {"name": "routedPath", "type": "STRING"},
    ],
}
_STRING_VALUE_SCHEMA = {"name": "stringValue", "fields": [{"name": "value", "type": "STRING"}]}
_INTEGER_VALUE_SCHEMA = {"name": "integerValue", "fields": [{"name": "value", "type": "INTEGER"}]}

MQTT_GATEWAY_MODEL_BODY: dict = {
    "name": GATEWAY_MODEL_NAME,
    "description": "MQTT ingress gateway — routes lastIngress to child sensors via dispatchTelemetry",
    "targetObjectType": "DEVICE",
    "suitabilityExpression": "",
    "variables": [
        {
            "name": "lastIngress",
            "description": "Latest MQTT message (topic + raw payload)",
            "group": "ingress",
            "schema": _MQTT_INGRESS_SCHEMA,
            "readable": True,
            "writable": False,
            "defaultValue": {
                "schema": _MQTT_INGRESS_SCHEMA,
                "rows": [{"topic": "", "raw": ""}],
            },
        },
        {
            "name": "dispatchStatus",
            "description": "Last dispatchTelemetry result",
            "group": "runtime",
            "schema": _DISPATCH_STATUS_SCHEMA,
            "readable": True,
            "writable": False,
            "defaultValue": {
                "schema": _DISPATCH_STATUS_SCHEMA,
                "rows": [{"ok": False, "message": "", "routedPath": ""}],
            },
        },
        {
            "name": "sensorParentPath",
            "description": "Parent path for routed child sensors",
            "group": "config",
            "schema": _STRING_VALUE_SCHEMA,
            "readable": True,
            "writable": True,
            "defaultValue": {
                "schema": _STRING_VALUE_SCHEMA,
                "rows": [{"value": GATEWAY_INSTANCES_PARENT}],
            },
        },
        {
            "name": "sensorNamePrefix",
            "description": "Child sensor object name prefix (suffix from topic index)",
            "group": "config",
            "schema": _STRING_VALUE_SCHEMA,
            "readable": True,
            "writable": True,
            "defaultValue": {
                "schema": _STRING_VALUE_SCHEMA,
                "rows": [{"value": SENSOR_NAME_PREFIX}],
            },
        },
        {
            "name": "topicIndexPattern",
            "description": "Regex with capture group for sensor index in MQTT topic",
            "group": "config",
            "schema": _STRING_VALUE_SCHEMA,
            "readable": True,
            "writable": True,
            "defaultValue": {
                "schema": _STRING_VALUE_SCHEMA,
                "rows": [{"value": "ispf/loadtest/(\\d+)/temperature"}],
            },
        },
        {
            "name": "instanceModelName",
            "description": "INSTANCE type name for gateway child sensors",
            "group": "config",
            "schema": _STRING_VALUE_SCHEMA,
            "readable": True,
            "writable": True,
            "defaultValue": {
                "schema": _STRING_VALUE_SCHEMA,
                "rows": [{"value": GATEWAY_SENSOR_MODEL_NAME}],
            },
        },
        {
            "name": "driverId",
            "description": "Attached driver plugin id",
            "group": "driver",
            "schema": _STRING_VALUE_SCHEMA,
            "readable": True,
            "writable": True,
            "defaultValue": {"schema": _STRING_VALUE_SCHEMA, "rows": [{"value": "mqtt"}]},
        },
        {
            "name": "driverStatus",
            "description": "Driver runtime status",
            "group": "driver",
            "schema": _STRING_VALUE_SCHEMA,
            "readable": True,
            "writable": False,
            "defaultValue": {"schema": _STRING_VALUE_SCHEMA, "rows": [{"value": "STOPPED"}]},
        },
        {
            "name": "driverPollIntervalMs",
            "description": "Driver polling interval",
            "group": "driver",
            "schema": _INTEGER_VALUE_SCHEMA,
            "readable": True,
            "writable": True,
            "defaultValue": {"schema": _INTEGER_VALUE_SCHEMA, "rows": [{"value": 5000}]},
        },
        {
            "name": "driverConfigJson",
            "description": "Driver configuration JSON",
            "group": "driver",
            "schema": _STRING_VALUE_SCHEMA,
            "readable": True,
            "writable": True,
            "defaultValue": {
                "schema": _STRING_VALUE_SCHEMA,
                "rows": [{"value": '{"ingressVariable":"lastIngress"}'}],
            },
        },
        {
            "name": "driverPointMappingsJson",
            "description": "Driver point mappings JSON",
            "group": "driver",
            "schema": _STRING_VALUE_SCHEMA,
            "readable": True,
            "writable": True,
            "defaultValue": {
                "schema": _STRING_VALUE_SCHEMA,
                "rows": [{"value": '{"ingress":"ispf/loadtest/+/temperature"}'}],
            },
        },
    ],
    "events": [],
    "functions": [
        {
            "name": "dispatchTelemetry",
            "description": "Route lastIngress MQTT payload to a child sensor object",
            "inputSchema": _MQTT_INGRESS_SCHEMA,
            "outputSchema": _DISPATCH_STATUS_SCHEMA,
        }
    ],
    "bindings": [],
    "parameters": {},
}

MQTT_GATEWAY_SENSOR_INSTANCE_BODY: dict = {
    "name": GATEWAY_SENSOR_MODEL_NAME,
    "description": (
        "MQTT temperature sensor — child of mqtt-gateway-v1 "
        "(telemetry via dispatchTelemetry, threshold alarms)"
    ),
    "targetObjectType": "CUSTOM",
    "suitabilityExpression": "",
    "variables": [
        {
            "name": "temperature",
            "description": "Current temperature reading",
            "group": "telemetry",
            "schema": {
                "name": "temperature",
                "fields": [
                    {"name": "value", "type": "DOUBLE"},
                    {"name": "unit", "type": "STRING"},
                ],
            },
            "readable": True,
            "writable": False,
            "historyEnabled": True,
            "defaultValue": {
                "schema": {
                    "name": "temperature",
                    "fields": [
                        {"name": "value", "type": "DOUBLE"},
                        {"name": "unit", "type": "STRING"},
                    ],
                },
                "rows": [{"value": 22.0, "unit": "C"}],
            },
        },
        {
            "name": "threshold",
            "description": "Alarm threshold in Celsius",
            "group": "config",
            "schema": {"name": "threshold", "fields": [{"name": "value", "type": "DOUBLE"}]},
            "readable": True,
            "writable": True,
            "defaultValue": {
                "schema": {"name": "threshold", "fields": [{"name": "value", "type": "DOUBLE"}]},
                "rows": [{"value": 35.0}],
            },
        },
        {
            "name": "temperaturePercent",
            "description": "Temperature normalized to 0-100% (-20..50 °C)",
            "group": "telemetry",
            "schema": {"name": "temperaturePercent", "fields": [{"name": "value", "type": "DOUBLE"}]},
            "readable": True,
            "writable": False,
            "defaultValue": {
                "schema": {"name": "temperaturePercent", "fields": [{"name": "value", "type": "DOUBLE"}]},
                "rows": [{"value": 0.0}],
            },
        },
        {
            "name": "alarmActive",
            "description": "Whether temperature exceeds threshold",
            "group": "status",
            "schema": {"name": "alarmActive", "fields": [{"name": "value", "type": "BOOLEAN"}]},
            "readable": True,
            "writable": False,
            "defaultValue": {
                "schema": {"name": "alarmActive", "fields": [{"name": "value", "type": "BOOLEAN"}]},
                "rows": [{"value": False}],
            },
        },
        {
            "name": "alarmAcknowledged",
            "description": "Operator acknowledged the active alarm",
            "group": "status",
            "schema": {"name": "alarmAcknowledged", "fields": [{"name": "value", "type": "BOOLEAN"}]},
            "readable": True,
            "writable": True,
            "defaultValue": {
                "schema": {"name": "alarmAcknowledged", "fields": [{"name": "value", "type": "BOOLEAN"}]},
                "rows": [{"value": False}],
            },
        },
    ],
    "events": [
        {
            "name": "thresholdExceeded",
            "description": "Temperature exceeded configured threshold",
            "level": "WARNING",
            "schema": {
                "name": "temperature",
                "fields": [
                    {"name": "value", "type": "DOUBLE"},
                    {"name": "unit", "type": "STRING"},
                ],
            },
        }
    ],
    "functions": [
        {
            "name": "acknowledgeAlarm",
            "description": "Acknowledge active temperature alarm",
            "inputSchema": {"name": "voidInput", "fields": []},
            "outputSchema": {
                "name": "functionResult",
                "fields": [
                    {"name": "success", "type": "BOOLEAN"},
                    {"name": "message", "type": "STRING"},
                ],
            },
        }
    ],
    "bindings": [
        {
            "id": "alarm-active",
            "expression": "hysteresis(temperature, 35, 33)",
            "targetVariable": "alarmActive",
        }
    ],
    "parameters": {"unit": "C"},
}


def _instance_type_is_stale(current: dict, body: dict) -> bool:
    if (current.get("description") or "") != (body.get("description") or ""):
        return True
    if (current.get("targetObjectType") or "") != (body.get("targetObjectType") or ""):
        return True
    return len(current.get("variables") or []) < len(body.get("variables") or [])


def ensure_instance_type_blueprint(client: Client, blueprint_name: str, body: dict) -> str:
    payload = {k: v for k, v in body.items() if k != "type"}
    payload["name"] = blueprint_name
    existing = find_instance_type_id(client, blueprint_name)
    if existing:
        r = client.request("GET", f"/api/v1/instance-types/{existing}")
        if r.status_code == 200 and _instance_type_is_stale(r.json(), body):
            upd = client.request(
                "PUT",
                f"/api/v1/instance-types/{existing}",
                json=payload,
            )
            if upd.status_code >= 400:
                raise RuntimeError(
                    f"update instance type {blueprint_name}: HTTP {upd.status_code} {upd.text[:200]}"
                )
        return existing
    create = client.request("POST", "/api/v1/instance-types", json=payload)
    if create.status_code == 409 or create.status_code == 403:
        existing = find_instance_type_id(client, blueprint_name)
        if existing:
            return existing
    if create.status_code >= 400:
        raise RuntimeError(
            f"create instance type {blueprint_name}: HTTP {create.status_code} {create.text[:200]}"
        )
    return create.json()["id"]


def find_instance_type_id(client: Client, blueprint_name: str) -> str | None:
    r = client.request("GET", f"/api/v1/instance-types/by-name/{quote(blueprint_name, safe='')}")
    if r.status_code == 200:
        return r.json().get("id")
    r = client.request("GET", "/api/v1/instance-types")
    if r.status_code == 200:
        for item in r.json():
            if item.get("name") == blueprint_name:
                return item.get("id")
    return None


def instantiate_instance_type(
    client: Client,
    instance_type_id: str,
    parent_path: str,
    instance_name: str,
) -> str:
    r = client.request(
        "POST",
        f"/api/v1/instance-types/{instance_type_id}/instantiate",
        json={
            "parentPath": parent_path,
            "instanceName": instance_name,
            "parameters": {},
        },
    )
    if r.status_code >= 400:
        raise RuntimeError(
            f"instantiate {instance_name} under {parent_path}: HTTP {r.status_code} {r.text[:200]}"
        )
    path = r.json().get("path")
    if not path:
        path = f"{parent_path}.{instance_name}"
    return path


def ensure_mqtt_gateway_sensor_instance_type(client: Client) -> str:
    """Register mqtt-gateway-sensor-v1 INSTANCE type when fixtures are disabled."""
    return ensure_instance_type_blueprint(
        client,
        GATEWAY_SENSOR_MODEL_NAME,
        MQTT_GATEWAY_SENSOR_INSTANCE_BODY,
    )


def ensure_mqtt_sensor_model(client: Client) -> str:
    """Register mqtt-sensor-v1 when fixtures are disabled."""
    return ensure_relative_blueprint(client, MODEL_NAME, MQTT_SENSOR_MODEL_BODY)


def ensure_mqtt_gateway_model(client: Client) -> str:
    """Register mqtt-gateway-v1 when fixtures are disabled."""
    return ensure_relative_blueprint(client, GATEWAY_MODEL_NAME, MQTT_GATEWAY_MODEL_BODY)


def list_mqtt_loadtest_devices(client: Client) -> list[str]:
    paths: list[str] = []
    items = client.request("GET", "/api/v1/objects?parent=root.platform.devices&lite=true").json()
    for item in items:
        path = item.get("path", "")
        if f".{PREFIX}-dev-" in path:
            paths.append(path)
    if paths:
        return sorted(paths)
    instances_parent = gateway_instances_parent()
    try:
        items = client.request(
            "GET",
            f"/api/v1/objects?parent={quote(instances_parent, safe='')}&lite=true",
        ).json()
        for item in items:
            path = item.get("path", "")
            if path and SENSOR_NAME_PREFIX in path.split(".")[-1]:
                paths.append(path)
    except Exception:
        pass
    return sorted(paths)


def delete_device(client: Client, path: str) -> None:
    client.request("POST", f"/api/v1/drivers/runtime/stop?devicePath={quote(path, safe='')}")
    r = client.request("DELETE", f"/api/v1/objects/by-path?path={quote(path, safe='')}")
    if r.status_code not in (200, 204, 404):
        raise RuntimeError(f"delete {path}: HTTP {r.status_code} {r.text[:160]}")


def ensure_event_journal_enabled(client: Client, path: str) -> None:
    r = client.request(
        "PATCH",
        f"/api/v1/objects/by-path?path={quote(path, safe='')}",
        json={"eventJournalEnabled": True},
    )
    if r.status_code >= 400:
        raise RuntimeError(f"enable event journal {path}: HTTP {r.status_code} {r.text[:200]}")


def ensure_mqtt_sensor_structure(client: Client, path: str) -> None:
    """Create mqtt-sensor-v1 variables/events when blueprint apply is forbidden (403)."""
    for var in MQTT_SENSOR_MODEL_BODY["variables"]:
        name = var["name"]
        existing = client.request(
            "GET",
            f"/api/v1/objects/by-path/variables/detail?path={quote(path, safe='')}&name={quote(name, safe='')}",
        )
        if existing.status_code == 200:
            continue
        body = {
            "name": name,
            "schema": var["schema"],
            "readable": var.get("readable", True),
            "writable": var.get("writable", True),
            "initialValue": var.get("defaultValue"),
            "historyEnabled": var.get("historyEnabled", False),
        }
        r = client.request(
            "POST",
            f"/api/v1/objects/by-path/variables?path={quote(path, safe='')}",
            json=body,
        )
        if r.status_code >= 400:
            raise RuntimeError(
                f"create variable {name} on {path}: HTTP {r.status_code} {r.text[:200]}"
            )
    for event in MQTT_SENSOR_MODEL_BODY["events"]:
        r = client.request(
            "PUT",
            f"/api/v1/objects/by-path/events?path={quote(path, safe='')}",
            json=event,
        )
        if r.status_code >= 400:
            raise RuntimeError(
                f"upsert event {event.get('name')} on {path}: HTTP {r.status_code} {r.text[:200]}"
            )


def apply_mqtt_sensor_model(client: Client, path: str, model_id: str | None) -> None:
    resolved = model_id
    if not resolved:
        try:
            resolved = ensure_mqtt_sensor_model(client)
        except RuntimeError as error:
            if "403" not in str(error):
                raise
            resolved = None
    if resolved:
        try:
            apply_relative_blueprint(client, resolved, path)
            return
        except RuntimeError as error:
            if "403" not in str(error):
                raise
    ensure_mqtt_sensor_structure(client, path)


def ensure_device(
    client: Client,
    index: int,
    pad: int,
    model_id: str | None = None,
    *,
    apply_blueprint: bool = True,
) -> str:
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
            if apply_blueprint:
                apply_mqtt_sensor_model(client, path, model_id)
            ensure_event_journal_enabled(client, path)
            return path
        delete_device(client, path)
        r = client.request("POST", "/api/v1/objects", json=body)
    if r.status_code >= 400:
        raise RuntimeError(f"create device {name}: HTTP {r.status_code} {r.text[:200]}")
    if apply_blueprint:
        apply_mqtt_sensor_model(client, path, model_id)
    ensure_event_journal_enabled(client, path)
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


def stop_mqtt_driver(client: Client, path: str) -> None:
    client.request("POST", f"/api/v1/drivers/runtime/stop?devicePath={quote(path, safe='')}")


def start_drivers_in_batches(
    client: Client,
    paths: list[str],
    *,
    batch_size: int = 10,
    pause_s: float = 1.0,
) -> None:
    import time

    for offset in range(0, len(paths), batch_size):
        batch = paths[offset : offset + batch_size]
        for path in batch:
            start_mqtt_driver(client, path)
        time.sleep(pause_s)


def restart_drivers_until_running(
    client: Client,
    paths: list[str],
    *,
    max_passes: int = 5,
    batch_size: int = 10,
    pause_s: float = 1.5,
) -> tuple[int, int]:
    import time

    for attempt in range(1, max_passes + 1):
        failed = [
            path
            for path in paths
            if (driver_status(client, path) or {}).get("status") != "RUNNING"
        ]
        if not failed:
            break
        print(f"  restart pass {attempt}: {len(failed)} drivers not RUNNING")
        for offset in range(0, len(failed), batch_size):
            batch = failed[offset : offset + batch_size]
            for path in batch:
                stop_mqtt_driver(client, path)
            time.sleep(0.3)
            for path in batch:
                start_mqtt_driver(client, path)
            time.sleep(pause_s)
    running = sum(
        1 for path in paths if (driver_status(client, path) or {}).get("status") == "RUNNING"
    )
    return running, len(paths)


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


def enable_variable_history(
    client: Client,
    path: str,
    variable_name: str,
    *,
    history_enabled: bool = True,
) -> None:
    r = client.request(
        "PATCH",
        f"/api/v1/objects/by-path/variables/history?path={quote(path, safe='')}&name={variable_name}",
        json={"historyEnabled": history_enabled},
    )
    if r.status_code >= 400:
        raise RuntimeError(
            f"enable history {variable_name} on {path}: HTTP {r.status_code} {r.text[:200]}"
        )


def clear_binding_rules(client: Client, path: str) -> None:
    r = client.request(
        "PUT",
        f"/api/v1/objects/by-path/binding-rules?path={quote(path, safe='')}",
        json=[],
    )
    if r.status_code >= 400:
        raise RuntimeError(f"clear binding rules {path}: HTTP {r.status_code} {r.text[:200]}")


def read_last_ingress_raw(client: Client, path: str) -> str | None:
    r = client.request(
        "GET",
        f"/api/v1/objects/by-path/variables/detail?path={quote(path, safe='')}&name={INGRESS_VARIABLE}",
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
    return None


def _iso_instant(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def variable_history_field_sample_count(
    client: Client,
    object_path: str,
    variable_name: str,
    field_name: str,
    from_instant: datetime | None = None,
    to_instant: datetime | None = None,
) -> int:
    """Count historian samples for one variable field (history query, up to 10k points)."""
    params: dict[str, str] = {
        "path": object_path,
        "name": variable_name,
        "field": field_name,
        "limit": "10000",
    }
    if from_instant is not None:
        params["from"] = _iso_instant(from_instant)
    if to_instant is not None:
        params["to"] = _iso_instant(to_instant)
    r = client.request("GET", "/api/v1/objects/by-path/variables/history", params=params)
    if r.status_code >= 400:
        raise RuntimeError(
            f"history query {object_path}/{variable_name}.{field_name}: "
            f"HTTP {r.status_code} {r.text[:200]}"
        )
    samples = r.json().get("samples") or []
    return len(samples)


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


def _delete_gateway_child_sensors(client: Client) -> None:
    """Remove loadtest gateway child sensors (instances + legacy gateway.sensors folder)."""
    for parent in (gateway_instances_parent(), gateway_sensors_path()):
        try:
            children = client.request(
                "GET",
                f"/api/v1/objects?parent={quote(parent, safe='')}&lite=true",
            ).json()
            for item in children:
                path = item.get("path", "")
                name = path.rsplit(".", 1)[-1] if path else ""
                if path and name.startswith(SENSOR_NAME_PREFIX):
                    delete_device(client, path)
        except Exception:
            pass
    client.request(
        "DELETE",
        f"/api/v1/objects/by-path?path={quote(gateway_sensors_path(), safe='')}",
    )


def delete_gateway_tree(client: Client) -> None:
    """Remove prior gateway loadtest tree (stale bindings/models break orchestrator)."""
    gw = gateway_path()
    _delete_gateway_child_sensors(client)
    if driver_status(client, gw):
        delete_device(client, gw)
    else:
        client.request("DELETE", f"/api/v1/objects/by-path?path={quote(gw, safe='')}")


def ensure_gateway_tree(
    client: Client,
    device_count: int,
    pad: int,
    gateway_model_id: str,
    instance_type_id: str,
    child_coalesce_ms: int | None = None,
) -> tuple[str, list[str]]:
    delete_gateway_tree(client)
    gw = gateway_path()
    instances_parent = gateway_instances_parent()
    _create_object(
        client,
        "root.platform.devices",
        GATEWAY_NAME,
        "DEVICE",
        "MQTT loadtest gateway",
        "Single MQTT connection orchestrator for load test",
    )
    apply_relative_blueprint(client, gateway_model_id, gw)
    put_gateway_binding_rules(client, gw)
    _set_string_variable(client, gw, "sensorParentPath", instances_parent)
    _set_string_variable(client, gw, "instanceModelName", GATEWAY_SENSOR_MODEL_NAME)

    sensor_paths: list[str] = []
    for index in range(1, device_count + 1):
        name = sensor_name(index, pad)
        path = instantiate_instance_type(client, instance_type_id, instances_parent, name)
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
    *,
    ingress_topic_lanes: bool = True,
) -> dict:
    if topics:
        point_mappings = {f"t{index}": topic for index, topic in enumerate(topics)}
    else:
        point_mappings = {"ingress": GATEWAY_TOPIC_FILTER}
    driver_configuration = {
        "ingressVariable": "lastIngress",
        "ingressTopicLanes": "true" if ingress_topic_lanes else "false",
    }
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
        configuration=driver_configuration,
    )


def ensure_gateway_ingress_only(client: Client, gateway_model_id: str) -> str:
    """Single MQTT gateway: lastIngress historian only (no child sensors, no dispatch binding)."""
    delete_gateway_tree(client)
    gw = gateway_path()
    client.request("DELETE", f"/api/v1/objects/by-path?path={quote(gw, safe='')}")
    _create_object(
        client,
        "root.platform.devices",
        GATEWAY_NAME,
        "DEVICE",
        "MQTT loadtest gateway (ingress historian)",
        "MQTT ingress load test — historian on lastIngress only",
    )
    try:
        apply_relative_blueprint(client, gateway_model_id, gw)
    except RuntimeError:
        client.request("DELETE", f"/api/v1/objects/by-path?path={quote(gw, safe='')}")
        _create_object(
            client,
            "root.platform.devices",
            GATEWAY_NAME,
            "DEVICE",
            "MQTT loadtest gateway (ingress historian)",
            "MQTT ingress load test — historian on lastIngress only",
        )
        apply_relative_blueprint(client, gateway_model_id, gw)
    clear_binding_rules(client, gw)
    enable_variable_history(client, gw, INGRESS_VARIABLE, history_enabled=True)
    return gw


def seed_mqtt_gateway_ingress_history(
    client: Client,
    topic_count: int,
    broker_url: str,
    poll_ms: int = 5000,
    topics: list[str] | None = None,
    telemetry_coalesce_ms: int | None = None,
) -> str:
    """Gateway-only MQTT ingress benchmark: historian samples on lastIngress.raw."""
    gateway_model_id = ensure_mqtt_gateway_model(client)
    gw = ensure_gateway_ingress_only(client, gateway_model_id)
    delete_loadtest_alert_rules(client)
    configure_mqtt_gateway_driver(
        client,
        gw,
        broker_url,
        topics=topics,
        telemetry_coalesce_ms=telemetry_coalesce_ms,
        poll_ms=poll_ms,
        auto_start=False,
        ingress_topic_lanes=False,
    )
    print(
        f"  gateway {gw}: ingress historian on {INGRESS_VARIABLE}.{INGRESS_HISTORY_FIELD}, "
        f"no child sensors ({topic_count} publisher topics, ingressTopicLanes=false)"
    )
    if start_mqtt_driver(client, gw):
        print("  mqtt gateway driver started (broker must be reachable from ISPF server)")
    else:
        print("  WARN: mqtt gateway driver failed to start")
    return gw


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
    gateway_model_id = ensure_mqtt_gateway_model(client)
    instance_type_id = ensure_mqtt_gateway_sensor_instance_type(client)
    gw, sensor_paths = ensure_gateway_tree(
        client,
        device_count,
        pad,
        gateway_model_id,
        instance_type_id,
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
    print(f"  gateway {gw}: 1 mqtt driver, {device_count} INSTANCE child sensors under {gateway_instances_parent()}")

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
    model_id = ensure_mqtt_sensor_model(client)
    paths: list[str] = []
    if historian_only:
        telemetry_only_count = device_count
    else:
        telemetry_only_count = int(device_count * telemetry_mix_ratio) if telemetry_mix_ratio > 0 else 0
    resolved_topics = topics or []

    for index in range(1, device_count + 1):
        path = ensure_device(client, index, pad, model_id)
        put_binding_rules(client, path)
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


def seed_one_mqtt_device(
    client: Client,
    device_name: str,
    topic: str,
    broker_url: str,
    *,
    telemetry_coalesce_ms: int | None = None,
    poll_ms: int = 5000,
    history_variable: str = "temperature",
) -> str:
    """Create a single MQTT device with historian on the given variable."""
    model_id = ensure_mqtt_sensor_model(client)
    path = f"root.platform.devices.{device_name}"
    body = {
        "parentPath": "root.platform.devices",
        "name": device_name,
        "type": "DEVICE",
        "displayName": device_name,
        "description": "Single MQTT device (lab)",
    }
    r = client.request("POST", "/api/v1/objects", json=body)
    if r.status_code == 409:
        delete_device(client, path)
        r = client.request("POST", "/api/v1/objects", json=body)
    if r.status_code >= 400:
        raise RuntimeError(f"create device {device_name}: HTTP {r.status_code} {r.text[:200]}")
    apply_relative_blueprint(client, model_id, path)
    put_binding_rules(client, path)
    configure_mqtt_driver(
        client,
        path,
        broker_url,
        topic,
        telemetry_publish_mode="TELEMETRY_ONLY",
        telemetry_coalesce_ms=telemetry_coalesce_ms,
        poll_ms=poll_ms,
        auto_start=False,
    )
    enable_variable_history(client, path, history_variable, history_enabled=True)
    delete_loadtest_alert_rules(client)
    if start_mqtt_driver(client, path):
        print(f"  mqtt driver started on {path}")
    else:
        print(f"  WARN: mqtt driver failed to start on {path} (broker must be reachable)")
    return path
