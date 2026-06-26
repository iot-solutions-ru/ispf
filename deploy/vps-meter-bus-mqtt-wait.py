#!/usr/bin/env python3
import json
import subprocess
import time
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


def get_vars(path, token):
    return api("GET", f"/api/v1/objects/by-path/variables?path={path}", token=token)


def pick(vars_list, name):
    for item in vars_list:
        if item.get("name") == name:
            return item
    return None


def main():
    token = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})["token"]
    subprocess.run(
        ["mosquitto_pub", "-h", "127.0.0.1", "-t", "meter", "-m", '{"id":"3123123123","temperature":"22"}'],
        check=True,
    )
    for wait in (2, 5, 8):
        time.sleep(wait)
        bus_vars = get_vars(BUS, token)
        last_ingress = pick(bus_vars, "lastIngress")
        ingest_status = pick(bus_vars, "ingestStatus")
        print(f"after {wait}s lastIngress:", json.dumps(last_ingress, ensure_ascii=False))
        print(f"after {wait}s ingestStatus:", json.dumps(ingest_status, ensure_ascii=False))
        try:
            inst_vars = get_vars(INSTANCE, token)
            temp = pick(inst_vars, "temperature")
            print(f"after {wait}s temperature:", json.dumps(temp, ensure_ascii=False))
            if temp:
                return
        except Exception as exc:
            print(f"after {wait}s instance missing:", exc)


if __name__ == "__main__":
    main()
