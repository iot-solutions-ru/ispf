#!/usr/bin/env python3
"""Regenerate gradle/driver-packs.json from driver module sources."""
import json
import re
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

OVERRIDES = {
    "ispf-driver-modbus": "modbus-tcp",
    "ispf-driver-modbus-rtu": "modbus-rtu",
    "ispf-driver-modbus-udp": "modbus-udp",
    "ispf-driver-opcua-server": "opcua-server",
    "ispf-driver-iec104-server": "iec104-server",
    "ispf-driver-ethernet-ip": "ethernet-ip",
    "ispf-driver-gps-tracker": "gps-tracker",
    "ispf-driver-message-stream": "message-stream",
    "ispf-driver-http-server": "http-server",
    "ispf-driver-graph-db": "graph-db",
    "ispf-driver-ip-host": "ip-host",
    "ispf-driver-opc-da": "opc-da",
    "ispf-driver-opc-bridge": "opc-bridge",
    "ispf-driver-web-transaction": "web-transaction",
    "ispf-driver-omron-fins": "omron-fins",
    "ispf-driver-modem-at": "modem-at",
    "ispf-driver-smis": "smi-s",
}

LICENSE_TYPES = {
    "ispf-driver-bacnet": "GPL-3.0-only",
    "ispf-driver-dlms": "GPL-2.0-only",
    "ispf-driver-iec104": "GPL-3.0-or-later",
    "ispf-driver-iec104-server": "GPL-3.0-or-later",
    "ispf-driver-radius": "LGPL-3.0-or-later",
    "ispf-driver-mbus": "MPL-2.0",
}


def main() -> None:
    entries: dict[str, dict[str, str]] = {}
    for path in (ROOT / "packages").rglob("*DeviceDriver.java"):
        if "ispf-driver-" not in str(path):
            continue
        text = path.read_text(encoding="utf-8")
        pkg = re.search(r"^package\s+([\w.]+);", text, re.M)
        cls = re.search(r"^public class (\w+DeviceDriver)", text, re.M)
        if not pkg or not cls:
            continue
        module = path.parts[path.parts.index("packages") + 1]
        driver_class = f"{pkg.group(1)}.{cls.group(1)}"
        driver_id = OVERRIDES.get(module, module.replace("ispf-driver-", ""))
        entries[module] = {
            "packId": module,
            "driverId": driver_id,
            "driverClass": driver_class,
            "licenseType": LICENSE_TYPES.get(module, "Apache-2.0"),
            "jarFile": f"{module}.jar",
        }

    out = ROOT / "gradle" / "driver-packs.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(entries, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote {len(entries)} entries to {out}")


if __name__ == "__main__":
    main()
