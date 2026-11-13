#!/usr/bin/env python3
import os
import shutil
import subprocess

src = "/tmp/ispf-server-0.9.208.jar"
dst = "/home/iot-solutions/ispf-tck/ispf-server.jar"
print("src", os.path.exists(src), os.path.getsize(src) if os.path.exists(src) else 0)
os.makedirs("/home/iot-solutions/ispf-tck", exist_ok=True)
shutil.copyfile(src, dst)
print("dst", os.path.getsize(dst))
started = subprocess.run(["docker", "start", "ispf-tck-clickhouse"], text=True, capture_output=True)
print(started.stdout.strip() or started.stderr.strip()[-300:])
names = subprocess.run(["docker", "ps", "-a"], text=True, capture_output=True)
for line in names.stdout.splitlines():
    if "ispf-tck" in line or "ispf-enterprise-l" in line:
        print(line)
