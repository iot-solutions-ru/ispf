#!/usr/bin/env python3
"""Publish to a single MQTT topic (historian lab / custom device)."""

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


def parse_broker(broker_url: str) -> tuple[str, int]:
    host_port = broker_url.replace("tcp://", "").replace("ssl://", "")
    if ":" in host_port:
        host, port_s = host_port.rsplit(":", 1)
        return host, int(port_s)
    return host_port, 1883


def main() -> int:
    parser = argparse.ArgumentParser(description="MQTT single-topic publisher")
    parser.add_argument("--broker", default="tcp://127.0.0.1:1883")
    parser.add_argument("--topic", required=True)
    parser.add_argument("--messages-per-second", type=float, default=100.0)
    parser.add_argument("--duration-seconds", type=float, default=60.0)
    parser.add_argument("--base-value", type=float, default=22.0)
    parser.add_argument("--amplitude", type=float, default=5.0)
    parser.add_argument("--client-id", default="ispf-mqtt-topic-publisher")
    args = parser.parse_args()

    host, port = parse_broker(args.broker)
    client = mqtt.Client(client_id=args.client_id, protocol=mqtt.MQTTv311)
    client.connect(host, port, keepalive=30)
    client.loop_start()

    interval = 1.0 / max(args.messages_per_second, 0.001)
    print(
        f"Publishing to {args.topic!r} ~{args.messages_per_second:.1f} msg/s "
        f"for {args.duration_seconds:.0f}s -> {host}:{port}"
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
            value = args.base_value + args.amplitude * math.sin(sent * 0.15)
            client.publish(args.topic, f"{value:.3f}", qos=0, retain=False)
            sent += 1
            next_tick += interval
    finally:
        client.loop_stop()
        client.disconnect()

    elapsed = time.perf_counter() - start
    print(f"Done: sent={sent} elapsed={elapsed:.1f}s rate={sent / max(elapsed, 0.001):.1f} msg/s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
