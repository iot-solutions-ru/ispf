package com.ispf.driver.xmpp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackReader;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests against an in-test XMPP server speaking real XML streams over a ServerSocket:
 * stream open, SASL PLAIN, resource binding, roster IQ and XEP-0199 ping are answered with
 * real stanzas. No TLS, matching the driver's SecurityMode.disabled.
 */
class XmppDeviceDriverTest {

    private static final String DOMAIN = "example.com";
    private static final String USER = "tester";
    private static final String PASSWORD = "secret";

    @Test
    void presenceReportsOnlineAndRosterCountZero() throws Exception {
        try (FakeXmppServer server = new FakeXmppServer()) {
            StubDriverObject driverObject = driverConfig(server.port(), PASSWORD);
            XmppDeviceDriver driver = new XmppDeviceDriver();
            driver.initialize(driverObject);
            driver.connect();
            assertTrue(driver.isConnected());

            driver.readPoints(Map.of(
                    "presence", "presence",
                    "roster", "rosterCount"
            ));

            DataRecord presence = driverObject.variables.get("presence");
            assertEquals("online", presence.firstRow().get("value"));
            assertEquals(true, presence.firstRow().get("online"));
            assertEquals(0, ((Number) presence.firstRow().get("count")).intValue());

            DataRecord roster = driverObject.variables.get("roster");
            assertEquals("0", roster.firstRow().get("value"));
            assertEquals(true, roster.firstRow().get("online"));
            assertEquals(0, ((Number) roster.firstRow().get("count")).intValue());

            assertTrue(server.authCount() > 0, "server must have seen SASL PLAIN auth");
            assertTrue(server.bindCount() > 0, "server must have seen resource bind");
            assertTrue(server.pingCount() > 0, "server must have seen the XEP-0199 ping");

            driver.disconnect();
            assertFalse(driver.isConnected());
        }
    }

    @Test
    void connectWithoutPasswordSkipsLogin() throws Exception {
        try (FakeXmppServer server = new FakeXmppServer()) {
            StubDriverObject driverObject = driverConfig(server.port(), "");
            XmppDeviceDriver driver = new XmppDeviceDriver();
            driver.initialize(driverObject);
            driver.connect();
            assertTrue(driver.isConnected());
            assertEquals(0, server.authCount());
            driver.disconnect();
            assertFalse(driver.isConnected());
        }
    }

