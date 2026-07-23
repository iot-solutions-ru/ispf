package com.ispf.driver.jmx;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests against a JDK JMXConnectorServer on the platform MBeanServer,
 * reached through a loopback RMI registry on an ephemeral port.
 */
class JmxDeviceDriverTest {

    private static final ObjectName LOOPBACK_NAME;

    static {
        try {
            LOOPBACK_NAME = new ObjectName("ispf.test:type=Loopback");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("jmxAttribute")
            .field("value", FieldType.STRING)
            .build();

    private Registry registry;
    private JMXConnectorServer connectorServer;
    private int registryPort;
    private String serviceUrl;

    public interface LoopbackMBean {
        String getMessage();

        int getCount();
    }

    public static final class Loopback implements LoopbackMBean {

        @Override
        public String getMessage() {
            return "ispf-jmx-loopback";
        }

        @Override
        public int getCount() {
            return 7;
        }
    }

    @BeforeAll
    static void forceLoopbackRmiHostname() {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
    }

    @BeforeEach
    void startConnectorServer() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            registryPort = socket.getLocalPort();
        }
        registry = LocateRegistry.createRegistry(registryPort, null,
                port -> new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1")));

        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        if (!mBeanServer.isRegistered(LOOPBACK_NAME)) {
            mBeanServer.registerMBean(new Loopback(), LOOPBACK_NAME);
        }

        serviceUrl = "service:jmx:rmi:///jndi/rmi://127.0.0.1:" + registryPort + "/jmxrmi";
        connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(
                new JMXServiceURL(serviceUrl), null, mBeanServer);
        connectorServer.start();
    }

    @AfterEach
    void stopConnectorServer() throws Exception {
        if (connectorServer != null) {
            connectorServer.stop();
            connectorServer = null;
        }
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        if (mBeanServer.isRegistered(LOOPBACK_NAME)) {
            mBeanServer.unregisterMBean(LOOPBACK_NAME);
        }
        if (registry != null) {
            UnicastRemoteObject.unexportObject(registry, true);
            registry = null;
        }
    }

    @Test
    void connectsViaServiceUrlAndReadsAttributes() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "serviceUrl", serviceUrl
        ));
        JmxDeviceDriver driver = new JmxDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "message", "ispf.test:type=Loopback::Message",
                "count", "ispf.test:type=Loopback::Count",
                "heapUsed", "java.lang:type=Memory::HeapMemoryUsage.used"
        ));

        assertEquals("ispf-jmx-loopback", driverObject.variables.get("message").firstRow().get("value"));
        assertEquals("7", driverObject.variables.get("count").firstRow().get("value"));
        long heapUsed = Long.parseLong(
                (String) driverObject.variables.get("heapUsed").firstRow().get("value"));
        assertTrue(heapUsed > 0, "HeapMemoryUsage.used must be positive, was " + heapUsed);

        driver.disconnect();
        assertFalse(driver.isConnected());
    }

    @Test
    void connectsViaHostPortWhenServiceUrlBlank() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(registryPort)
        ));
        JmxDeviceDriver driver = new JmxDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("message", "ispf.test:type=Loopback::Message"));
        assertEquals("ispf-jmx-loopback", driverObject.variables.get("message").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void readWithoutConnectFails() {
        JmxDeviceDriver driver = new JmxDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("message", "ispf.test:type=Loopback::Message")));
        assertEquals("Not connected", error.getMessage());
    }

    @Test
    void connectToClosedPortFails() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        JmxDeviceDriver driver = new JmxDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of(
                "serviceUrl", "service:jmx:rmi:///jndi/rmi://127.0.0.1:" + closedPort + "/jmxrmi"
        )));

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("JMX connect failed"));
        assertFalse(driver.isConnected());
    }

    @Test
    void writeIsReadOnly() {
        JmxDeviceDriver driver = new JmxDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("message", DataRecord.single(VALUE_SCHEMA,
                        Map.of("value", "x"))));
        assertTrue(error.getMessage().contains("read-only"));
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
                    "test-jmx",
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
