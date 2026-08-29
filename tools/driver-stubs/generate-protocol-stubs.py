#!/usr/bin/env python3
"""Generate one ispf-driver-* pack per protocol stub (Apache-2.0, maturity STUB)."""
from __future__ import annotations

import json
import re
import shutil
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


def write_stub_kit() -> None:
    main = STUB_KIT / "src/main/java/com/ispf/driver/stubkit"
    test = STUB_KIT / "src/test/java/com/ispf/driver/stubkit"
    if STUB_KIT.exists():
        shutil.rmtree(STUB_KIT)
    main.mkdir(parents=True)
    test.mkdir(parents=True)

    (STUB_KIT / "build.gradle.kts").write_text(
        """dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
""",
        encoding="utf-8",
    )
    (STUB_KIT / "README.md").write_text(
        """# ISPF driver stub kit

Shared TCP-reachability base for generated protocol **STUB** packs.

- Not a driver pack (excluded from `ispf-driver-pack` plugin)
- License: Apache-2.0 (same as other in-tree protocol packs)
- Generator: `python tools/driver-stubs/generate-protocol-stubs.py`
""",
        encoding="utf-8",
    )

    (main / "ProtocolStubDeviceDriver.java").write_text(
        """package com.ispf.driver.stubkit;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared TCP reachability shell for protocol catalog stubs (maturity {@code STUB}).
 * <p>
 * Clean-room ISPF code, Apache-2.0 — no third-party protocol stacks.
 */
public abstract class ProtocolStubDeviceDriver implements DeviceDriver {

    protected static final DataSchema STUB_SCHEMA = DataSchema.builder("protocolStubResult")
            .field("connected", FieldType.BOOLEAN)
            .field("value", FieldType.STRING)
            .field("limitation", FieldType.STRING)
            .build();

    private final DriverMetadata metadata;
    private final String limitation;
    private final int defaultPort;

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port;
    private int timeoutMs = 5000;
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    protected ProtocolStubDeviceDriver(
            String driverId,
            String displayName,
            String description,
            int defaultPort
    ) {
        this.defaultPort = defaultPort;
        this.port = defaultPort;
        this.limitation = description + " — connectivity stub only (no protocol codec)";
        this.metadata = new DriverMetadata(
                driverId,
                displayName,
                "0.1.0",
                this.limitation,
                "ISPF",
                Map.of(
                        "host", "127.0.0.1",
                        "port", String.valueOf(defaultPort),
                        "timeoutMs", "5000",
                        "pollIntervalMs", "30000"
                ),
                DriverMaturity.STUB,
                Set.of("read")
        );
    }

    @Override
    public final DriverMetadata metadata() {
        return metadata;
    }

    @Override
    public final void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        this.host = "127.0.0.1";
        this.port = defaultPort;
        this.timeoutMs = 5000;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public final void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.WARNING, limitation);
        driverObject.log(
                DriverLogLevel.INFO,
                metadata.id() + " stub ready for " + host + ":" + port
        );
    }

    @Override
    public final void disconnect() {
        connected = false;
        points.clear();
    }

    @Override
    public final boolean isConnected() {
        return connected;
    }

    @Override
    public final void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        boolean reachable = tcpConnect(host, port);
        String value = reachable ? "endpoint-open" : "endpoint-closed";
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            points.put(entry.getKey(), entry.getValue() == null ? "connected" : entry.getValue());
            driverObject.updateVariable(entry.getKey(), DataRecord.single(STUB_SCHEMA, Map.of(
                    "connected", reachable,
                    "value", value,
                    "limitation", limitation
            )));
        }
    }

    @Override
    public final void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException(metadata.id() + " driver is read-only stub in v0.1");
    }

    private boolean tcpConnect(String targetHost, int targetPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, targetPort), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
""",
        encoding="utf-8",
    )

    (test / "ProtocolStubDeviceDriverTest.java").write_text(
        """package com.ispf.driver.stubkit;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolStubDeviceDriverTest {

    @Test
    void stubIsMarkedStubAndReadOnly() throws Exception {
        ProtocolStubDeviceDriver driver = new ProtocolStubDeviceDriver(
                "unit-test-stub",
                "Unit Test Stub Driver",
                "Unit test connectivity stub",
                1
        ) {};
        assertEquals("unit-test-stub", driver.metadata().id());
        assertEquals(DriverMaturity.STUB, driver.metadata().maturity());

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", "1",
                "timeoutMs", "200"
        ));
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("status", "connected"));
        assertEquals(false, driverObject.variables.get("status").firstRow().get("connected"));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("status", DataRecord.single(
                        DataSchema.builder("value").field("value", FieldType.STRING).build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
        driver.disconnect();
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-protocol-stub",
                    "root.platform.devices.test",
                    ObjectType.DEVICE,
                    "Test",
                    "",
                    null
            );
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
            variables.put(name, value);
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
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

    write_stub_kit()
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

    # Drop obsolete multi-driver sidecar semantics note in YAML header expectation.
    print(f"Generated stub-kit + {len(modules)} individual Apache-2.0 stub packs")
    print(f"Wrote {ids_resource}")
    print(f"Wrote {sidecar}")


if __name__ == "__main__":
    main()
