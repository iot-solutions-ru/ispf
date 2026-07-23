package com.ispf.driver.imap;

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
 * Loopback tests against an embedded GreenMail IMAP server on an ephemeral port.
 */
class ImapDeviceDriverTest {

    private static final String USER = "test@localhost";
    private static final String PASSWORD = "secret";

    private GreenMail greenMail;
    private int imapPort;

    @BeforeEach
    void startMailServer() throws Exception {
        ServerSetup setup = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP);
        greenMail = new GreenMail(setup);
        greenMail.start();
        imapPort = greenMail.getImap().getPort();
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
    void connectsAndReadsMailboxPoints() throws Exception {
        StubDriverObject driverObject = driverConfig();
        ImapDeviceDriver driver = new ImapDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "total", "messageCount",
                "unseen", "unseen",
                "firstSubject", "subject:1",
                "secondSubject", "subject:2"
        ));

        DataRecord total = driverObject.variables.get("total");
        assertEquals("2", total.firstRow().get("value"));
        assertEquals(2, ((Number) total.firstRow().get("count")).intValue());

        DataRecord unseen = driverObject.variables.get("unseen");
        assertEquals("2", unseen.firstRow().get("value"));
        assertEquals(2, ((Number) unseen.firstRow().get("count")).intValue());

        DataRecord firstSubject = driverObject.variables.get("firstSubject");
        assertEquals("Alpha report", firstSubject.firstRow().get("value"));
        assertEquals(1, ((Number) firstSubject.firstRow().get("count")).intValue());

        DataRecord secondSubject = driverObject.variables.get("secondSubject");
        assertEquals("Beta alert", secondSubject.firstRow().get("value"));

        driver.disconnect();
        assertFalse(driver.isConnected());
    }

    @Test
    void subjectOutOfRangeReturnsEmptyRecord() throws Exception {
        StubDriverObject driverObject = driverConfig();
        ImapDeviceDriver driver = new ImapDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("missing", "subject:99"));

        DataRecord record = driverObject.variables.get("missing");
        assertEquals("", record.firstRow().get("value"));
        assertEquals(0, ((Number) record.firstRow().get("count")).intValue());
        driver.disconnect();
    }

    @Test
    void readWithoutConnectFails() {
        ImapDeviceDriver driver = new ImapDeviceDriver();
        driver.initialize(driverConfig());

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("total", "messageCount")));
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
                "password", PASSWORD,
                "useSsl", "false"
        ));
        ImapDeviceDriver driver = new ImapDeviceDriver();
        driver.initialize(driverObject);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("IMAP connect failed"));
        assertFalse(driver.isConnected());
    }

    @Test
    void writeIsReadOnly() {
        ImapDeviceDriver driver = new ImapDeviceDriver();
        driver.initialize(driverConfig());

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("total", DataRecord.single(
                        DataSchema.builder("imapMailbox")
                                .field("value", FieldType.STRING)
                                .field("count", FieldType.INTEGER)
                                .build(),
                        Map.of("value", "0", "count", 0)
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject driverConfig() {
        return new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(imapPort),
                "username", USER,
                "password", PASSWORD,
                "folder", "INBOX",
                "useSsl", "false"
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
                    "test-imap",
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
