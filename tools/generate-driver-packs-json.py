#!/usr/bin/env python3
"""Regenerate gradle/driver-packs.json from driver module sources."""
import json
import re
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

# Non-Apache pack licenses only (NIST SIP RI). All protocol packs are Apache-2.0 ISPF codecs.
LICENSE_TYPES = {
    "ispf-driver-sip": "LicenseRef-NIST-PublicDomain",
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
        driver_id = OVERRIDES.get(module, module.removeprefix("ispf-driver-"))
        jar_file = f"{module}.jar"
        license_type = LICENSE_TYPES.get(module, "Apache-2.0")
        entries[module] = {
            "driverClass": driver_class,
            "driverId": driver_id,
            "jarFile": jar_file,
            "licenseType": license_type,
            "packId": module,
        }

    out = ROOT / "gradle" / "driver-packs.json"
    out.write_text(json.dumps(dict(sorted(entries.items())), indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(entries)} packs to {out}")


if __name__ == "__main__":
    main()
