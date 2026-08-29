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

MULTI_DRIVER_SIDECAR = ROOT / "tools" / "driver-stubs" / "generated-drivers.json"


def discover_single_driver_modules() -> dict[str, dict]:
    entries: dict[str, dict] = {}
    for path in (ROOT / "packages").rglob("*DeviceDriver.java"):
        if "ispf-driver-" not in str(path):
            continue
        if path.name == "ProtocolStubDeviceDriver.java":
            continue
        if "ispf-driver-ddk" in path.parts:
            continue
        if "ispf-driver-protocol-stubs" in path.parts:
            # Multi-driver pack handled via sidecar.
            continue
        text = path.read_text(encoding="utf-8")
        if re.search(r"abstract\s+class\s+\w+DeviceDriver", text):
            continue
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
    return entries


def load_multi_driver_sidecar() -> dict[str, dict]:
    if not MULTI_DRIVER_SIDECAR.is_file():
        return {}
    raw = json.loads(MULTI_DRIVER_SIDECAR.read_text(encoding="utf-8"))
    module = raw["module"]
    drivers = raw["drivers"]
    if not drivers:
        return {}
    first = drivers[0]
    return {
        module: {
            "driverClass": first["driverClass"],
            "driverId": first["driverId"],
            "jarFile": raw["jarFile"],
            "licenseType": raw.get("licenseType", "Apache-2.0"),
            "packId": raw["packId"],
            "drivers": drivers,
        }
    }


def main() -> None:
    entries = discover_single_driver_modules()
    entries.update(load_multi_driver_sidecar())
    out = ROOT / "gradle" / "driver-packs.json"
    out.write_text(json.dumps(dict(sorted(entries.items())), indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(entries)} packs to {out}")
    multi = entries.get("ispf-driver-protocol-stubs", {}).get("drivers")
    if multi:
        print(f"  protocol-stubs drivers: {len(multi)}")


if __name__ == "__main__":
    main()
