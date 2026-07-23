package com.ispf.driver.ldap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Entry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests against an UnboundID in-memory directory server
 * (shipped inside the unboundid-ldapsdk artifact the driver already uses).
 */
class LdapDeviceDriverTest {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("ldapValue")
            .field("value", FieldType.STRING)
            .field("count", FieldType.INTEGER)
            .build();

    private InMemoryDirectoryServer server;

    @BeforeEach
    void startServer() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(
                new DN("dc=example,dc=com"));
        config.setSchema(null);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig(
                "loopback", InetAddress.getByName("127.0.0.1"), 0, null));
        server = new InMemoryDirectoryServer(config);
        server.add(new Entry("dc=example,dc=com",
                new Attribute("objectClass", "top", "domain"),
                new Attribute("dc", "example")));
        server.add(new Entry("cn=admin,dc=example,dc=com",
                new Attribute("objectClass", "top", "person"),
                new Attribute("cn", "admin"),
                new Attribute("sn", "admin"),
                new Attribute("mail", "admin@example.com")));
        server.add(new Entry("uid=alice,dc=example,dc=com",
                new Attribute("objectClass", "top", "person"),
                new Attribute("uid", "alice"),
                new Attribute("cn", "Alice"),
                new Attribute("sn", "Anderson"),
                new Attribute("mail", "alice@example.com")));
        server.add(new Entry("uid=bob,dc=example,dc=com",
                new Attribute("objectClass", "top", "person"),
                new Attribute("uid", "bob"),
                new Attribute("cn", "Bob"),
                new Attribute("sn", "Brown")));
        server.startListening();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.shutDown(true);
            server = null;
        }
    }

    @Test
    void connectsAndReadsFilterCountAndAttributePoints() throws Exception {
        StubDriverObject driverObject = driverConfig();
        LdapDeviceDriver driver = new LdapDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "people", "(objectClass=person)",
                "adminMail", "cn=admin:mail"
        ));

        DataRecord people = driverObject.variables.get("people");
        assertEquals("3", people.firstRow().get("value"));
        assertEquals(3, ((Number) people.firstRow().get("count")).intValue());

        DataRecord adminMail = driverObject.variables.get("adminMail");
        assertEquals("admin@example.com", adminMail.firstRow().get("value"));
        assertEquals(1, ((Number) adminMail.firstRow().get("count")).intValue());

        driver.disconnect();
        assertFalse(driver.isConnected());
    }

    @Test
    void attributeReadWithoutMatchReturnsEmptyValueAndZeroCount() throws Exception {
        StubDriverObject driverObject = driverConfig();
        LdapDeviceDriver driver = new LdapDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("missingMail", "cn=missing:mail"));

        DataRecord record = driverObject.variables.get("missingMail");
        assertEquals("", record.firstRow().get("value"));
        assertEquals(0, ((Number) record.firstRow().get("count")).intValue());
        driver.disconnect();
    }

    @Test
    void reconnectsPerPoll() throws Exception {
        StubDriverObject driverObject = driverConfig();
        LdapDeviceDriver driver = new LdapDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        for (int i = 0; i < 2; i++) {
            driver.readPoints(Map.of("aliceMail", "uid=alice:mail"));
            DataRecord record = driverObject.variables.get("aliceMail");
            assertEquals("alice@example.com", record.firstRow().get("value"));
        }
        driver.disconnect();
    }

    @Test
    void readWithoutConnectFails() {
        LdapDeviceDriver driver = new LdapDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("people", "(objectClass=person)")));
        assertEquals("Not connected", error.getMessage());
    }

    @Test
    void connectToClosedPortFails() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        LdapDeviceDriver driver = new LdapDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort)
        )));

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("LDAP connect failed"));
        assertFalse(driver.isConnected());
    }

    @Test
    void writeIsReadOnly() {
        LdapDeviceDriver driver = new LdapDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("adminMail", DataRecord.single(VALUE_SCHEMA,
                        Map.of("value", "other@example.com", "count", 1))));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject driverConfig() {
        Map<String, String> configuration = new HashMap<>();
        configuration.put("host", "127.0.0.1");
        configuration.put("port", String.valueOf(server.getListenPort()));
        return new StubDriverObject(configuration);
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
                    "test-ldap",
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
