#!/usr/bin/env python3
"""One MQTT device configured for EVENT_JOURNAL_ONLY (internal event journal path)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from urllib.parse import quote

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mqtt_loadtest_lib import (
    Client,
    MQTT_SENSOR_MODEL_BODY,
    apply_relative_model,
    configure_mqtt_driver,
    driver_status,
    ensure_mqtt_sensor_model,
    ensure_relative_model,
    seed_one_mqtt_device,
)

MESSAGE_RECEIVED_EVENT = {
    "name": "messageReceived",
    "description": "MQTT payload received (load test / ingress audit)",
    "level": "INFO",
    "schema": {
        "name": "mqttPayload",
        "fields": [{"name": "raw", "type": "STRING"}],
    },
}


def ensure_message_event_on_model(client: Client) -> None:
    body = dict(MQTT_SENSOR_MODEL_BODY)
    events = list(body.get("events") or [])
    if not any(e.get("name") == "messageReceived" for e in events):
        events.append(MESSAGE_RECEIVED_EVENT)
        body["events"] = events
    model_id = ensure_relative_model(client, body["name"], body)
    return model_id


def main() -> int:
    parser = argparse.ArgumentParser(description="MQTT device with EVENT_JOURNAL_ONLY policy")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--broker-url", default="tcp://127.0.0.1:1883")
    parser.add_argument("--device-name", default="mqtt-device-01")
    parser.add_argument("--topic", default="")
    parser.add_argument("--event-name", default="messageReceived")
    parser.add_argument("--telemetry-coalesce-ms", type=int, default=1)
    parser.add_argument("--skip-purge", action="store_true", default=True)
    args = parser.parse_args()

    topic = args.topic or f"ispf/{args.device_name}/temperature"
    coalesce = args.telemetry_coalesce_ms if args.telemetry_coalesce_ms > 0 else None

    client = Client(args.base_url, None, 60.0)
    client.login(args.username, args.password)

    model_id = ensure_message_event_on_model(client)
    path = f"root.platform.devices.{args.device_name}"

    try:
        client.request("GET", f"/api/v1/objects/by-path?path={quote(path, safe='')}")
    except Exception:
        path = seed_one_mqtt_device(
            client,
            args.device_name,
            topic,
            args.broker_url,
            telemetry_coalesce_ms=coalesce,
        )
    else:
        apply_relative_model(client, model_id, path)

    configure_mqtt_driver(
        client,
        path,
        args.broker_url,
        topic,
        telemetry_publish_mode="EVENT_JOURNAL_ONLY",
        telemetry_coalesce_ms=coalesce,
        configuration={"ingressEventName": args.event_name},
        auto_start=True,
    )

    print(f"  device: {path}")
    print(f"  mode: EVENT_JOURNAL_ONLY")
    print(f"  event: {args.event_name}")
    print(f"  driver: {driver_status(client, path)}")
    print(f"  topic: {topic}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
