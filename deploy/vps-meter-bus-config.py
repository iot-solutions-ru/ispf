#!/usr/bin/env python3
import json
import urllib.request

API = "http://127.0.0.1:8080"
BUS = "root.platform.devices.mqtt-meter-bus"


def api(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


token = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})["token"]
vars_list = api("GET", f"/api/v1/objects/by-path/variables?path={BUS}", token=token)
for name in ["driverConfigJson", "driverPointMappingsJson", "driverId", "driverStatus", "instanceParentPath", "instanceModelName"]:
    for item in vars_list:
        if item.get("name") == name:
            print(name, item.get("value", {}).get("rows"))

print("runtime", api("GET", f"/api/v1/drivers/runtime/status?devicePath={BUS}", token=token))
