package com.ispf.driver.opcuaserver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
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
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BL-143: scale test — external client subscribes to 50 monitored items.
 */
class OpcUaServerSubscriptionScaleTest {

    private static final int MONITORED_ITEMS = 50;
    private static final int NAMESPACE = 2;

    private static final DataSchema WRITE_SCHEMA = DataSchema.builder("write")
            .field("value", FieldType.STRING)
            .build();

    private OpcUaServerDeviceDriver serverDriver;
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
    }

    @Test
    void externalClientSubscribesToFiftyMonitoredItems() throws Exception {
        int port = freePort();
        Map<String, String> pointMappings = new LinkedHashMap<>();
        for (int index = 0; index < MONITORED_ITEMS; index++) {
            pointMappings.put("tag" + index, "Tag" + index);
        }

        serverDriver = startServer(port, pointMappings);
        List<NodeId> nodeIds = new ArrayList<>(MONITORED_ITEMS);
        for (int index = 0; index < MONITORED_ITEMS; index++) {
            nodeIds.add(serverNodeId(serverDriver, "Tag" + index));
        }

        client = connectClient(port);
        ManagedSubscription subscription = ManagedSubscription.create(client);
        subscription.setDefaultMonitoringMode(MonitoringMode.Reporting);
        List<ManagedDataItem> items = subscription.createDataItems(nodeIds);

        assertEquals(MONITORED_ITEMS, items.size(), "all monitored items must be created");
        for (ManagedDataItem item : items) {
            assertTrue(item.getStatusCode().isGood(), "monitored item status must be good");
        }

        for (int index = 0; index < MONITORED_ITEMS; index++) {
            serverDriver.writePoint(
                    "tag" + index,
                    DataRecord.single(WRITE_SCHEMA, Map.of("value", String.valueOf(index)))
            );
        }

        for (int index = 0; index < MONITORED_ITEMS; index++) {
            NodeId nodeId = nodeIds.get(index);
            DataValue readBack = client.readValue(0.0, TimestampsToReturn.Neither, nodeId).get(5, TimeUnit.SECONDS);
            assertEquals(String.valueOf(index), readBack.getValue().getValue().toString());
        }
    }

    private OpcUaServerDeviceDriver startServer(int port, Map<String, String> pointMappings) throws Exception {
        StubDriverObject serverObject = new StubDriverObject(Map.of(
                "bindPort", String.valueOf(port),
                "namespace", String.valueOf(NAMESPACE),
                "timeoutMs", "10000"
        ));
        OpcUaServerDeviceDriver driver = new OpcUaServerDeviceDriver();
        driver.initialize(serverObject);
        driver.connect();
        driver.readPoints(pointMappings);
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
                        .setApplicationName(LocalizedText.english("ISPF OPC UA Scale Client"))
                        .setApplicationUri("urn:ispf:interop:opcua-scale-client")
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

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "opcua-server-scale",
                    "root.platform.devices.opcua-server-scale",
                    ObjectType.DEVICE,
                    "OPC UA Server Scale",
                    "",
                    null
            );
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            return Optional.empty();
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
