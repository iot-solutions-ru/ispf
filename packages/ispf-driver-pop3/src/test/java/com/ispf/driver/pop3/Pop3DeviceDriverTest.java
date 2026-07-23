package com.ispf.driver.pop3;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests against an embedded GreenMail POP3 server on an ephemeral port.
 */
class Pop3DeviceDriverTest {

    private static final String USER = "test@localhost";
    private static final String PASSWORD = "secret";

    private GreenMail greenMail;
    private int pop3Port;

    @BeforeEach
    void startMailServer() throws Exception {
        ServerSetup setup = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_POP3);
        greenMail = new GreenMail(setup);
        greenMail.start();
        pop3Port = greenMail.getPop3().getPort();
        GreenMailUser user = greenMail.setUser(USER, USER, PASSWORD);
        user.deliver(message("Alpha report", "body one"));
        user.deliver(message("Beta alert", "body two"));
    }

    @AfterEach
    void stopMailServer() {
        if (greenMail != null) {
            greenMail.stop();
            greenMail = null;
        }
    }

    @Test
    void connectsAndReadsStat() throws Exception {
        StubDriverObject driverObject = driverConfig();
        Pop3DeviceDriver driver = new Pop3DeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("mailbox", "stat"));

        DataRecord record = driverObject.variables.get("mailbox");
        int count = ((Number) record.firstRow().get("count")).intValue();
        long sizeBytes = ((Number) record.firstRow().get("sizeBytes")).longValue();
        assertEquals(2, count);
        assertTrue(sizeBytes > 0, "mailbox size should be positive, got " + sizeBytes);
        assertEquals("2 " + sizeBytes, record.firstRow().get("value"));

        driver.disconnect();
        assertFalse(driver.isConnected());
    }

    @Test
    void retrReadsFullMessage() throws Exception {
        StubDriverObject driverObject = driverConfig();
        Pop3DeviceDriver driver = new Pop3DeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of(
                "first", "retr:1",
                "second", "retr:2"
        ));

        // NOTE: Part.getInputStream() yields the decoded message content (body),
        // not the raw RFC822 stream — headers such as Subject are not included.
        DataRecord first = driverObject.variables.get("first");
        String firstBody = (String) first.firstRow().get("value");
        assertTrue(firstBody.contains("body one"), "message should contain its body: " + firstBody);
        assertEquals(1, ((Number) first.firstRow().get("count")).intValue());
        assertTrue(((Number) first.firstRow().get("sizeBytes")).longValue() > 0);

        DataRecord second = driverObject.variables.get("second");
        assertTrue(((String) second.firstRow().get("value")).contains("body two"));

        driver.disconnect();
    }

    @Test
    void retrOutOfRangeReturnsEmptyRecord() throws Exception {
        StubDriverObject driverObject = driverConfig();
        Pop3DeviceDriver driver = new Pop3DeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("missing", "retr:99"));

        DataRecord record = driverObject.variables.get("missing");
        assertEquals("", record.firstRow().get("value"));
        assertEquals(0, ((Number) record.firstRow().get("count")).intValue());
        assertEquals(0L, ((Number) record.firstRow().get("sizeBytes")).longValue());
        driver.disconnect();
    }

    @Test
    void readWithoutConnectFails() {
        Pop3DeviceDriver driver = new Pop3DeviceDriver();
        driver.initialize(driverConfig());

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("mailbox", "stat")));
        assertEquals("Not connected", error.getMessage());
    }

    @Test
    void connectToClosedPortFails() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "username", USER,
                "password", PASSWORD
        ));
        Pop3DeviceDriver driver = new Pop3DeviceDriver();
        driver.initialize(driverObject);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("POP3 connect failed"));
        assertFalse(driver.isConnected());
    }

    @Test
    void writeIsReadOnly() {
        Pop3DeviceDriver driver = new Pop3DeviceDriver();
        driver.initialize(driverConfig());

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("mailbox", DataRecord.single(
                        DataSchema.builder("pop3Mailbox")
                                .field("value", FieldType.STRING)
                                .field("count", FieldType.INTEGER)
                                .field("sizeBytes", FieldType.LONG)
                                .build(),
                        Map.of("value", "0 0", "count", 0, "sizeBytes", 0L)
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject driverConfig() {
        return new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(pop3Port),
                "username", USER,
                "password", PASSWORD
        ));
    }

    private static MimeMessage message(String subject, String body) throws Exception {
        // Session with mail.host avoids a reverse-DNS lookup in MimeMessage.saveChanges
        // (updateMessageID -> getCanonicalHostName), which can stall for seconds on
        // machines without a PTR record for the local address.
        Properties props = new Properties();
        props.setProperty("mail.host", "localhost");
        Session session = Session.getInstance(props);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("sender@localhost"));
        message.setRecipients(Message.RecipientType.TO, USER);
        message.setSubject(subject);
        message.setText(body);
        message.setSentDate(new Date());
        return message;
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
                    "test-pop3",
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
