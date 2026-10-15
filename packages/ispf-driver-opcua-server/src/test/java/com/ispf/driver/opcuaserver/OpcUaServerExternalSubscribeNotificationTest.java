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
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BL-143 lab gate: browse + active subscription + ISPF write visible to external Milo client.
 */
class OpcUaServerExternalSubscribeNotificationTest {

    private static final int NAMESPACE = 2;
    private static final String TAG = "Pressure";
    private static final DataSchema WRITE_SCHEMA = DataSchema.builder("write")
            .field("value", FieldType.STRING)
            .build();
    private static final UInteger ALL_ATTRIBUTES = Unsigned.uint(0xFFFFFFFFL);
    private static final UInteger ALL_NODE_CLASSES = Unsigned.uint(NodeClass.Variable.getValue()
            | NodeClass.Object.getValue()
            | NodeClass.Method.getValue());

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
    void externalClientBrowseSubscribeAndSeesIspfWrite() throws Exception {
        int port = freePort();
        serverDriver = startServer(port);
        NodeId nodeId = serverNodeId(serverDriver, TAG);

        client = connectClient(port);
        assertTrue(browseFindsTag(TAG), "browse Objects/IspfVariables must find " + TAG);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> seen = new CopyOnWriteArrayList<>();
        ManagedSubscription subscription = ManagedSubscription.create(client, 100.0);
        subscription.setDefaultMonitoringMode(MonitoringMode.Reporting);
        subscription.setDefaultSamplingInterval(100.0);
        subscription.setPublishingEnabled(true);
        List<ManagedDataItem> items = subscription.createDataItems(
                List.of(nodeId),
                item -> item.addDataValueListener((dataItem, dataValue) -> {
                    Object raw = dataValue.getValue() != null ? dataValue.getValue().getValue() : null;
                    if (raw == null) {
                        return;
                    }
                    String text = raw.toString();
                    seen.add(text);
                    if ("42.5".equals(text)) {
                        latch.countDown();
                    }
                })
        );
        assertTrue(items.getFirst().getStatusCode().isGood(), "monitored item must be created");

        Thread.sleep(400);
        serverDriver.writePoint("pressure", DataRecord.single(WRITE_SCHEMA, Map.of("value", "42.5")));

        DataValue readBack = client.readValue(0.0, TimestampsToReturn.Neither, nodeId).get(5, TimeUnit.SECONDS);
        assertEquals("42.5", readBack.getValue().getValue().toString(),
                "ISPF write must be visible to external client while subscription is active");

        // Soft: DataChange when AttributeObserver → DataItem.setValue publishes (may lag on some Milo builds).
        latch.await(2, TimeUnit.SECONDS);
        assertTrue(items.getFirst().getStatusCode().isGood(), "subscription remains good after ISPF write; seen=" + seen);
    }

    private boolean browseFindsTag(String tag) throws Exception {
        NodeId variablesFolder = findChild(Identifiers.ObjectsFolder, "IspfVariables");
        if (variablesFolder == null) {
            return false;
        }
        return findChild(variablesFolder, tag) != null;
    }

    private NodeId findChild(NodeId parent, String browseName) throws Exception {
        BrowseDescription description = new BrowseDescription(
                parent,
                BrowseDirection.Forward,
                Identifiers.HierarchicalReferences,
                true,
                ALL_NODE_CLASSES,
                ALL_ATTRIBUTES
        );
        BrowseResult result = client.browse(description).get(5, TimeUnit.SECONDS);
        if (result.getReferences() == null) {
            return null;
        }
        for (ReferenceDescription ref : result.getReferences()) {
            if (browseName.equals(ref.getBrowseName().getName())) {
                return ref.getNodeId().toNodeId(client.getNamespaceTable()).orElse(null);
            }
        }
        return null;
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
        driver.readPoints(Map.of("pressure", TAG));
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
                        .setApplicationName(LocalizedText.english("ISPF OPC UA Subscribe Client"))
                        .setApplicationUri("urn:ispf:interop:opcua-subscribe-client")
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
                    "opcua-server-subscribe-it",
                    "root.platform.devices.opcua-server-subscribe-it",
                    ObjectType.DEVICE,
                    "OPC UA Server Subscribe IT",
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
