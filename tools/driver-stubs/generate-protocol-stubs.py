#!/usr/bin/env python3
"""Generate packages/ispf-driver-protocol-stubs from protocol-stubs.yaml."""
from __future__ import annotations

import re
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None

ROOT = Path(__file__).resolve().parents[2]
CATALOG = Path(__file__).resolve().parent / "protocol-stubs.yaml"
OUT = ROOT / "packages" / "ispf-driver-protocol-stubs"
PKG = "com.ispf.driver.protocolstubs"


def to_class_prefix(driver_id: str) -> str:
    parts = re.split(r"[^a-zA-Z0-9]+", driver_id)
    return "".join(p[:1].upper() + p[1:] for p in parts if p)


def load_stubs() -> list[dict]:
    text = CATALOG.read_text(encoding="utf-8")
    if yaml is not None:
        data = yaml.safe_load(text)
        return list(data["stubs"])
    # Minimal fallback parser for this file's shape (no PyYAML in some CI images).
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


def java_string(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\n", "\\n")
    )


def write_base(out_main: Path) -> None:
    content = '''package PKG_PLACEHOLDER;

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
'''.replace("PKG_PLACEHOLDER", PKG)
    (out_main / "ProtocolStubDeviceDriver.java").write_text(content, encoding="utf-8")


def write_driver(out_main: Path, stub: dict) -> str:
    prefix = to_class_prefix(stub["id"])
    class_name = f"{prefix}DeviceDriver"
    path = out_main / f"{class_name}.java"
    path.write_text(
        f'''package {PKG};

/**
 * {java_string(stub["name"])} protocol stub ({stub["id"]}).
 * <p>
 * {java_string(stub["description"])}.
 */
public class {class_name} extends ProtocolStubDeviceDriver {{

    public {class_name}() {{
        super(
                "{stub["id"]}",
                "{java_string(stub["name"])} Driver",
                "{java_string(stub["description"])}",
                {int(stub["port"])}
        );
    }}
}}
''',
        encoding="utf-8",
    )
    return f"{PKG}.{class_name}"


def write_catalog_java(out_main: Path, stubs: list[dict]) -> None:
    lines = [
        f"package {PKG};",
        "",
        "import java.util.List;",
        "",
        "/** Generated catalog of protocol stub driver ids (see tools/driver-stubs/). */",
        "public final class ProtocolStubCatalog {",
        "",
        "    public static final List<String> DRIVER_IDS = List.of(",
    ]
    for i, stub in enumerate(stubs):
        comma = "," if i < len(stubs) - 1 else ""
        lines.append(f'            "{stub["id"]}"{comma}')
    lines += [
        "    );",
        "",
        "    private ProtocolStubCatalog() {",
        "    }",
        "}",
        "",
    ]
    (out_main / "ProtocolStubCatalog.java").write_text("\n".join(lines), encoding="utf-8")


def write_test(out_test: Path, first_class: str, first_id: str) -> None:
    (out_test / "ProtocolStubDeviceDriverTest.java").write_text(
        f'''package {PKG};

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

class ProtocolStubDeviceDriverTest {{

    @Test
    void catalogIsNonEmptyAndUnique() {{
        assertTrue(ProtocolStubCatalog.DRIVER_IDS.size() > 40);
        assertEquals(
                ProtocolStubCatalog.DRIVER_IDS.size(),
                ProtocolStubCatalog.DRIVER_IDS.stream().distinct().count()
        );
    }}

    @Test
    void sampleStubIsMarkedStubAndReadOnly() throws Exception {{
        {first_class} driver = new {first_class}();
        assertEquals("{first_id}", driver.metadata().id());
        assertEquals(DriverMaturity.STUB, driver.metadata().maturity());

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", "1",
                "timeoutMs", "200"
        ));
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("status", "connected"));
        DataRecord record = driverObject.variables.get("status");
        assertEquals(false, record.firstRow().get("connected"));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("status", DataRecord.single(
                        DataSchema.builder("value").field("value", FieldType.STRING).build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
        driver.disconnect();
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
                    "test-protocol-stub",
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
''',
        encoding="utf-8",
    )


def write_build_gradle(out_root: Path) -> None:
    (out_root / "build.gradle.kts").write_text(
        '''dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
''',
        encoding="utf-8",
    )


def main() -> None:
    stubs = load_stubs()
    if not stubs:
        raise SystemExit("No stubs loaded from " + str(CATALOG))
    ids = [s["id"] for s in stubs]
    if len(ids) != len(set(ids)):
        raise SystemExit("Duplicate stub ids in catalog")

    out_main = OUT / "src" / "main" / "java" / "com" / "ispf" / "driver" / "protocolstubs"
    out_test = OUT / "src" / "test" / "java" / "com" / "ispf" / "driver" / "protocolstubs"
    if OUT.exists():
        for path in OUT.rglob("*.java"):
            path.unlink()
    out_main.mkdir(parents=True, exist_ok=True)
    out_test.mkdir(parents=True, exist_ok=True)

    write_build_gradle(OUT)
    write_base(out_main)
    write_catalog_java(out_main, stubs)
    classes: list[tuple[str, str]] = []
    for stub in stubs:
        fqcn = write_driver(out_main, stub)
        classes.append((stub["id"], fqcn))
    first_id, first_fqcn = classes[0]
    first_simple = first_fqcn.rsplit(".", 1)[-1]
    write_test(out_test, first_simple, first_id)

    manifest_drivers = [{"driverId": driver_id, "driverClass": fqcn} for driver_id, fqcn in classes]
    # Side-car for generate-driver-packs-json / docs (committed).
    import json

    sidecar = ROOT / "tools" / "driver-stubs" / "generated-drivers.json"
    sidecar.write_text(
        json.dumps(
            {
                "module": "ispf-driver-protocol-stubs",
                "packId": "ispf-driver-protocol-stubs",
                "licenseType": "Apache-2.0",
                "jarFile": "ispf-driver-protocol-stubs.jar",
                "drivers": manifest_drivers,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
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
        json.dumps({"driverIds": [stub["id"] for stub in stubs]}, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Generated {len(stubs)} protocol stubs into {OUT}")
    print(f"Wrote sidecar {sidecar}")
    print(f"Wrote maturity ids {ids_resource}")


if __name__ == "__main__":
    main()
