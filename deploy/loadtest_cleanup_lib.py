"""Stop loadtest fixtures and silence background load before measurements."""

from __future__ import annotations

from urllib.parse import quote

VIRTUAL_DEVICE_FRAGMENT = "loadtest-dev-"
MQTT_DEVICE_FRAGMENT = "loadtest-mqtt-dev-"

LOADTEST_ALERT_NAME_PREFIXES = (
    "loadtest internal alert",
    "loadtest alert ",
    "mqtt loadtest alert",
)

LOADTEST_CORRELATOR_NAME_PREFIXES = (
    "loadtest correlator",
)

# Drivers that must keep running during load tests (metrics probe syncer).
DRIVER_EXEMPT_PATH_SUFFIXES = (
    "platform-metrics-probe",
)

# Container object types that may hold nested devices or schedules.
_TREE_CONTAINER_TYPES = frozenset(
    {
        "DEVICES",
        "DEVICE",
        "APPLICATION",
        "APPLICATIONS",
        "SCHEDULES",
        "VISUAL_GROUP",
        "CUSTOM",
        "PLATFORM",
    }
)


def _quote(path: str) -> str:
    return quote(path, safe="")


def list_children(client, parent: str) -> list[dict]:
    items = client.request("GET", f"/api/v1/objects?parent={parent}&lite=true").json()
    if not isinstance(items, list):
        return []
    return [item for item in items if isinstance(item, dict) and item.get("path")]


def list_device_paths(client, parent: str = "root.platform.devices") -> list[str]:
    """Direct children only (legacy helper)."""
    return sorted(item.get("path", "") for item in list_children(client, parent) if item.get("path"))


def list_all_device_paths(client, parent: str = "root.platform.devices") -> list[str]:
    """Recursively collect DEVICE paths (mini-TEC, mqtt-lab, nested folders)."""
    paths: list[str] = []
    for item in list_children(client, parent):
        path = item.get("path", "")
        obj_type = item.get("type", "")
        if not path:
            continue
        if obj_type == "DEVICE":
            paths.append(path)
        elif obj_type in _TREE_CONTAINER_TYPES and obj_type != "DEVICE":
            paths.extend(list_all_device_paths(client, path))
    return sorted(set(paths))


def filter_loadtest_paths(paths: list[str], fragment: str) -> list[str]:
    token = f".{fragment}"
    return [path for path in paths if token in path]


def is_exempt_driver_path(path: str) -> bool:
    return any(path.endswith(suffix) or f".{suffix}" in path for suffix in DRIVER_EXEMPT_PATH_SUFFIXES)


def is_loadtest_alert_name(name: str) -> bool:
    return any(name.startswith(prefix) for prefix in LOADTEST_ALERT_NAME_PREFIXES)


def is_loadtest_correlator_name(name: str) -> bool:
    lowered = name.lower()
    return any(lowered.startswith(prefix) for prefix in LOADTEST_CORRELATOR_NAME_PREFIXES)


def stop_driver(client, path: str) -> bool:
    r = client.request("POST", f"/api/v1/drivers/runtime/stop?devicePath={_quote(path)}")
    return r.status_code < 400


def delete_object(client, path: str) -> None:
    stop_driver(client, path)
    client.request("DELETE", f"/api/v1/objects/by-path?path={_quote(path)}")


def stop_devices(client, paths: list[str]) -> int:
    stopped = 0
    for path in paths:
        if stop_driver(client, path):
            stopped += 1
    return stopped


def stop_background_drivers(client, *, keep_paths: set[str] | None = None) -> int:
    """Stop every device driver except metrics probe (and optional keep set)."""
    keep = keep_paths or set()
    stopped = 0
    for path in list_all_device_paths(client):
        if is_exempt_driver_path(path) or path in keep:
            continue
        if stop_driver(client, path):
            stopped += 1
    return stopped


def delete_loadtest_alert_rules(client) -> int:
    items = client.request("GET", "/api/v1/alert-rules").json()
    if not isinstance(items, list):
        return 0
    removed = 0
    for item in items:
        name = item.get("name", "") or ""
        path = item.get("id", "") or ""
        if not is_loadtest_alert_name(name):
            continue
        if not path:
            continue
        r = client.request("DELETE", f"/api/v1/alert-rules/by-path?path={_quote(path)}")
        if r.status_code in (200, 204, 404):
            removed += 1
    return removed


def disable_non_loadtest_alert_rules(client) -> int:
    """Disable demo/lab alert rules so they do not fire during measurement."""
    items = client.request("GET", "/api/v1/alert-rules").json()
    if not isinstance(items, list):
        return 0
    disabled = 0
    for item in items:
        name = item.get("name", "") or ""
        path = item.get("id", "") or ""
        if not path or not item.get("enabled", False):
            continue
        if is_loadtest_alert_name(name):
            continue
        r = client.request(
            "PUT",
            f"/api/v1/alert-rules/by-path?path={_quote(path)}",
            json={"enabled": False},
        )
        if r.status_code < 400:
            disabled += 1
    return disabled


def disable_non_loadtest_correlators(client) -> int:
    items = client.request("GET", "/api/v1/correlators").json()
    if not isinstance(items, list):
        return 0
    disabled = 0
    for item in items:
        name = item.get("name", "") or ""
        path = item.get("id", "") or ""
        if not path or not item.get("enabled", False):
            continue
        if is_loadtest_correlator_name(name):
            continue
        r = client.request(
            "PUT",
            f"/api/v1/correlators/by-path?path={_quote(path)}",
            json={"enabled": False},
        )
        if r.status_code < 400:
            disabled += 1
    return disabled


