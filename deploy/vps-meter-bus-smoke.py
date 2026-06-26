#!/usr/bin/env python3
import json
import time
import urllib.request

API = "http://127.0.0.1:8080"
INSTANCE = "root.platform.instances.3123123123"


def api(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def main():
    token = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})["token"]
    try:
        import paho.mqtt.publish as pub

        pub.single("meter", json.dumps({"id": "3123123123", "temperature": "22"}), hostname="127.0.0.1")
        print("published to topic meter via paho")
    except Exception:
        import shutil
        import subprocess

        mosquitto = shutil.which("mosquitto_pub")
        if not mosquitto:
            print("publish failed: no paho and no mosquitto_pub")
            return 1
        payload = json.dumps({"id": "3123123123", "temperature": "22"})
        subprocess.run([mosquitto, "-h", "127.0.0.1", "-t", "meter", "-m", payload], check=True)
        print("published to topic meter via mosquitto_pub")

    time.sleep(4)
    variables = api("GET", f"/api/v1/objects/by-path/variables?path={INSTANCE}", token=token)
    temp = next((v for v in variables if v.get("name") == "temperature"), None)
    print("temperature variable:", json.dumps(temp, ensure_ascii=False))
    if not temp:
        return 1
    value = temp.get("value", {}).get("rows", [{}])[0].get("value")
    print("temperature.value =", value)
    return 0 if value == 22.0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
