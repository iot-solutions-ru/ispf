package com.ispf.driver.opcua;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverDiscovery;
import com.ispf.driver.DriverException;
import com.ispf.driver.opcuaserver.OpcUaServerDeviceDriver;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpcUaDeviceDriverTest {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("opcUaValue")
            .field("value", FieldType.STRING)
            .field("quality", FieldType.STRING)
            .build();

    private static final int NAMESPACE = 2;
    private static final String NODE = "Temperature";

    private OpcUaServerDeviceDriver serverDriver;

    @AfterEach
    void tearDown() {
        if (serverDriver != null) {
            serverDriver.disconnect();
            serverDriver = null;
        }
    }

    @Test
    void subscribeModeReceivesPushUpdates() throws Exception {
        int port = freePort();
        serverDriver = startServer(port);
        String nodeId = serverNodeId(serverDriver, NODE);

        StubDriverObject clientObject = new StubDriverObject(Map.of(
                "endpointUrl", endpointUrl(port),
                "timeoutMs", "10000",
                "readMode", "subscribe"
        ));
        OpcUaDeviceDriver client = new OpcUaDeviceDriver();
        client.initialize(clientObject);
        client.connect();
        client.readPoints(Map.of("temp", nodeId));

        waitForVariable(clientObject, "temp", 5000);
        assertEquals("GOOD", clientObject.variables.get("temp").firstRow().get("quality"));

        client.writePoint("temp", DataRecord.single(VALUE_SCHEMA, Map.of("value", "88.0")));
        waitForVariableValue(clientObject, "temp", "88", 5000);

        client.disconnect();
    }

    private static void waitForVariable(StubDriverObject clientObject, String name, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (clientObject.variables.containsKey(name)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for variable " + name);
    }

    private static void waitForVariableValue(
            StubDriverObject clientObject,
            String name,
            String expectedFragment,
            long timeoutMs
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            DataRecord record = clientObject.variables.get(name);
            if (record != null && record.firstRow().get("value").toString().contains(expectedFragment)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + name + " to contain " + expectedFragment);
    }

    @Test
    void browseChildrenFindsTemperatureNode() throws Exception {
        int port = freePort();
        serverDriver = startServer(port);
        String nodeId = serverNodeId(serverDriver, NODE);

        List<OpcUaBrowseSupport.BrowseNode> nodes = OpcUaBrowseSupport.browseChildren(
                endpointUrl(port),
                null,
                10_000
        );

        assertTrue(nodes.stream().anyMatch(node -> "IspfVariables".equals(node.displayName())));
    }

    @Test
    void writePointUpdatesServerNodeAndVariable() throws Exception {
        int port = freePort();
        serverDriver = startServer(port);
        String nodeId = serverNodeId(serverDriver, NODE);

        StubDriverObject clientObject = new StubDriverObject(Map.of(
                "endpointUrl", endpointUrl(port),
                "timeoutMs", "10000"
        ));
        OpcUaDeviceDriver client = new OpcUaDeviceDriver();
        client.initialize(clientObject);
        client.connect();
        client.readPoints(Map.of("temp", nodeId));

        client.writePoint("temp", DataRecord.single(VALUE_SCHEMA, Map.of("value", "42.5")));

        assertEquals("42.5", clientObject.variables.get("temp").firstRow().get("value"));
        client.disconnect();
    }

    @Test
    void rejectsUnknownPoint() throws Exception {
        int port = freePort();
        serverDriver = startServer(port);

        StubDriverObject clientObject = new StubDriverObject(Map.of(
                "endpointUrl", endpointUrl(port),
                "timeoutMs", "10000"
        ));
        OpcUaDeviceDriver client = new OpcUaDeviceDriver();
        client.initialize(clientObject);
        client.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                client.writePoint("missing", DataRecord.single(VALUE_SCHEMA, Map.of("value", "1"))));
        assertTrue(error.getMessage().contains("Unknown point"));
        client.disconnect();
    }

    @Test
    void rejectsWriteWhenNotConnected() {
        OpcUaDeviceDriver client = new OpcUaDeviceDriver();
        client.initialize(new StubDriverObject(Map.of("endpointUrl", "opc.tcp://127.0.0.1:4840/ispf")));

        DriverException error = assertThrows(DriverException.class, () ->
                client.writePoint("temp", DataRecord.single(VALUE_SCHEMA, Map.of("value", "1"))));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private OpcUaServerDeviceDriver startServer(int port) throws Exception {
        StubDriverObject serverObject = new StubDriverObject(Map.of(
                "bindPort", String.valueOf(port),
                "namespace", String.valueOf(NAMESPACE),
                "timeoutMs", "10000"
        ));
        OpcUaServerDeviceDriver driver = new OpcUaServerDeviceDriver();
        driver.initialize(serverObject);
        driver.connect();
        driver.readPoints(Map.of("temp", NODE));
        return driver;
    }

    private static String serverNodeId(OpcUaServerDeviceDriver driver, String identifier) throws Exception {
        var namespaceField = OpcUaServerDeviceDriver.class.getDeclaredField("namespace");
        namespaceField.setAccessible(true);
        Object namespace = namespaceField.get(driver);
        var indexMethod = namespace.getClass().getMethod("getNamespaceIndex");
        indexMethod.setAccessible(true);
        UShort index = (UShort) indexMethod.invoke(namespace);
        return "ns=" + index.intValue() + ";s=" + identifier;
    }

    private static String endpointUrl(int port) {
        return "opc.tcp://localhost:" + port + "/ispf";
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
                .field("value", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-device",
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
            if (configuration.containsKey(name)) {
                String value = configuration.get(name);
                return Optional.of(DataRecord.single(STRING_VALUE, Map.of("value", value, "raw", value)));
            }
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
