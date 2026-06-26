#!/usr/bin/env python3
import json
import urllib.request

API = "http://127.0.0.1:8080"
BUS = "root.platform.devices.mqtt-meter-bus"
INSTANCE = "root.platform.instances.3123123123"


def api(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def safe(method, path, body=None, token=None):
    try:
        return api(method, path, body, token)
    except Exception as exc:
        return {"error": str(exc)}


def main():
    token = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})["token"]
    print("bus object:", safe("GET", f"/api/v1/objects/by-path?path={BUS}", token=token))
    print("driver:", safe("GET", f"/api/v1/drivers/runtime/status?devicePath={BUS}", token=token))
    print(
        "invoke:",
        safe(
            "POST",
            "/api/v1/objects/by-path/functions/invoke",
            {
                "path": BUS,
                "functionName": "ingestMeterPayload",
                "input": {"topic": "meter", "raw": '{"id":"3123123123","temperature":"22"}'},
            },
            token=token,
        ),
    )
    print("instance:", safe("GET", f"/api/v1/objects/by-path?path={INSTANCE}", token=token))
    vars_resp = safe("GET", f"/api/v1/objects/by-path/variables?path={INSTANCE}", token=token)
    if isinstance(vars_resp, list):
        for item in vars_resp:
            if item.get("name") == "temperature":
                print("temperature:", item)
    else:
        print("variables:", vars_resp)


if __name__ == "__main__":
    main()
