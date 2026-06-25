#!/usr/bin/env python3
"""
Publish synthetic temperature payloads to Mosquitto for MQTT ingress load tests.

Requires: pip install paho-mqtt
"""

from __future__ import annotations

import argparse
import math
import sys
import time

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("Install dependency: pip install paho-mqtt", file=sys.stderr)
    raise SystemExit(1)

TOPIC_PREFIX = "ispf/loadtest"


def mqtt_topic(index: int, pad: int = 5) -> str:
    return f"{TOPIC_PREFIX}/{index:0{pad}d}/temperature"


def main() -> int:
    parser = argparse.ArgumentParser(description="MQTT load test publisher (synthetic sensor)")
    parser.add_argument("--broker", default="tcp://127.0.0.1:1883", help="Broker URL (tcp://host:port)")
    parser.add_argument("--devices", type=int, default=10)
    parser.add_argument("--messages-per-second", type=float, default=100.0)
    parser.add_argument("--duration-seconds", type=float, default=60.0)
    parser.add_argument("--base-value", type=float, default=22.0)
    parser.add_argument("--amplitude", type=float, default=5.0)
    parser.add_argument("--client-id", default="ispf-mqtt-loadtest-publisher")
    args = parser.parse_args()

    host_port = args.broker.replace("tcp://", "").replace("ssl://", "")
    if ":" in host_port:
        host, port_s = host_port.rsplit(":", 1)
        port = int(port_s)
    else:
        host, port = host_port, 1883

    pad = max(5, len(str(args.devices)))
    topics = [mqtt_topic(i, pad) for i in range(1, args.devices + 1)]

    client = mqtt.Client(client_id=args.client_id, protocol=mqtt.MQTTv311)
    client.connect(host, port, keepalive=30)
    client.loop_start()

    interval = 1.0 / max(args.messages_per_second, 0.001)
    per_device_interval = interval * args.devices
    print(
        f"Publishing to {args.devices} topics under {TOPIC_PREFIX}/ "
        f"~{args.messages_per_second:.1f} msg/s for {args.duration_seconds:.0f}s "
        f"-> {host}:{port}"
    )

    start = time.perf_counter()
    sent = 0
    next_tick = start
    try:
        while time.perf_counter() - start < args.duration_seconds:
            now = time.perf_counter()
            if now < next_tick:
                time.sleep(min(0.001, next_tick - now))
                continue
            device_index = (sent % args.devices) + 1
            topic = topics[device_index - 1]
            phase = sent / max(args.devices, 1)
            value = args.base_value + args.amplitude * math.sin(phase * 0.15)
            payload = f"{value:.3f}"
            client.publish(topic, payload, qos=0, retain=False)
            sent += 1
            next_tick += interval
    finally:
        client.loop_stop()
        client.disconnect()

    elapsed = max(time.perf_counter() - start, 0.001)
    print(f"Done: {sent} messages in {elapsed:.1f}s ({sent / elapsed:.1f} msg/s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
