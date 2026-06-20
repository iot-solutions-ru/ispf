#!/usr/bin/env python3
"""Update snmp-localhost SNMP point mappings and restart driver."""
import json
import os
import sys
import time
import urllib.error
import urllib.request

API = os.environ.get("API", "http://127.0.0.1:8080")
PATH_DEVICE = os.environ.get("PATH_DEVICE", "root.platform.devices.snmp-localhost")
HEADERS = {"Content-Type": "application/json", "X-ISPF-Role": "admin"}

MAPPINGS = {
    "sysName": "1.3.6.1.2.1.1.5.0:STRING",
    "sysDescr": "1.3.6.1.2.1.1.1.0:STRING",
    "sysUpTime": "1.3.6.1.2.1.1.3.0",
    "sysLocation": "1.3.6.1.2.1.1.6.0:STRING",
    "sysContact": "1.3.6.1.2.1.1.4.0:STRING",
    "hrMemorySize": "1.3.6.1.2.1.25.2.2.0:INTEGER",
    "hrSystemProcesses": "1.3.6.1.2.1.25.1.6.0:INTEGER",
    "hrSystemNumUsers": "1.3.6.1.2.1.25.1.5.0:INTEGER",
    "ifNumber": "1.3.6.1.2.1.2.1.0:INTEGER",
    "ifInOctets": "1.3.6.1.2.1.2.2.1.10.2:INTEGER",
    "ifOutOctets": "1.3.6.1.2.1.2.2.1.16.2:INTEGER",
    "hrProcessorLoad": "1.3.6.1.2.1.25.3.3.1.2.196608:INTEGER:optional",
}


def call(method: str, path: str, body: dict | None = None) -> str:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(f"{API}{path}", data=data, method=method, headers=HEADERS)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.read().decode()
    except urllib.error.HTTPError as e:
        print(e.read().decode(), file=sys.stderr)
        raise


def main() -> None:
    print("=== Driver status (before) ===")
    try:
        print(call("GET", f"/api/v1/drivers/runtime/status?devicePath={PATH_DEVICE}"))
    except urllib.error.HTTPError:
        print("driver not bound")

    payload = {
        "schema": {"name": "driverPointMappingsJson", "fields": [{"name": "value", "type": "STRING"}]},
        "rows": [{"value": json.dumps(MAPPINGS)}],
    }
    print("=== Updating mappings ===")
    call(
        "PUT",
        f"/api/v1/objects/by-path/variables?path={PATH_DEVICE}&name=driverPointMappingsJson",
        payload,
    )
    print("OK")

    try:
        call("POST", f"/api/v1/drivers/runtime/stop?devicePath={PATH_DEVICE}")
    except urllib.error.HTTPError:
        pass
    time.sleep(2)
    print("=== Starting driver ===")
    print(call("POST", f"/api/v1/drivers/runtime/start?devicePath={PATH_DEVICE}"))
    time.sleep(8)
    print("=== Driver status (after) ===")
    print(call("GET", f"/api/v1/drivers/runtime/status?devicePath={PATH_DEVICE}"))


if __name__ == "__main__":
    main()
