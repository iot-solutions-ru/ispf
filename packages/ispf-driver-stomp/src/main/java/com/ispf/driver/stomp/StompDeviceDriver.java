package com.ispf.driver.stomp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverErrorKind;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverReconnectPendingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * STOMP 1.2 client over TCP or WebSocket.
 * <p>
 * A point mapping is a destination, optionally with a JSON selector after {@code |}:
 * {@code /topic/temp}, {@code /topic/temp|@raw}, {@code /topic/temp|$.value},
 * {@code /topic/temp|round:2:$.value}. {@code @state} is the open-session marker.
 * {@code login} and {@code passcode} are the standard CONNECT headers.
 * {@code authUrl}, when set, posts those credentials. The returned token is sent
 * on the WebSocket handshake as {@code Authorization: Bearer} and on the STOMP
 * CONNECT frame as {@code Authorization} with the raw token. Brokers such as
 * AggreGate use that CONNECT header to open the subscription session.
 * {@code connectHeaders} is a JSON object of extra CONNECT headers, for example
 * {@code {"host":"devices"}}. A rejected login is a configuration error
 * and is not retried. Other connect failures wait from 5 seconds to 60 seconds.
 * Write sends the record text as a SEND body to that destination.
 */
public class StompDeviceDriver implements DeviceDriver {

