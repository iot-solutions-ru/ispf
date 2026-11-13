#!/usr/bin/env python3
import subprocess

log = open("/tmp/gates-full.log", "w")
subprocess.Popen(
    ["python3", "-u", "/home/iot-solutions/ispf-tck/gates_on_lab.py"],
    stdout=log,
    stderr=subprocess.STDOUT,
    start_new_session=True,
)
print("started")
