#!/usr/bin/env python3
"""Install the TCK jar and MQTT driver without changing the production driver directory."""

import os
import shutil

SRC = "/home/iot-solutions/ispf-cluster/drivers"
DST = "/home/iot-solutions/ispf-tck/drivers"
PACK = "/tmp/ispf-driver-mqtt.jar"
JAR = "/tmp/ispf-server-0.9.208.jar"


def main():
    os.makedirs(DST, exist_ok=True)
    names = sorted(os.listdir(SRC))
    mqtt = [name for name in names if "mqtt" in name.lower() and name.endswith(".jar")]
    print("mqtt packs", mqtt, flush=True)
    for name in names:
        target = os.path.join(DST, name)
        if os.path.exists(target):
            continue
        source = os.path.join(SRC, name)
        if os.path.isdir(source):
            continue
        try:
            os.link(source, target)
        except OSError:
            shutil.copy2(source, target)
    if not mqtt:
        raise SystemExit("no mqtt driver pack in the production drivers directory")
    for name in mqtt:
        target = os.path.join(DST, name)
        if os.path.lexists(target):
            os.remove(target)
        shutil.copy2(PACK, target)
        print("installed", name, os.path.getsize(target), flush=True)
    shutil.copy2(JAR, "/home/iot-solutions/ispf-tck/ispf-server.jar")
    print("server", os.path.getsize("/home/iot-solutions/ispf-tck/ispf-server.jar"), flush=True)


if __name__ == "__main__":
    main()