    private static final long[] RETRY_DELAYS_MS = {5_000L, 10_000L, 20_000L, 40_000L, 60_000L};
    private static final int DRAIN_LIMIT = 64;
    private static final int DRAIN_FOLLOW_MS = 50;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("stompValue")
            .field("value", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "stomp",
            "STOMP Driver",
            "0.1.0",
            "STOMP 1.2 over TCP or WebSocket. A point is destination or destination|JSON path",
            "ISPF",
            Map.ofEntries(
                    Map.entry("transport", "websocket"),
                    Map.entry("host", "127.0.0.1"),
                    Map.entry("port", "8080"),
                    Map.entry("path", "/"),
                    Map.entry("useTls", "false"),
                    Map.entry("caCertPath", ""),
                    Map.entry("login", ""),
                    Map.entry("passcode", ""),
                    Map.entry("authUrl", ""),
                    Map.entry("connectHeaders", ""),
                    Map.entry("vhost", ""),
                    Map.entry("acceptVersion", "1.2"),
                    Map.entry("timeoutMs", "5000")
            ),
            DriverMaturity.PRODUCTION,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String transport = "websocket";
    private String host = "127.0.0.1";
    private int port = 8080;
    private String path = "/";
    private boolean useTls;
    private String caCertPath = "";
    private String login = "";
    private String passcode = "";
    private String authUrl = "";
    private String connectHeaders = "";
    private String vhost = "";
    private String acceptVersion = "1.2";
    private int timeoutMs = 5000;
    private DriverErrorKind lastConnectFailure;
    private int retryAttempt;
    private long nextConnectAttemptAt;
    LongSupplier clock = System::currentTimeMillis;
    private Link link;
    private final Map<String, String> subscriptionIds = new LinkedHashMap<>();
    private final Map<String, PointBinding> points = new LinkedHashMap<>();
    private int nextSubscription;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null) {
            return;
        }
        switch (key) {
            case "transport" -> transport = value.trim().toLowerCase(Locale.ROOT);
            case "host" -> host = value.trim();
            case "port" -> {
                if (!value.isBlank()) {
                    port = Integer.parseInt(value.trim());
                }
            }
            case "path" -> path = value.trim();
            case "useTls" -> useTls = Boolean.parseBoolean(value.trim());
            case "caCertPath" -> caCertPath = value.trim();
            case "login" -> login = value.trim();
            case "passcode" -> passcode = value;
            case "authUrl" -> authUrl = value.trim();
            case "connectHeaders" -> connectHeaders = value.trim();
            case "vhost" -> vhost = value.trim();
            case "acceptVersion" -> {
                if (!value.isBlank()) {
                    acceptVersion = value.trim();
                }
            }
            case "timeoutMs" -> {
                if (!value.isBlank()) {
                    timeoutMs = Integer.parseInt(value.trim());
                }
            }
            default -> { }
        }
    }

    @Override
    public boolean retriesConnectWhilePolling() {
        return true;
    }

    @Override
    public void connect() throws DriverException {
        try {
            closeLink();
            String token = bearerToken();
            link = openLink(token);
            StompFrames.Frame connected = link.exchange(connectFrame(token), timeoutMs);
            if (connected == null) {
                throw new IOException("STOMP broker closed before CONNECTED");
            }
            if ("ERROR".equals(connected.command())) {
                throw rejected(connected);
            }
            if (!"CONNECTED".equals(connected.command())) {
                throw new IOException("STOMP expected CONNECTED, got " + connected.command());
            }
            subscriptionIds.clear();
            clearConnectFailure();
            driverObject.log(DriverLogLevel.INFO, "STOMP connected via " + transport + " to " + host + ":" + port);
        } catch (DriverException e) {
            closeLink();
            rememberConnectFailure(e);
            throw e;
        } catch (IOException e) {
            closeLink();
            DriverException error = handshakeFailure(e);
            rememberConnectFailure(error);
            throw error;
        }
    }

    @Override
    public void disconnect() {
        clearConnectFailure();
        closeLink();
        subscriptionIds.clear();
        points.clear();
        if (driverObject != null) {
            driverObject.log(DriverLogLevel.INFO, "STOMP disconnected");
        }
    }

    @Override
    public boolean isConnected() {
        return link != null && link.isOpen();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnectedForPoll();
        Map<String, PointBinding> desired = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            desired.put(entry.getKey(), PointBinding.parse(entry.getKey(), entry.getValue()));
        }
        points.clear();
        points.putAll(desired);
        try {
            syncSubscriptions(desired);
            List<StompFrames.Frame> messages = link.drainMessages(timeoutMs, DRAIN_FOLLOW_MS, DRAIN_LIMIT);
            Map<String, String> latestByDestination = new LinkedHashMap<>();
            for (StompFrames.Frame message : messages) {
                String destination = message.headers().get("destination");
                if (destination != null) {
                    latestByDestination.put(destination, message.body());
                }
            }
            for (Map.Entry<String, PointBinding> entry : desired.entrySet()) {
                PointBinding binding = entry.getValue();
                if (binding.state()) {
                    update(entry.getKey(), "Open", "@state");
                    continue;
                }
                if (!latestByDestination.containsKey(binding.destination())) {
                    continue;
                }
                String body = latestByDestination.get(binding.destination());
                try {
                    update(entry.getKey(), StompJson.read(body, binding.selector()), binding.mapping());
                } catch (IllegalArgumentException ex) {
                    throw new DriverException(
                            "STOMP point " + entry.getKey() + " did not match " + binding.mapping()
                                    + ": " + ex.getMessage());
                }
            }
        } catch (DriverException e) {
            throw e;
        } catch (IOException e) {
            closeLink();
            throw new DriverException("STOMP read failed", e);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnectedForPoll();
        PointBinding binding = points.get(pointId);
        String destination = binding != null && !binding.state() ? binding.destination() : pointId;
        String payload = extractValue(value);
        try {
            link.send(StompFrames.encode("SEND", Map.of(
                    "destination", destination,
                    "content-type", "text/plain"
            ), payload));
        } catch (IOException e) {
            closeLink();
            throw new DriverException("STOMP write failed for " + destination, e);
        }
        update(pointId, payload, binding != null ? binding.mapping() : destination);
    }

    private void syncSubscriptions(Map<String, PointBinding> desired) throws IOException {
        Set<String> wanted = new java.util.LinkedHashSet<>();
        for (PointBinding binding : desired.values()) {
            if (!binding.state()) {
                wanted.add(binding.destination());
            }
        }
        for (String destination : List.copyOf(subscriptionIds.keySet())) {
            if (!wanted.contains(destination)) {
                String id = subscriptionIds.remove(destination);
                link.send(StompFrames.encode("UNSUBSCRIBE", Map.of("id", id), ""));
            }
        }
        for (String destination : wanted) {
            if (subscriptionIds.containsKey(destination)) {
                continue;
            }
            String id = "s" + nextSubscription++;
            subscriptionIds.put(destination, id);
            link.send(StompFrames.encode("SUBSCRIBE", Map.of(
                    "id", id,
                    "destination", destination,
                    "ack", "auto"
            ), ""));
        }
    }

    private byte[] connectFrame(String token) throws DriverException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.putAll(parseConnectHeaders(connectHeaders));
        headers.put("accept-version", acceptVersion);
        headers.put("host", vhost.isBlank() ? host : vhost);
        headers.put("heart-beat", "0,0");
        if (!login.isBlank()) {
            headers.put("login", login);
        }
        if (!passcode.isBlank()) {
            headers.put("passcode", passcode);
        }
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", token);
        }
        for (Map.Entry<String, String> header : headers.entrySet()) {
            rejectHeaderInjection(header.getKey(), header.getValue());
        }
        return StompFrames.encode("CONNECT", headers, "");
    }

    static Map<String, String> parseConnectHeaders(String json) throws DriverException {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = StompJson.parse(json);
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new DriverConfigurationException("connectHeaders must be a JSON object");
            }
            Map<String, String> headers = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String name) || name.isBlank()) {
                    throw new DriverConfigurationException("connectHeaders keys must be strings");
                }
                Object raw = entry.getValue();
                if (raw != null && !(raw instanceof String)) {
                    throw new DriverConfigurationException("connectHeaders value for " + name + " must be a string");
                }
                headers.put(name, raw == null ? "" : (String) raw);
            }
            return headers;
        } catch (IllegalArgumentException ex) {
            throw new DriverConfigurationException("connectHeaders is not a JSON object", ex);
        }
    }

    private static void rejectHeaderInjection(String name, String value) throws DriverConfigurationException {
        if (name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0 || name.indexOf(':') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new DriverConfigurationException("STOMP header contains a line break");
        }
    }

    private String bearerToken() throws IOException, DriverException {
        if (authUrl == null || authUrl.isBlank()) {
            return null;
        }
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) java.net.URI.create(authUrl).toURL().openConnection();
        if (connection instanceof javax.net.ssl.HttpsURLConnection https) {
            javax.net.ssl.SSLContext context = StompWebSocket.sslContext(new StompWebSocket.SocketSecurity(useTls, caCertPath));
            https.setSSLSocketFactory(context.getSocketFactory());
            if (caCertPath != null && !caCertPath.isBlank()) {
                https.setHostnameVerifier((name, session) -> true);
            }
        }
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        byte[] body = ("{\"username\":" + jsonString(login) + ",\"password\":" + jsonString(passcode) + "}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (java.io.OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
        int status = connection.getResponseCode();
        java.io.InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = stream == null ? "" : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (status == 401 || status == 403) {
            throw new DriverConfigurationException("STOMP auth URL rejected the login");
        }
        if (status < 200 || status >= 300) {
            throw new IOException("STOMP auth URL returned " + status);
        }
        Object parsed = StompJson.parse(text);
        if (!(parsed instanceof Map<?, ?> map) || !(map.get("token") instanceof String token) || token.isBlank()) {
            throw new DriverConfigurationException("STOMP auth URL did not return a token");
        }
        return token;
    }

    private static String jsonString(String value) {
        String text = value == null ? "" : value;
        StringBuilder out = new StringBuilder(text.length() + 2);
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            switch (current) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(current);
            }
        }
        out.append('"');
        return out.toString();
    }

    private Link openLink(String token) throws IOException, DriverException {
        if (host == null || host.isBlank()) {
            throw new DriverConfigurationException("STOMP host is empty");
        }
        return switch (transport) {
            case "tcp" -> Link.tcp(host, port, timeoutMs);
            case "websocket" -> {
                java.util.LinkedHashMap<String, String> httpHeaders = new java.util.LinkedHashMap<>();
                if (token != null && !token.isBlank()) {
                    httpHeaders.put("Authorization", "Bearer " + token);
                }
                yield Link.websocket(
                        host, port, path, timeoutMs, new StompWebSocket.SocketSecurity(useTls, caCertPath), httpHeaders);
            }
            default -> throw new DriverConfigurationException("STOMP transport must be tcp or websocket");
        };
    }

    private static DriverException handshakeFailure(IOException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("401") || lower.contains("403") || lower.contains("authentication")) {
            return new DriverConfigurationException("STOMP handshake rejected: " + message, error);
        }
        return new DriverException("STOMP connect failed for " + message, error);
    }

    private static DriverException rejected(StompFrames.Frame error) {
        String message = error.headers().getOrDefault("message", error.body());
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("auth") || lower.contains("login") || lower.contains("passcode")
                || lower.contains("unauthorized") || lower.contains("401") || lower.contains("403")) {
            return new DriverConfigurationException("STOMP login rejected: " + message);
        }
        return new DriverException("STOMP broker rejected CONNECT: " + message);
    }

    private void update(String pointId, String value, String mapping) {
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value == null ? "" : value,
                "point", mapping
        )));
    }

    private void ensureConnectedForPoll() throws DriverException {
        if (isConnected()) {
            return;
        }
        if (lastConnectFailure == DriverErrorKind.CONFIGURATION) {
            throw new DriverConfigurationException("STOMP login or address is not usable");
        }
        if (clock.getAsLong() < nextConnectAttemptAt) {
            throw new DriverReconnectPendingException("STOMP reconnect is waiting");
        }
        connect();
    }

    void rememberConnectFailure(DriverException error) {
        lastConnectFailure = DriverErrorKind.classify(error);
        long delay = RETRY_DELAYS_MS[Math.min(retryAttempt, RETRY_DELAYS_MS.length - 1)];
        retryAttempt++;
        nextConnectAttemptAt = clock.getAsLong() + delay;
    }

    private void clearConnectFailure() {
        lastConnectFailure = null;
        retryAttempt = 0;
        nextConnectAttemptAt = 0;
    }

    private void closeLink() {
        if (link != null) {
            link.close();
            link = null;
        }
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "data", "text", "raw")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        if (row.size() == 1) {
            return String.valueOf(row.values().iterator().next());
        }
        return row.toString();
    }

    record PointBinding(String mapping, String destination, String selector, boolean state) {
        static PointBinding parse(String pointId, String raw) throws DriverException {
            String mapping = raw == null || raw.isBlank() ? pointId : raw.trim();
            if ("@state".equals(mapping)) {
                return new PointBinding(mapping, "", null, true);
            }
            int pipe = mapping.indexOf('|');
            String destination = pipe < 0 ? mapping : mapping.substring(0, pipe).trim();
            String selector = pipe < 0 ? null : mapping.substring(pipe + 1).trim();
            if (destination.isEmpty()) {
                throw new DriverConfigurationException("STOMP destination is empty for " + pointId);
            }
            if (selector != null && selector.isEmpty()) {
                selector = null;
            }
            return new PointBinding(mapping, destination, selector, false);
        }
    }

    /** Byte pipe that already speaks whole STOMP frames. */
    private static class Link {
        private final InputStream in;
        private final OutputStream out;
        private final Runnable close;
        private final java.util.function.BooleanSupplier open;
        private final java.util.function.IntConsumer timeout;
        private final StompFrames.Buffer buffer = new StompFrames.Buffer();
        private final boolean chunkIsMessage;

        private Link(
                InputStream in,
                OutputStream out,
                Runnable close,
                java.util.function.BooleanSupplier open,
                java.util.function.IntConsumer timeout,
                boolean chunkIsMessage
        ) {
            this.in = in;
            this.out = out;
            this.close = close;
            this.open = open;
            this.timeout = timeout;
            this.chunkIsMessage = chunkIsMessage;
        }

        static Link tcp(String host, int port, int timeoutMs) throws IOException {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(timeoutMs);
            return new Link(socket.getInputStream(), socket.getOutputStream(), () -> {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // already closed
                }
            }, () -> !socket.isClosed(), millis -> {
                try {
                    socket.setSoTimeout(millis);
                } catch (IOException ignored) {
                    // closed
                }
            }, false);
        }

        static Link websocket(
                String host,
                int port,
                String path,
                int timeoutMs,
                StompWebSocket.SocketSecurity security,
                Map<String, String> httpHeaders
        ) throws IOException {
            StompWebSocket socket = StompWebSocket.connect(host, port, path, timeoutMs, security, httpHeaders);
            return new Link(null, null, socket::close, socket::isOpen, millis -> {
                try {
                    socket.setSoTimeout(millis);
                } catch (IOException ignored) {
                    // closed
                }
            }, true) {
                @Override
                void send(byte[] frame) throws IOException {
                    socket.send(frame);
                }

                @Override
                byte[] readChunk() throws IOException {
                    return socket.read();
                }
            };
        }

        boolean isOpen() {
            return open.getAsBoolean();
        }

        void send(byte[] frame) throws IOException {
            out.write(frame);
            out.flush();
        }

        StompFrames.Frame exchange(byte[] request, int timeoutMs) throws IOException {
            timeout.accept(timeoutMs);
            send(request);
            return next(timeoutMs);
        }

        List<StompFrames.Frame> drainMessages(int firstTimeoutMs, int followTimeoutMs, int limit) throws IOException, DriverException {
            java.util.ArrayList<StompFrames.Frame> messages = new java.util.ArrayList<>();
            StompFrames.Frame first;
            try {
                first = next(firstTimeoutMs);
            } catch (SocketTimeoutException timedOut) {
                return messages;
            }
            collect(messages, first, limit);
            while (messages.size() < limit) {
                try {
                    collect(messages, next(followTimeoutMs), limit);
                } catch (SocketTimeoutException timedOut) {
                    return messages;
                }
            }
            return messages;
        }

        private void collect(List<StompFrames.Frame> messages, StompFrames.Frame frame, int limit)
                throws IOException, DriverException {
            if (frame == null) {
                throw new IOException("STOMP broker closed the session");
            }
            if ("ERROR".equals(frame.command())) {
                throw new DriverException("STOMP broker error: " + frame.headers().getOrDefault("message", frame.body()));
            }
            if ("MESSAGE".equals(frame.command()) && messages.size() < limit) {
                messages.add(frame);
            }
        }

        private StompFrames.Frame next(int timeoutMs) throws IOException {
            timeout.accept(timeoutMs);
            while (true) {
                StompFrames.Frame frame = buffer.poll();
                if (frame != null) {
                    return frame;
                }
                byte[] chunk = readChunk();
                buffer.append(chunk);
            }
        }

        byte[] readChunk() throws IOException {
            if (chunkIsMessage) {
                throw new IOException("WebSocket read is not wired");
            }
            byte[] buf = new byte[4096];
            int n = in.read(buf);
            if (n < 0) {
                throw new IOException("STOMP connection closed");
            }
            if (n == buf.length) {
                return buf;
            }
            return java.util.Arrays.copyOf(buf, n);
        }

        void close() {
            close.run();
        }
    }
}
