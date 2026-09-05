package com.ispf.driver.amqp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link AmqpDeviceDriver} against an in-process AMQP 0-9-1 lab broker.
 */
class AmqpDeviceDriverTest {

    private AmqpDeviceDriver driver;
    private FakeAmqp091Broker broker;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (broker != null) {
            broker.close();
            broker = null;
        }
    }

    @Test
    void publishesAndGetsQueueBodies() throws Exception {
        broker = new FakeAmqp091Broker();
        broker.enqueue("sensors.temp", "23.5");
        broker.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(broker.port()),
                "timeoutMs", "3000"
        ));
        driver = new AmqpDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("temperature", "sensors.temp"));
        DataRecord temperature = object.variables.get("temperature");
        assertEquals("23.5", temperature.firstRow().get("value"));
        assertEquals("sensors.temp", temperature.firstRow().get("queue"));
        assertEquals("false", temperature.firstRow().get("empty"));

        driver.writePoint("temperature", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "24.1")
        ));
        assertTrue(broker.awaitPublish("sensors.temp", "24.1", 2000),
                "publish not observed; count=" + broker.publishCount.get()
                        + " lastKey=" + broker.lastPublishKey.get());
        assertEquals("24.1", broker.peekLast("sensors.temp"));

        driver.readPoints(Map.of("temperature", "sensors.temp"));
        assertEquals("24.1", object.variables.get("temperature").firstRow().get("value"));
    }

    @Test
    void emptyQueueReturnsEmptyFlag() throws Exception {
        broker = new FakeAmqp091Broker();
        broker.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(broker.port())
        ));
        driver = new AmqpDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("missing", "no-such-queue"));
        assertEquals("", object.variables.get("missing").firstRow().get("value"));
        assertEquals("true", object.variables.get("missing").firstRow().get("empty"));
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new AmqpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("q", "queue")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void metadataStatesHonestyBoundary() {
        AmqpDeviceDriver d = new AmqpDeviceDriver();
        assertEquals("amqp", d.metadata().id());
        assertTrue(d.metadata().supportsWrite());
        String desc = d.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(desc.contains("0-9-1"));
        assertTrue(desc.contains("not amqp 1.0"));
    }

    /**
     * Minimal AMQP 0-9-1 broker: handshake + default-exchange publish + basic.get queues.
     */
    private static final class FakeAmqp091Broker implements AutoCloseable {

        private static final int FRAME_METHOD = 1;
        private static final int FRAME_HEADER = 2;
        private static final int FRAME_BODY = 3;
        private static final int FRAME_END = 0xCE;
        private static final int CLASS_CONNECTION = 10;
        private static final int CLASS_CHANNEL = 20;
        private static final int CLASS_BASIC = 60;

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-amqp091");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Deque<String>> queues = new ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicInteger publishCount = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicReference<String> lastPublishKey =
                new java.util.concurrent.atomic.AtomicReference<>("");
        private final Object queueSignal = new Object();

        FakeAmqp091Broker() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void enqueue(String queue, String body) {
            queues.computeIfAbsent(queue, key -> new ArrayDeque<>()).addLast(body);
            synchronized (queueSignal) {
                queueSignal.notifyAll();
            }
        }

        String peekLast(String queue) {
            Deque<String> q = queues.get(queue);
            return q == null || q.isEmpty() ? null : q.peekLast();
        }

        boolean awaitPublish(String queue, String body, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            synchronized (queueSignal) {
                while (true) {
                    if (body.equals(peekLast(queue))) {
                        return true;
                    }
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) {
                        return false;
                    }
                    queueSignal.wait(wait);
                }
            }
        }

        void start() {
            executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                byte[] header = in.readNBytes(8);
                if (header.length != 8 || header[0] != 'A' || header[1] != 'M' || header[2] != 'Q' || header[3] != 'P') {
                    return;
                }

                writeMethod(out, 0, CLASS_CONNECTION, 10, buf -> {
                    buf.write(0); // major
                    buf.write(9); // minor
                    writeTable(buf, Map.of("product", "fake-amqp091"));
                    writeLongstr(buf, "PLAIN".getBytes(StandardCharsets.UTF_8));
                    writeLongstr(buf, "en_US".getBytes(StandardCharsets.UTF_8));
                });

                expectMethod(in, 0, CLASS_CONNECTION, 11); // start-ok

                writeMethod(out, 0, CLASS_CONNECTION, 30, buf -> {
                    writeShort(buf, 2047);
                    writeLong(buf, 131072);
                    writeShort(buf, 0);
                });
                expectMethod(in, 0, CLASS_CONNECTION, 31); // tune-ok
                expectMethod(in, 0, CLASS_CONNECTION, 40); // open
                writeMethod(out, 0, CLASS_CONNECTION, 41, buf -> writeShortstr(buf, ""));

                expectMethod(in, 1, CLASS_CHANNEL, 10); // channel.open
                writeMethod(out, 1, CLASS_CHANNEL, 11, buf -> writeLongstr(buf, new byte[0]));

                while (!socket.isClosed()) {
                    Frame frame = readFrame(in);
                    if (frame.type != FRAME_METHOD) {
                        continue;
                    }
                    int classId = u16(frame.payload, 0);
                    int methodId = u16(frame.payload, 2);
                    DataInputStream args = new DataInputStream(new ByteArrayInputStream(
                            frame.payload, 4, frame.payload.length - 4));

                    if (classId == CLASS_CONNECTION && methodId == 50) { // close
                        writeMethod(out, 0, CLASS_CONNECTION, 51, buf -> { });
                        return;
                    }
                    if (classId == CLASS_CHANNEL && methodId == 40) { // channel.close
                        writeMethod(out, frame.channel, CLASS_CHANNEL, 41, buf -> { });
                        continue;
                    }
                    if (classId == CLASS_BASIC && methodId == 40) { // publish
                        args.readUnsignedShort(); // ticket
                        skipShortstr(args); // exchange
                        String routingKey = readShortstr(args);
                        args.readUnsignedByte(); // bits
                        Frame headerFrame = readFrame(in);
                        DataInputStream hp = new DataInputStream(new ByteArrayInputStream(headerFrame.payload));
                        hp.readUnsignedShort();
                        hp.readUnsignedShort();
                        long bodySize = hp.readLong();
                        ByteArrayOutputStream body = new ByteArrayOutputStream();
                        long remaining = bodySize;
                        while (remaining > 0) {
                            Frame bodyFrame = readFrame(in);
                            body.write(bodyFrame.payload);
                            remaining -= bodyFrame.payload.length;
                        }
                        if (bodySize == 0) {
                            // client may still send an empty body frame
                        }
                        enqueue(routingKey, body.toString(StandardCharsets.UTF_8));
                        publishCount.incrementAndGet();
                        lastPublishKey.set(routingKey);
                        continue;
                    }
                    if (classId == CLASS_BASIC && methodId == 70) { // get
                        args.readUnsignedShort();
                        String queue = readShortstr(args);
                        args.readUnsignedByte();
                        Deque<String> q = queues.get(queue);
                        String msg = q == null ? null : q.pollFirst();
                        if (msg == null) {
                            writeMethod(out, frame.channel, CLASS_BASIC, 72, buf -> writeShortstr(buf, ""));
                        } else {
                            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                            writeMethod(out, frame.channel, CLASS_BASIC, 71, buf -> {
                                buf.writeLong(1L);
                                buf.write(0); // redelivered
                                writeShortstr(buf, "");
                                writeShortstr(buf, queue);
                                writeLong(buf, q == null ? 0 : q.size());
                            });
                            ByteArrayOutputStream hp = new ByteArrayOutputStream();
                            DataOutputStream h = new DataOutputStream(hp);
                            writeShort(h, CLASS_BASIC);
                            writeShort(h, 0);
                            h.writeLong(bytes.length);
                            writeShort(h, 0);
                            h.flush();
                            writeFrame(out, FRAME_HEADER, frame.channel, hp.toByteArray());
                            writeFrame(out, FRAME_BODY, frame.channel, bytes);
                        }
                    }
                }
            } catch (EOFException ignored) {
            } catch (IOException ignored) {
            }
        }

        private static void expectMethod(DataInputStream in, int channel, int classId, int methodId)
                throws IOException {
            Frame frame = readFrame(in);
            if (frame.type != FRAME_METHOD || frame.channel != channel) {
                throw new IOException("unexpected frame");
            }
            if (u16(frame.payload, 0) != classId || u16(frame.payload, 2) != methodId) {
                throw new IOException("unexpected method");
            }
        }

        private static Frame readFrame(DataInputStream in) throws IOException {
            int type = in.readUnsignedByte();
            int channel = in.readUnsignedShort();
            int size = in.readInt();
            byte[] payload = in.readNBytes(size);
            int end = in.readUnsignedByte();
            if (end != FRAME_END) {
                throw new IOException("bad frame end");
            }
            return new Frame(type, channel, payload);
        }

        private static void writeMethod(DataOutputStream out, int channel, int classId, int methodId,
                                        FrameWriter args) throws IOException {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(payload);
            writeShort(dos, classId);
            writeShort(dos, methodId);
            args.write(dos);
            dos.flush();
            writeFrame(out, FRAME_METHOD, channel, payload.toByteArray());
        }

        private static void writeFrame(DataOutputStream out, int type, int channel, byte[] payload)
                throws IOException {
            out.writeByte(type);
            out.writeShort(channel);
            out.writeInt(payload.length);
            out.write(payload);
            out.writeByte(FRAME_END);
            out.flush();
        }

        private static int u16(byte[] payload, int offset) {
            return ((payload[offset] & 0xff) << 8) | (payload[offset + 1] & 0xff);
        }

        private static void writeShort(DataOutputStream out, int value) throws IOException {
            out.writeShort(value);
        }

        private static void writeLong(DataOutputStream out, int value) throws IOException {
            out.writeInt(value);
        }

        private static void writeShortstr(DataOutputStream out, String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.writeByte(bytes.length);
            out.write(bytes);
        }

        private static void writeLongstr(DataOutputStream out, byte[] bytes) throws IOException {
            out.writeInt(bytes.length);
            out.write(bytes);
        }

        private static void writeTable(DataOutputStream out, Map<String, String> table) throws IOException {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream t = new DataOutputStream(raw);
            for (Map.Entry<String, String> e : table.entrySet()) {
                writeShortstr(t, e.getKey());
                t.writeByte('S');
                writeLongstr(t, e.getValue().getBytes(StandardCharsets.UTF_8));
            }
            t.flush();
            byte[] bytes = raw.toByteArray();
            out.writeInt(bytes.length);
            out.write(bytes);
        }

        private static void skipShortstr(DataInputStream in) throws IOException {
            int size = in.readUnsignedByte();
            in.skipNBytes(size);
        }

        private static String readShortstr(DataInputStream in) throws IOException {
            int size = in.readUnsignedByte();
            return new String(in.readNBytes(size), StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }

        @FunctionalInterface
        private interface FrameWriter {
            void write(DataOutputStream out) throws IOException;
        }

        private record Frame(int type, int channel, byte[] payload) {
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-amqp",
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
