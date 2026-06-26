#!/usr/bin/env python3
"""
Publish sine-wave temperature telemetry for many MQTT meter sensors.

Each sensor id gets a phase offset so values spread along the sine curve.
Uses mosquitto_pub (same as manual smoke tests on VPS).

Example:
  python3 deploy/meter-sine-simulator.py
  python3 deploy/meter-sine-simulator.py --count 10000 --interval 2 --period 120
  python3 deploy/meter-sine-simulator.py --once
"""
from __future__ import annotations

import argparse
import json
import math
import shutil
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed


def sensor_id(prefix: str, index: int, width: int) -> str:
    return f"{prefix}{index:0{width}d}"


def temperature_at(
    *,
    base: float,
    amplitude: float,
    period_s: float,
    elapsed_s: float,
    sensor_index: int,
    sensor_count: int,
) -> float:
    phase_offset = 2.0 * math.pi * sensor_index / sensor_count
    angle = 2.0 * math.pi * elapsed_s / period_s + phase_offset
    return base + amplitude * math.sin(angle)


def publish(
    mosquitto_pub: str,
    host: str,
    topic: str,
    sensor: str,
    value: float,
    dry_run: bool,
) -> None:
    payload = json.dumps({"id": sensor, "temperature": f"{value:.2f}"}, separators=(",", ":"))
    cmd = [mosquitto_pub, "-h", host, "-t", topic, "-m", payload]
    if dry_run:
        print(" ".join(cmd))
        return
    subprocess.run(cmd, check=True, capture_output=True)


def publish_round(
    *,
    mosquitto_pub: str,
    host: str,
    topic: str,
    count: int,
    id_prefix: str,
    id_width: int,
    base: float,
    amplitude: float,
    period_s: float,
    elapsed_s: float,
    parallel: int,
    dry_run: bool,
) -> int:
    tasks = []
    for i in range(count):
        sensor = sensor_id(id_prefix, i + 1, id_width)
        value = temperature_at(
            base=base,
            amplitude=amplitude,
            period_s=period_s,
            elapsed_s=elapsed_s,
            sensor_index=i,
            sensor_count=count,
        )
        tasks.append((sensor, value))

    if parallel <= 1:
        for sensor, value in tasks:
            publish(mosquitto_pub, host, topic, sensor, value, dry_run)
        return len(tasks)

    errors = 0
    with ThreadPoolExecutor(max_workers=parallel) as pool:
        futures = [
            pool.submit(publish, mosquitto_pub, host, topic, sensor, value, dry_run)
            for sensor, value in tasks
        ]
        for future in as_completed(futures):
            try:
                future.result()
            except subprocess.CalledProcessError:
                errors += 1
    if errors:
        raise RuntimeError(f"{errors} mosquitto_pub calls failed")
    return len(tasks)


def default_id_width(count: int) -> int:
    return max(4, len(str(count)))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Simulate meter MQTT payloads with sine-wave temperatures."
    )
    parser.add_argument("-n", "--count", type=int, default=10000, help="number of sensors")
    parser.add_argument("-H", "--host", default="127.0.0.1", help="MQTT broker host")
    parser.add_argument("-t", "--topic", default="meter", help="MQTT topic")
    parser.add_argument("--base", type=float, default=22.0, help="mean temperature (C)")
    parser.add_argument("--amplitude", type=float, default=10.0, help="sine amplitude (C)")
    parser.add_argument(
        "--period",
        type=float,
        default=120.0,
        help="full sine cycle duration in seconds",
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=1.0,
        help="seconds between publishing rounds for all sensors",
    )
    parser.add_argument(
        "--rounds",
        type=int,
        default=0,
        help="number of rounds (0 = run until Ctrl+C)",
    )
    parser.add_argument("--once", action="store_true", help="publish one round and exit")
    parser.add_argument("--id-prefix", default="meter-", help="sensor id prefix")
    parser.add_argument("--id-width", type=int, default=0, help="zero-pad width (0 = auto from count)")
    parser.add_argument(
        "--parallel",
        type=int,
        default=50,
        help="concurrent mosquitto_pub processes (1 = serial)",
    )
    parser.add_argument("--dry-run", action="store_true", help="print commands, do not publish")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.count < 1:
        print("count must be >= 1", file=sys.stderr)
        return 2
    id_width = args.id_width if args.id_width > 0 else default_id_width(args.count)

    mosquitto_pub = shutil.which("mosquitto_pub")
    if not mosquitto_pub and not args.dry_run:
        print("mosquitto_pub not found in PATH", file=sys.stderr)
        return 1

    rounds = 1 if args.once else args.rounds
    started = time.monotonic()
    round_no = 0

    print(
        f"simulator: sensors={args.count} topic={args.topic} "
        f"base={args.base} amplitude={args.amplitude} period={args.period}s "
        f"interval={args.interval}s parallel={args.parallel}",
        flush=True,
    )

    try:
        while True:
            round_no += 1
            elapsed = time.monotonic() - started
            t0 = time.monotonic()
            published = publish_round(
                mosquitto_pub=mosquitto_pub or "mosquitto_pub",
                host=args.host,
                topic=args.topic,
                count=args.count,
                id_prefix=args.id_prefix,
                id_width=id_width,
                base=args.base,
                amplitude=args.amplitude,
                period_s=args.period,
                elapsed_s=elapsed,
                parallel=args.parallel,
                dry_run=args.dry_run,
            )
            dt = time.monotonic() - t0
            sample = temperature_at(
                base=args.base,
                amplitude=args.amplitude,
                period_s=args.period,
                elapsed_s=elapsed,
                sensor_index=0,
                sensor_count=args.count,
            )
            print(
                f"round {round_no}: published {published} messages in {dt:.2f}s "
                f"(sample {sensor_id(args.id_prefix, 1, id_width)}={sample:.2f} C)",
                flush=True,
            )

            if rounds and round_no >= rounds:
                break

            sleep_for = args.interval - dt
            if sleep_for > 0:
                time.sleep(sleep_for)
    except KeyboardInterrupt:
        print("\nstopped", flush=True)
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or b"").decode(errors="replace").strip()
        print(f"mosquitto_pub failed: {stderr or exc}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
