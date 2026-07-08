package com.ispf.driver.opcuaserver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedDataItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BL-143: external OPC UA client subscribe + write propagates back to ISPF variables.
 */
class OpcUaServerSubscriptionWriteBackIntegrationTest {

    private static final int NAMESPACE = 2;
    private static final String TAG = "Temperature";

    private OpcUaServerDeviceDriver serverDriver;
    private StubDriverObject serverObject;
    private OpcUaClient client;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.disconnect().get(5, TimeUnit.SECONDS);
            client = null;
        }
        if (serverDriver != null) {
            serverDriver.disconnect();
            serverDriver = null;
        }
        serverObject = null;
    }

    @Test
    void externalClientSubscribeWriteBacksToIspfVariable() throws Exception {
        int port = freePort();
        serverDriver = startServer(port);
        NodeId nodeId = serverNodeId(serverDriver, TAG);

        client = connectClient(port);
        ManagedSubscription subscription = ManagedSubscription.create(client);
        subscription.setDefaultMonitoringMode(MonitoringMode.Reporting);
        List<ManagedDataItem> items = subscription.createDataItems(List.of(nodeId));
        assertTrue(items.getFirst().getStatusCode().isGood(), "subscription item must be created");

        StatusCode writeStatus = client.writeValue(
                nodeId,
                new DataValue(new Variant("77.3"))
        ).get(5, TimeUnit.SECONDS);
        assertTrue(writeStatus.isGood(), "external write must succeed");

        waitForVariableValue(serverObject, "temperature", "77.3", 5000);

        DataValue readBack = client.readValue(0.0, TimestampsToReturn.Neither, nodeId).get(5, TimeUnit.SECONDS);
        assertEquals("77.3", readBack.getValue().getValue().toString(), "external read after write");
    }

    private OpcUaServerDeviceDriver startServer(int port) throws Exception {
        serverObject = new StubDriverObject(Map.of(
                "bindPort", String.valueOf(port),
                "namespace", String.valueOf(NAMESPACE),
                "timeoutMs", "10000"
        ));
        OpcUaServerDeviceDriver driver = new OpcUaServerDeviceDriver();
        driver.initialize(serverObject);
        driver.connect();
        driver.readPoints(Map.of("temperature", TAG));
        return driver;
    }

    private OpcUaClient connectClient(int port) throws Exception {
        String endpointUrl = OpcUaServerInterop.endpointUrl(port);
        OpcUaClient opcUaClient = OpcUaClient.create(
                endpointUrl,
                endpoints -> endpoints.stream()
                        .filter(endpoint -> SecurityPolicy.None.getUri().equals(endpoint.getSecurityPolicyUri()))
                        .findFirst(),
                configBuilder -> configBuilder
                        .setApplicationName(LocalizedText.english("ISPF OPC UA Interop Client"))
                        .setApplicationUri("urn:ispf:interop:opcua-client")
                        .build()
        );
        opcUaClient.connect().get(5, TimeUnit.SECONDS);
        return opcUaClient;
    }

    private static NodeId serverNodeId(OpcUaServerDeviceDriver driver, String identifier) throws Exception {
        var namespaceField = OpcUaServerDeviceDriver.class.getDeclaredField("namespace");
        namespaceField.setAccessible(true);
        Object namespace = namespaceField.get(driver);
        var indexMethod = namespace.getClass().getMethod("getNamespaceIndex");
        indexMethod.setAccessible(true);
        UShort index = (UShort) indexMethod.invoke(namespace);
        return new NodeId(index.intValue(), identifier);
    }

    private static void waitForVariableValue(
            StubDriverObject serverObject,
            String name,
            String expected,
            long timeoutMs
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            DataRecord record = serverObject.variables.get(name);
            if (record != null && expected.equals(record.firstRow().get("value"))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for ISPF variable " + name + " = " + expected);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new java.util.concurrent.ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "opcua-server-it",
                    "root.platform.devices.opcua-server-it",
                    ObjectType.DEVICE,
                    "OPC UA Server IT",
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
