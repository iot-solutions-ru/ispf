#!/usr/bin/env python3
"""
MQTT tap subscriber — verifies the broker delivers messages (external receiver device).

Requires: pip install paho-mqtt
"""

from __future__ import annotations

import argparse
import sys
import time

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("Install dependency: pip install paho-mqtt", file=sys.stderr)
    raise SystemExit(1)

TOPIC_PREFIX = "ispf/loadtest"


def main() -> int:
    parser = argparse.ArgumentParser(description="MQTT load test tap subscriber")
    parser.add_argument("--broker", default="tcp://127.0.0.1:1883")
    parser.add_argument("--topic-filter", default=f"{TOPIC_PREFIX}/+/temperature")
    parser.add_argument("--duration-seconds", type=float, default=30.0)
    parser.add_argument("--client-id", default="ispf-mqtt-loadtest-tap")
    args = parser.parse_args()

    host_port = args.broker.replace("tcp://", "")
    host, port_s = host_port.rsplit(":", 1) if ":" in host_port else (host_port, "1883")
    port = int(port_s)

    received = 0
    last_topic = ""

    def on_connect(client, _userdata, _flags, rc):
        if rc != 0:
            print(f"Connect failed: rc={rc}", file=sys.stderr)
            return
        client.subscribe(args.topic_filter, qos=0)
        print(f"Subscribed: {args.topic_filter}")

    def on_message(_client, _userdata, msg):
        nonlocal received, last_topic
        received += 1
        last_topic = msg.topic

    client = mqtt.Client(client_id=args.client_id, protocol=mqtt.MQTTv311)
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(host, port, keepalive=30)
    client.loop_start()

    print(f"Listening on {host}:{port} for {args.duration_seconds:.0f}s ...")
    time.sleep(args.duration_seconds)
    client.loop_stop()
    client.disconnect()

    rate = received / max(args.duration_seconds, 0.001)
    print(f"Tap received {received} messages ({rate:.1f} msg/s), last topic={last_topic!r}")
    return 0 if received > 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