    @Test
    void connectToClosedPortFails() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StubDriverObject driverObject = driverConfig(closedPort, PASSWORD);
        XmppDeviceDriver driver = new XmppDeviceDriver();
        driver.initialize(driverObject);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("XMPP connect failed"));
        assertFalse(driver.isConnected());
    }

    @Test
    void readWithoutConnectFails() {
        XmppDeviceDriver driver = new XmppDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("presence", "presence")));
        assertEquals("Not connected", error.getMessage());
    }

    @Test
    void writeIsReadOnly() {
        XmppDeviceDriver driver = new XmppDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("presence", DataRecord.single(
                        DataSchema.builder("xmppResult")
                                .field("value", FieldType.STRING)
                                .field("online", FieldType.BOOLEAN)
                                .field("count", FieldType.INTEGER)
                                .build(),
                        Map.of("value", "online", "online", true, "count", 0)
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject driverConfig(int port, String password) {
        return new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "username", USER,
                "password", password,
                "domain", DOMAIN,
                "timeoutMs", "5000"
        ));
    }

    /**
     * Minimal XMPP server: answers stream open with stream features (SCRAM-SHA-1 first,
     * bind after auth), runs the real RFC 5802 SCRAM exchange, completes resource binding,
     * returns an empty roster and answers XEP-0199 pings. Unknown IQ gets get
     * service-unavailable, everything else is ignored.
     */
    private static final class FakeXmppServer implements AutoCloseable {

        private static final Pattern ID_PATTERN = Pattern.compile("id=['\"]([^'\"]*)['\"]");
        private static final Pattern RESOURCE_PATTERN = Pattern.compile("<resource>([^<]*)</resource>");
        private static final Pattern SASL_BODY_PATTERN = Pattern.compile(">([^<]*)</(?:auth|response)>");
        private static final byte[] SCRAM_SALT = "xmpp-test-salt16".getBytes(StandardCharsets.UTF_8);
        private static final int SCRAM_ITERATIONS = 4096;

        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
        private volatile int authCount;
        private volatile int bindCount;
        private volatile int pingCount;
        private volatile int rosterCount;

        FakeXmppServer() throws IOException {
            serverSocket = new ServerSocket(0);
            acceptThread = new Thread(this::acceptLoop, "xmpp-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int authCount() {
            return authCount;
        }

        int bindCount() {
            return bindCount;
        }

        int pingCount() {
            return pingCount;
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(15_000);
                    connections.add(socket);
                    Thread handler = new Thread(() -> serve(socket), "xmpp-session");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        throw new IllegalStateException("XMPP server accept failed", e);
                    }
                }
            }
        }

        private void serve(Socket socket) {
            try {
                StanzaReader reader = new StanzaReader(socket.getInputStream());                Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                boolean authenticated = false;
                String boundJid = null;
                String stanza;
                while ((stanza = reader.nextStanza()) != null) {
                    if (stanza.startsWith("</")) {
                        send(writer, "</stream:stream>");
                        return;
                    }
                    if (stanza.startsWith("<stream:stream")) {
                        send(writer, "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'"
                                + " xmlns='jabber:client' from='" + DOMAIN + "' id='is-"
                                + System.nanoTime() + "' version='1.0' xml:lang='en'>");
                        if (!authenticated) {
                            send(writer, "<stream:features>"
                                    + "<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>"
                                    + "<mechanism>SCRAM-SHA-1</mechanism></mechanisms></stream:features>");
                        } else {
                            send(writer, "<stream:features>"
                                    + "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>");
                        }
                    } else if (stanza.startsWith("<auth")) {
                        authenticated = scramAuth(stanza, reader, writer);
                        if (!authenticated) {
                            return;
                        }
                        authCount++;
                    } else if (stanza.startsWith("<iq")) {
                        String id = attribute(ID_PATTERN, stanza);
                        if (stanza.contains("urn:ietf:params:xml:ns:xmpp-bind")) {
                            bindCount++;
                            String resource = attribute(RESOURCE_PATTERN, stanza);
                            boundJid = USER + "@" + DOMAIN + "/" + (resource.isEmpty() ? "ispf" : resource);
                            send(writer, "<iq type='result' id='" + id + "'>"
                                    + "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><jid>" + boundJid
                                    + "</jid></bind></iq>");
                        } else if (stanza.contains("jabber:iq:roster")) {
                            rosterCount++;
                            send(writer, "<iq type='result' id='" + id + "'>"
                                    + "<query xmlns='jabber:iq:roster'/></iq>");
                        } else if (stanza.contains("urn:xmpp:ping")) {
                            pingCount++;
                            send(writer, "<iq type='result' id='" + id + "' from='" + DOMAIN + "'"
                                    + (boundJid != null ? " to='" + boundJid + "'" : "") + "/>");
                        } else {
                            send(writer, "<iq type='error' id='" + id + "'>"
                                    + "<error type='cancel'><service-unavailable"
                                    + " xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/></error></iq>");
                        }
                    }
                    // presence and other stanzas need no reply
                }
            } catch (IOException ignored) {
                // connection closed or reset by client
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }

        /**
         * RFC 5802 SCRAM-SHA-1 exchange against the test password (Smack 4.4 no longer
         * registers PLAIN). Verifies the client proof for real; sends the server-final
         * signature Smack checks.
         */
        private boolean scramAuth(String authStanza, StanzaReader reader, Writer writer) throws IOException {
            try {
                String clientFirst = new String(Base64.getDecoder().decode(saslBody(authStanza)),
                        StandardCharsets.UTF_8);
                int gs2End = clientFirst.indexOf(',', clientFirst.indexOf(',') + 1);
                String gs2Header = clientFirst.substring(0, gs2End + 1);
                String clientFirstBare = clientFirst.substring(gs2End + 1);
                String clientNonce = scramAttribute(clientFirstBare, "r");

                String serverNonce = Long.toHexString(System.nanoTime()) + "srv";
                String serverFirst = "r=" + clientNonce + serverNonce
                        + ",s=" + Base64.getEncoder().encodeToString(SCRAM_SALT)
                        + ",i=" + SCRAM_ITERATIONS;
                send(writer, "<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>"
                        + Base64.getEncoder().encodeToString(serverFirst.getBytes(StandardCharsets.UTF_8))
                        + "</challenge>");

                String responseStanza = reader.nextStanza();
                if (responseStanza == null || !responseStanza.startsWith("<response")) {
                    return false;
                }
                String clientFinal = new String(Base64.getDecoder().decode(saslBody(responseStanza)),
                        StandardCharsets.UTF_8);
                String channelBinding = scramAttribute(clientFinal, "c");
                if (!channelBinding.equals(Base64.getEncoder()
                        .encodeToString(gs2Header.getBytes(StandardCharsets.UTF_8)))) {
                    return false;
                }
                int proofIndex = clientFinal.lastIndexOf(",p=");
                String clientFinalWithoutProof = clientFinal.substring(0, proofIndex);
                byte[] clientProof = Base64.getDecoder().decode(clientFinal.substring(proofIndex + 3));

                String authMessage = clientFirstBare + "," + serverFirst + "," + clientFinalWithoutProof;
                byte[] saltedPassword = pbkdf2(PASSWORD, SCRAM_SALT, SCRAM_ITERATIONS);
                byte[] clientKey = hmacSha1(saltedPassword, "Client Key");
                byte[] storedKey = sha1(clientKey);
                byte[] clientSignature = hmacSha1(storedKey, authMessage);
                byte[] recoveredClientKey = new byte[clientProof.length];
                for (int i = 0; i < clientProof.length; i++) {
                    recoveredClientKey[i] = (byte) (clientProof[i] ^ clientSignature[i]);
                }
                if (!Arrays.equals(sha1(recoveredClientKey), storedKey)) {
                    send(writer, "<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>"
                            + "<not-authorized/></failure>");
                    return false;
                }
                byte[] serverKey = hmacSha1(saltedPassword, "Server Key");
                byte[] serverSignature = hmacSha1(serverKey, authMessage);
                String serverFinal = "v=" + Base64.getEncoder().encodeToString(serverSignature);
                send(writer, "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>"
                        + Base64.getEncoder().encodeToString(serverFinal.getBytes(StandardCharsets.UTF_8))
                        + "</success>");
                return true;
            } catch (Exception e) {
                System.err.println("SCRAM auth failed: " + e);
                return false;
            }
        }

        private static String saslBody(String stanza) {
            Matcher matcher = SASL_BODY_PATTERN.matcher(stanza);
            return matcher.find() ? matcher.group(1) : "";
        }

        private static String scramAttribute(String message, String name) {
            Matcher matcher = Pattern.compile("(?:^|,)" + name + "=([^,]*)").matcher(message);
            return matcher.find() ? matcher.group(1) : "";
        }

        private static byte[] pbkdf2(String password, byte[] salt, int iterations) throws Exception {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 160);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        }

        private static byte[] hmacSha1(byte[] key, String message) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        }

        private static byte[] sha1(byte[] input) throws Exception {
            return MessageDigest.getInstance("SHA-1").digest(input);
        }

        private static String attribute(Pattern pattern, String stanza) {
            Matcher matcher = pattern.matcher(stanza);
            return matcher.find() ? matcher.group(1) : "";
        }

        private static void send(Writer writer, String xml) throws IOException {
            synchronized (writer) {
                writer.write(xml);
                writer.flush();
            }
        }

        @Override
        public void close() throws IOException {
            for (Socket socket : connections) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
            serverSocket.close();
            try {
                acceptThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Depth-counting stanza tokenizer over the raw XML stream. The unclosed
     * {@code <stream:stream>} open tag and any closing tag at depth zero are
     * returned as standalone stanzas.
     */
    private static final class StanzaReader {

        private final PushbackReader in;

        StanzaReader(InputStream stream) {
            this.in = new PushbackReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        String nextStanza() throws IOException {
            StringBuilder stanza = new StringBuilder();
            int c;
            do {
                c = in.read();
                if (c == -1) {
                    return null;
                }
            } while (c != '<');
            stanza.append('<');
            int depth = 0;
            boolean inTag = true;
            boolean tagClosing = false;
            boolean prolog = false;
            boolean firstCharOfTag = true;
            char quote = 0;
            boolean prevWasSlash = false;
            while ((c = in.read()) != -1) {
                char ch = (char) c;
                stanza.append(ch);
                if (inTag) {
                    if (quote != 0) {
                        if (ch == quote) {
                            quote = 0;
                        }
                    } else if (ch == '\'' || ch == '"') {
                        quote = ch;
                    } else if (ch == '>') {
                        inTag = false;
                        if (prolog) {
                            prolog = false;
                        } else if (tagClosing) {
                            depth--;
                            if (depth <= 0) {
                                return stanza.toString();
                            }
                        } else if (prevWasSlash) {
                            if (depth == 0) {
                                return stanza.toString();
                            }
                        } else {
                            depth++;
                            if (depth == 1 && stanza.toString().startsWith("<stream:stream")) {
                                return stanza.toString();
                            }
                        }
                        firstCharOfTag = true;
                        tagClosing = false;
                        prevWasSlash = false;
                    } else {
                        if (firstCharOfTag && !Character.isWhitespace(ch)) {
                            tagClosing = ch == '/';
                            prolog = ch == '?';
                            firstCharOfTag = false;
                        }
                        prevWasSlash = ch == '/';
                    }
                } else if (ch == '<') {
                    inTag = true;
                    firstCharOfTag = true;
                    tagClosing = false;
                    prevWasSlash = false;
                }
            }
            return stanza.isEmpty() ? null : stanza.toString();
        }
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
                    "test-xmpp",
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