def _collect_schedule_paths(client, parent: str) -> list[str]:
    paths: list[str] = []
    for item in list_children(client, parent):
        path = item.get("path", "")
        obj_type = item.get("type", "")
        if not path:
            continue
        if obj_type == "SCHEDULE":
            paths.append(path)
        elif obj_type in ("SCHEDULES", "APPLICATION", "APPLICATIONS"):
            paths.extend(_collect_schedule_paths(client, path))
    return paths


def list_all_schedule_paths(client) -> list[str]:
    roots = ["root.platform.schedules"]
    for app in list_children(client, "root.platform.applications"):
        app_path = app.get("path", "")
        if app_path:
            roots.append(f"{app_path}.schedules")
    paths: list[str] = []
    for root in roots:
        try:
            paths.extend(_collect_schedule_paths(client, root))
        except Exception:
            continue
    return sorted(set(paths))


def disable_enabled_schedules(client) -> int:
    disabled = 0
    for path in list_all_schedule_paths(client):
        r = client.request("GET", f"/api/v1/platform-schedules/by-path?path={_quote(path)}")
        if r.status_code >= 400:
            continue
        view = r.json()
        if not view.get("enabled", False):
            continue
        u = client.request(
            "PUT",
            f"/api/v1/platform-schedules/by-path?path={_quote(path)}",
            json={
                "displayName": view.get("displayName"),
                "description": view.get("description"),
                "enabled": False,
                "intervalMs": view.get("intervalMs"),
                "objectPath": view.get("objectPath"),
                "functionName": view.get("functionName"),
            },
        )
        if u.status_code < 400:
            disabled += 1
    return disabled


def silence_background_load(client) -> dict:
    """Stop demo/lab drivers; disable alerts, correlators, schedules that add noise."""
    return {
        "backgroundDriversStopped": stop_background_drivers(client),
        "alertsDisabled": disable_non_loadtest_alert_rules(client),
        "correlatorsDisabled": disable_non_loadtest_correlators(client),
        "schedulesDisabled": disable_enabled_schedules(client),
    }


def purge_devices(client, paths: list[str]) -> int:
    purged = 0
    for path in paths:
        delete_object(client, path)
        purged += 1
    return purged


def cleanup_loadtest_environment(
    client,
    *,
    stop_virtual: bool = True,
    stop_mqtt: bool = True,
    purge_virtual: bool = False,
    purge_mqtt: bool = False,
    delete_alerts: bool = True,
    silence_background: bool = True,
) -> dict:
    """Isolate one load-test contour by stopping/deleting competing fixtures."""
    all_paths = list_all_device_paths(client)
    virtual_paths = filter_loadtest_paths(all_paths, VIRTUAL_DEVICE_FRAGMENT)
    mqtt_paths = filter_loadtest_paths(all_paths, MQTT_DEVICE_FRAGMENT)

    stats = {
        "virtualDevices": len(virtual_paths),
        "mqttDevices": len(mqtt_paths),
        "virtualStopped": 0,
        "mqttStopped": 0,
        "virtualPurged": 0,
        "mqttPurged": 0,
        "alertsRemoved": 0,
        "backgroundDriversStopped": 0,
        "alertsDisabled": 0,
        "correlatorsDisabled": 0,
        "schedulesDisabled": 0,
    }

    if silence_background:
        stats.update(silence_background_load(client))

    if delete_alerts:
        stats["alertsRemoved"] = delete_loadtest_alert_rules(client)

    if stop_virtual and virtual_paths:
        stats["virtualStopped"] = stop_devices(client, virtual_paths)
    if stop_mqtt and mqtt_paths:
        stats["mqttStopped"] = stop_devices(client, mqtt_paths)

    if purge_virtual and virtual_paths:
        stats["virtualPurged"] = purge_devices(client, virtual_paths)
    if purge_mqtt and mqtt_paths:
        stats["mqttPurged"] = purge_devices(client, mqtt_paths)

    return stats


def format_cleanup_stats(stats: dict) -> str:
    return (
        f"background drivers stopped={stats.get('backgroundDriversStopped', 0)}, "
        f"alerts disabled={stats.get('alertsDisabled', 0)}, "
        f"correlators disabled={stats.get('correlatorsDisabled', 0)}, "
        f"schedules disabled={stats.get('schedulesDisabled', 0)}, "
        f"virtual stopped={stats.get('virtualStopped', 0)}/{stats.get('virtualDevices', 0)}, "
        f"mqtt stopped={stats.get('mqttStopped', 0)}/{stats.get('mqttDevices', 0)}, "
        f"loadtest alerts removed={stats.get('alertsRemoved', 0)}, "
        f"purged virtual={stats.get('virtualPurged', 0)}, purged mqtt={stats.get('mqttPurged', 0)}"
    )


def cleanup_for_internal_poll_test(client) -> dict:
    """Virtual poll test: silence background load; poll phase restarts virtual fleet."""
    return cleanup_loadtest_environment(
        client,
        stop_virtual=True,
        stop_mqtt=True,
        purge_mqtt=False,
        delete_alerts=False,
        silence_background=True,
    )


def cleanup_for_mqtt_subscribe_test(client, purge_mqtt: bool = True) -> dict:
    """MQTT subscribe test: stop virtual poll fleet, fresh mqtt devices."""
    return cleanup_loadtest_environment(
        client,
        stop_virtual=True,
        stop_mqtt=True,
        purge_virtual=False,
        purge_mqtt=purge_mqtt,
        delete_alerts=True,
        silence_background=True,
    )
