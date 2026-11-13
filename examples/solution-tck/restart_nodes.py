#!/usr/bin/env python3
import subprocess
import time
import urllib.request

def restart(name, port):
    subprocess.run(["docker", "restart", name], check=True)
    deadline = time.time() + 180
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/api/v1/info", timeout=5) as response:
                if response.status == 200:
                    print(name, "up", flush=True)
                    return
        except Exception:
            time.sleep(2)
    raise SystemExit(name + " did not answer")

restart("ispf-tck-1", 8081)
time.sleep(5)
restart("ispf-tck-2", 8082)
