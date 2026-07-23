package com.ispf.driver.jms;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmsDeviceDriverTest {

    private BrokerService broker;
    private String brokerUrl;
    private JmsDeviceDriver driver;
    private StubDriverObject driverObject;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
            broker = null;
        }
    }

    @Test
    void consumeReadsSeededMessage() throws Exception {
        startBroker();
        seedQueue("test.consume.queue", "hello-jms-1");
        startDriver("test.consume.queue", "queue");

        driver.readPoints(Map.of("lastMessage", "consume"));

        DataRecord record = driverObject.variables.get("lastMessage");
        assertEquals("hello-jms-1", record.firstRow().get("value"));
        assertEquals(0, ((Number) record.firstRow().get("depth")).intValue());
    }

    @Test
    void consumeTimesOutOnEmptyQueue() throws Exception {
        startBroker();
        startDriver("test.empty.queue", "queue");

        driver.readPoints(Map.of("lastMessage", "consume"));

        DataRecord record = driverObject.variables.get("lastMessage");
        assertEquals("", record.firstRow().get("value"));
        assertEquals(0, ((Number) record.firstRow().get("depth")).intValue());
    }

    @Test
    void browseReportsQueueDepth() throws Exception {
        startBroker();
        seedQueue("test.browse.queue", "m1", "m2", "m3");
        startDriver("test.browse.queue", "queue");

        driver.readPoints(Map.of(
                "depth", "browse",
                "depthCapped", "browse:10"
        ));

        DataRecord depth = driverObject.variables.get("depth");
        assertEquals("3", depth.firstRow().get("value"));
        assertEquals(3, ((Number) depth.firstRow().get("depth")).intValue());

        // Capped browse with a limit above the actual depth reports the full depth.
        DataRecord capped = driverObject.variables.get("depthCapped");
        assertEquals("3", capped.firstRow().get("value"));
        assertEquals(3, ((Number) capped.firstRow().get("depth")).intValue());
    }

    @Test
    void browseWithLimitBelowDepthReportsTheLimit() throws Exception {
        startBroker();
        seedQueue("test.browse.capped.queue", "m1", "m2", "m3");
        startDriver("test.browse.capped.queue", "queue");

        driver.readPoints(Map.of(
                "depthTwo", "browse:2",
                "depthOne", "browse:1"
        ));

        DataRecord depthTwo = driverObject.variables.get("depthTwo");
        assertEquals("2", depthTwo.firstRow().get("value"));
        assertEquals(2, ((Number) depthTwo.firstRow().get("depth")).intValue());

        DataRecord depthOne = driverObject.variables.get("depthOne");
        assertEquals("1", depthOne.firstRow().get("value"));
        assertEquals(1, ((Number) depthOne.firstRow().get("depth")).intValue());
    }

    @Test
    void browseOnTopicDestinationIsRejected() throws Exception {
        startBroker();
        startDriver("test.topic", "topic");

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("depth", "browse")));
        assertTrue(error.getMessage().contains("only supported for queue destinations"));
    }

    @Test
    void writePointIsReadOnly() {
        driver = new JmsDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("lastMessage", null));
        assertTrue(error.getMessage().contains("read-only"));
    }

    @Test
    void readPointsRequiresConnection() {
        driver = new JmsDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("lastMessage", "consume")));
    }

    private void startBroker() throws Exception {
        String brokerName = "jms-driver-test-" + UUID.randomUUID();
        broker = new BrokerService();
        broker.setBrokerName(brokerName);
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.start();
        broker.waitUntilStarted();
        brokerUrl = "vm://" + brokerName + "?create=false";
    }

    private void seedQueue(String queueName, String... payloads) throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(queueName));
            for (String payload : payloads) {
                producer.send(session.createTextMessage(payload));
            }
            session.close();
        }
    }

    private void startDriver(String destination, String destinationType) throws DriverException {
        driverObject = new StubDriverObject(Map.of(
                "brokerUrl", brokerUrl,
                "destination", destination,
                "destinationType", destinationType,
                "timeoutMs", "1000"
        ));
        driver = new JmsDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());
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
                    "test-jms",
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
