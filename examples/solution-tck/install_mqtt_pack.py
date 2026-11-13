#!/usr/bin/env python3
import os
import shutil

PROD = "/home/iot-solutions/ispf-cluster/drivers/ispf-driver-mqtt"
DST = "/home/iot-solutions/ispf-tck/drivers/ispf-driver-mqtt"
NEW = "/tmp/ispf-driver-mqtt.jar"

print("prod", os.listdir(PROD))
os.makedirs(DST, exist_ok=True)
for name in os.listdir(PROD):
    source = os.path.join(PROD, name)
    target = os.path.join(DST, name)
    if os.path.isdir(source) or os.path.exists(target):
        continue
    try:
        os.link(source, target)
    except OSError:
        shutil.copy2(source, target)
jar_name = "ispf-driver-mqtt.jar"
for name in os.listdir(PROD):
    if name.endswith(".jar"):
        jar_name = name
target = os.path.join(DST, jar_name)
if os.path.lexists(target):
    os.remove(target)
shutil.copy2(NEW, target)
shutil.copy2("/tmp/ispf-server-0.9.208.jar", "/home/iot-solutions/ispf-tck/ispf-server.jar")
shutil.copy2("/tmp/ch_peak.py", "/home/iot-solutions/ispf-tck/ch_peak.py")
print("pack", jar_name, os.path.getsize(target))
print("server", os.path.getsize("/home/iot-solutions/ispf-tck/ispf-server.jar"))
