#!/usr/bin/env python3
"""Raise protocol-stub pack readiness: add contract tests for every stub pack.

Does **not** promote maturity to PRODUCTION. Adds pack-level loopback contract
tests so audit can label stubs as STUB_LAB (lab-ready shells).

Usage:
  python3 tools/driver-stubs/raise-stub-readiness.py
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKAGES = ROOT / "packages"
STUB_JSON = (
    ROOT
    / "packages"
    / "ispf-server"
    / "src"
    / "main"
    / "resources"
    / "driver-pack"
    / "protocol-stub-ids.json"
)
PACKS_JSON = ROOT / "gradle" / "driver-packs.json"

BUILD_GRADLE = """dependencies {
    implementation(project(":packages:ispf-driver-stub-kit"))
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
"""


def to_class_prefix(driver_id: str) -> str:
    parts = re.split(r"[^a-zA-Z0-9]+", driver_id)
    return "".join(p[:1].upper() + p[1:] for p in parts if p)


def to_java_package_suffix(driver_id: str) -> str:
    return re.sub(r"[^a-zA-Z0-9]", "", driver_id).lower()


def contract_test_java(driver_id: str, class_name: str, java_pkg: str) -> str:
    return f"""package {java_pkg};

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pack contract for STUB_LAB readiness (TCP probe + memory loopback).
 * Does not certify a protocol codec or field deployment.
 */
class {class_name}ContractTest {{

    @Test
    void stubLabContractConnectReadWriteLoopback() throws Exception {{
        {class_name} driver = new {class_name}();
        assertEquals("{driver_id}", driver.metadata().id());
        assertEquals(DriverMaturity.STUB, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", "1",
                "timeoutMs", "200"
        ));
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.writePoint("lab", DataRecord.single(
                DataSchema.builder("value").field("value", FieldType.STRING).build(),
                Map.of("value", "stub-lab")
        ));
        driver.readPoints(Map.of("lab", "connected"));
        assertEquals("stub-lab", driverObject.variables.get("lab").firstRow().get("value"));
        assertEquals("loopback", driverObject.variables.get("lab").firstRow().get("mode"));

        driver.disconnect();
        assertTrue(!driver.isConnected());
    }}

    private static final class StubDriverObject implements DeviceDriver.DriverObject {{
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {{
            this.configuration = configuration;
        }}

        @Override
        public PlatformObject deviceObject() {{
            return new PlatformObject(
                    "test-{driver_id}",
                    "root.platform.devices.test",
                    ObjectType.DEVICE,
                    "Test",
                    "",
                    null
            );
        }}

        @Override
        public void updateVariable(String name, DataRecord value) {{
            variables.put(name, value);
        }}

        @Override
        public Optional<DataRecord> getVariable(String name) {{
            return Optional.ofNullable(variables.get(name));
        }}

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {{
        }}

        @Override
        public Map<String, String> configuration() {{
            return configuration;
        }}
    }}
}}
"""


def main() -> int:
    stub_ids = json.loads(STUB_JSON.read_text(encoding="utf-8"))["driverIds"]
    packs = json.loads(PACKS_JSON.read_text(encoding="utf-8"))
    by_driver = {meta["driverId"]: (pack_id, meta) for pack_id, meta in packs.items()}

    updated = 0
    missing = []
    for driver_id in stub_ids:
        if driver_id not in by_driver:
            missing.append(driver_id)
            continue
        pack_id, meta = by_driver[driver_id]
        pack_dir = PACKAGES / pack_id
        if not pack_dir.is_dir():
            missing.append(driver_id)
            continue

        class_name = meta["driverClass"].rsplit(".", 1)[-1]
        java_pkg = meta["driverClass"].rsplit(".", 1)[0]
        # Prefer derived names from id for consistency with generator
        expected_class = f"{to_class_prefix(driver_id)}DeviceDriver"
        if class_name != expected_class:
            # still use actual class from packs json
            pass

        (pack_dir / "build.gradle.kts").write_text(BUILD_GRADLE, encoding="utf-8")
        test_dir = pack_dir / "src/test/java" / Path(*java_pkg.split("."))
        test_dir.mkdir(parents=True, exist_ok=True)
        test_path = test_dir / f"{class_name}ContractTest.java"
        test_path.write_text(
            contract_test_java(driver_id, class_name, java_pkg),
            encoding="utf-8",
        )
        updated += 1

    print(f"Raised STUB_LAB contract tests for {updated}/{len(stub_ids)} stub packs")
    if missing:
        print("Missing packs:", ", ".join(missing), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
