#!/usr/bin/env python3
"""Hardlink production driver packs into the TCK directory, leaving the TCK MQTT jar in place."""

import os

src = "/home/iot-solutions/ispf-cluster/drivers"
dst = "/home/iot-solutions/ispf-tck/drivers"
linked = 0
for name in os.listdir(src):
    if name == "ispf-driver-mqtt":
        continue
    source = os.path.join(src, name)
    target = os.path.join(dst, name)
    if os.path.isdir(source):
        for root, _dirs, files in os.walk(source):
            rel = os.path.relpath(root, source)
            os.makedirs(os.path.join(target, rel), exist_ok=True)
            for filename in files:
                origin = os.path.join(root, filename)
                dest = os.path.join(target, rel, filename)
                if os.path.exists(dest):
                    continue
                os.link(origin, dest)
                linked += 1
    elif not os.path.exists(target):
        os.link(source, target)
        linked += 1
print("linked", linked, "entries", len(os.listdir(dst)))
