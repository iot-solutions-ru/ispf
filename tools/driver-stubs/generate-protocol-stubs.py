#!/usr/bin/env python3
"""Generate one ispf-driver-* pack per protocol stub (Apache-2.0, maturity STUB)."""
from __future__ import annotations

import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = Path(__file__).resolve().parent / "protocol-stubs.yaml"
PACKAGES = ROOT / "packages"
STUB_KIT = PACKAGES / "ispf-driver-stub-kit"
LEGACY_MULTI = PACKAGES / "ispf-driver-protocol-stubs"
NON_PACK_MODULES = {"ispf-driver-api", "ispf-driver-ddk", "ispf-driver-stub-kit"}


def to_class_prefix(driver_id: str) -> str:
    parts = re.split(r"[^a-zA-Z0-9]+", driver_id)
    return "".join(p[:1].upper() + p[1:] for p in parts if p)


def to_java_package_suffix(driver_id: str) -> str:
    return re.sub(r"[^a-zA-Z0-9]", "", driver_id).lower()


def java_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def load_stubs() -> list[dict]:
    text = CATALOG.read_text(encoding="utf-8")
    try:
        import yaml  # type: ignore

        return list(yaml.safe_load(text)["stubs"])
    except Exception:
        stubs: list[dict] = []
        current: dict | None = None
        for line in text.splitlines():
            if line.startswith("  - id:"):
                if current:
                    stubs.append(current)
                current = {"id": line.split(":", 1)[1].strip()}
            elif current is not None and line.startswith("    name:"):
                current["name"] = line.split(":", 1)[1].strip()
            elif current is not None and line.startswith("    description:"):
                current["description"] = line.split(":", 1)[1].strip()
            elif current is not None and line.startswith("    port:"):
                current["port"] = int(line.split(":", 1)[1].strip())
        if current:
            stubs.append(current)
        return stubs


def ensure_stub_kit() -> None:
    """Stub-kit sources are maintained in-tree (v0.2 lab loopback). Do not wipe them.

    Pack generation depends on `:packages:ispf-driver-stub-kit`. After regenerating
    packs, run `python3 tools/driver-stubs/raise-stub-readiness.py` for STUB_LAB tests.
    """
    if not (STUB_KIT / "src/main/java/com/ispf/driver/stubkit/ProtocolStubDeviceDriver.java").is_file():
        raise SystemExit(
            "Missing ispf-driver-stub-kit ProtocolStubDeviceDriver.java — "
            "restore packages/ispf-driver-stub-kit before generating packs"
        )
    (STUB_KIT / "build.gradle.kts").write_text(
        """dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
""",
        encoding="utf-8",
    )
    readme = STUB_KIT / "README.md"
    if not readme.is_file():
        readme.write_text(
            """# ISPF driver stub kit

Shared lab shell for generated protocol **STUB** packs (TCP probe + memory loopback write).

- Maturity stays **STUB** — not PRODUCTION / not a protocol codec
- Audit label after pack contract tests: **STUB_LAB**
- Not a driver pack (excluded from `ispf-driver-pack` plugin)
- License: Apache-2.0
- Generator: `python tools/driver-stubs/generate-protocol-stubs.py`
- Raise pack tests: `python tools/driver-stubs/raise-stub-readiness.py`
""",
            encoding="utf-8",
        )


def write_driver_pack(stub: dict) -> str:
    driver_id = stub["id"]
    module = f"ispf-driver-{driver_id}"
    if module in NON_PACK_MODULES:
        raise SystemExit(f"Refusing to overwrite reserved module {module}")
    class_prefix = to_class_prefix(driver_id)
    class_name = f"{class_prefix}DeviceDriver"
    pkg_suffix = to_java_package_suffix(driver_id)
    java_pkg = f"com.ispf.driver.{pkg_suffix}"
    out = PACKAGES / module

    if out.exists():
        shutil.rmtree(out)

    main = out / f"src/main/java/{java_pkg.replace('.', '/')}"
    main.mkdir(parents=True)

    (out / "build.gradle.kts").write_text(
        """dependencies {
    implementation(project(":packages:ispf-driver-stub-kit"))
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
""",
        encoding="utf-8",
    )

    (main / f"{class_name}.java").write_text(
        f"""package {java_pkg};

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * {java_string(stub["name"])} protocol stub ({driver_id}).
 * <p>
 * {java_string(stub["description"])}.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class {class_name} extends ProtocolStubDeviceDriver {{

    public {class_name}() {{
        super(
                "{driver_id}",
                "{java_string(stub["name"])} Driver",
                "{java_string(stub["description"])}",
                {int(stub["port"])}
        );
    }}
}}
""",
        encoding="utf-8",
    )
    return module


def main() -> None:
    stubs = load_stubs()
    if not stubs:
        raise SystemExit("No stubs loaded")
    ids = [s["id"] for s in stubs]
    if len(ids) != len(set(ids)):
        raise SystemExit("Duplicate stub ids")

    # Remove legacy multi-driver pack if present.
    if LEGACY_MULTI.exists():
        shutil.rmtree(LEGACY_MULTI)

    # Remove previously generated stub packs (idempotent regenerate).
    for path in PACKAGES.glob("ispf-driver-*"):
        if path.name in NON_PACK_MODULES:
            continue
        # Only delete packs that are known stub ids (avoid wiping real drivers).
        driver_id = path.name.removeprefix("ispf-driver-")
        if driver_id in ids:
            shutil.rmtree(path)

    ensure_stub_kit()
    modules = [write_driver_pack(stub) for stub in stubs]

    ids_resource = (
        ROOT
        / "packages"
        / "ispf-server"
        / "src"
        / "main"
        / "resources"
        / "driver-pack"
        / "protocol-stub-ids.json"
    )
    ids_resource.parent.mkdir(parents=True, exist_ok=True)
    ids_resource.write_text(
        json.dumps({"driverIds": ids, "licenseType": "Apache-2.0"}, indent=2) + "\n",
        encoding="utf-8",
    )

    sidecar = Path(__file__).resolve().parent / "generated-drivers.json"
    sidecar.write_text(
        json.dumps(
            {
                "licenseType": "Apache-2.0",
                "modules": modules,
                "drivers": [
                    {
                        "driverId": stub["id"],
                        "module": f"ispf-driver-{stub['id']}",
                        "licenseType": "Apache-2.0",
                    }
                    for stub in stubs
                ],
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    print(f"Generated {len(modules)} individual Apache-2.0 stub packs (stub-kit preserved)")
    print(f"Wrote {ids_resource}")
    print(f"Wrote {sidecar}")
    # Pack-level STUB_LAB contract tests (connect/read/write loopback).
    raise_script = Path(__file__).resolve().parent / "raise-stub-readiness.py"
    if raise_script.is_file():
        import subprocess

        rc = subprocess.call([sys.executable, str(raise_script)])
        if rc != 0:
            raise SystemExit(rc)


if __name__ == "__main__":
    main()
