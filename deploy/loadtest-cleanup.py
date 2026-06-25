#!/usr/bin/env python3
"""Stop / purge loadtest devices and alert rules before a clean measurement run."""

from __future__ import annotations

import argparse
import sys

from loadtest_cleanup_lib import cleanup_loadtest_environment


class Client:
    def __init__(self, base_url: str, host_header: str | None, timeout: float):
        self.base = base_url.rstrip("/")
        self.timeout = timeout
        self.session = __import__("requests").Session()
        self.session.headers.update({"Accept": "application/json"})
        if host_header:
            self.session.headers["Host"] = host_header

    def login(self, username: str, password: str) -> None:
        r = self.session.post(
            f"{self.base}/api/v1/auth/login",
            json={"username": username, "password": password},
            timeout=self.timeout,
        )
        r.raise_for_status()
        self.session.headers["Authorization"] = f"Bearer {r.json()['token']}"

    def request(self, method: str, path: str, **kwargs):
        kwargs.setdefault("timeout", self.timeout)
        return self.session.request(method, f"{self.base}{path}", **kwargs)


def main() -> int:
    parser = argparse.ArgumentParser(description="Clean loadtest devices and alert rules on ISPF")
    parser.add_argument("--base-url", default="https://ispf.iot-solutions.ru")
    parser.add_argument("--host-header", default="")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--timeout", type=float, default=60.0)
    parser.add_argument("--purge-virtual", action="store_true")
    parser.add_argument("--purge-mqtt", action="store_true")
    parser.add_argument("--keep-alerts", action="store_true")
    parser.add_argument(
        "--keep-background",
        action="store_true",
        help="Do not stop demo/lab drivers or disable non-loadtest automation",
    )
    args = parser.parse_args()

    client = Client(args.base_url, args.host_header or None, args.timeout)
    client.login(args.username, args.password)

    stats = cleanup_loadtest_environment(
        client,
        stop_virtual=True,
        stop_mqtt=True,
        purge_virtual=args.purge_virtual,
        purge_mqtt=args.purge_mqtt,
        delete_alerts=not args.keep_alerts,
        silence_background=not args.keep_background,
    )
    print(
        "Cleanup done: "
        f"background drivers stopped={stats['backgroundDriversStopped']}, "
        f"alerts disabled={stats['alertsDisabled']}, "
        f"correlators disabled={stats['correlatorsDisabled']}, "
        f"schedules disabled={stats['schedulesDisabled']}, "
        f"virtual stopped={stats['virtualStopped']}/{stats['virtualDevices']}, "
        f"mqtt stopped={stats['mqttStopped']}/{stats['mqttDevices']}, "
        f"loadtest alerts removed={stats['alertsRemoved']}, "
        f"purged virtual={stats['virtualPurged']}, purged mqtt={stats['mqttPurged']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
