package com.ispf.driver.bacnet;

import com.ispf.driver.bacnet.codec.BacnetEngineeringUnit;
import com.ispf.driver.bacnet.codec.BacnetException;
import com.ispf.driver.bacnet.codec.BacnetObjectIdentifier;
import com.ispf.driver.bacnet.codec.BacnetObjectType;
import com.ispf.driver.bacnet.codec.BacnetPacketCodec;
import com.ispf.driver.bacnet.codec.BacnetPropertyIdentifier;
import com.ispf.driver.bacnet.codec.BacnetValue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ISPF-owned BACnet/IP loopback device for UDP driver tests.
 */
final class BacnetLoopbackServer implements AutoCloseable {

    static final String LOOPBACK_HOST = "127.0.0.1";

    private final int deviceId;
    private final DatagramSocket socket;
    private final Map<BacnetObjectIdentifier, LoopbackObject> objects = new ConcurrentHashMap<>();
    private final Thread worker;
    private volatile boolean running = true;

    BacnetLoopbackServer(int deviceId, int port, float initialValue) throws Exception {
        this(deviceId, LOOPBACK_HOST, port, initialValue);
    }

    BacnetLoopbackServer(int deviceId, String bindHost, int port, float initialValue) throws Exception {
        this.deviceId = deviceId;
        this.socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(bindHost), port));
        addAnalogValue(1, initialValue, BacnetEngineeringUnit.NO_UNITS, true);
        worker = new Thread(this::serve, "bacnet-loopback-" + port);
        worker.setDaemon(true);
        worker.start();
    }

    int deviceId() {
        return deviceId;
    }

    int objectCount() {
        return objects.size();
    }

    void addAnalogValue(int instance, float value, BacnetEngineeringUnit unit, boolean writable) {
        objects.put(
                new BacnetObjectIdentifier(BacnetObjectType.ANALOG_VALUE, instance),
                new LoopbackObject(new BacnetValue.RealValue(value), unit, writable)
        );
    }

    void addAnalogInput(int instance, float value, BacnetEngineeringUnit unit) {
        objects.put(
                new BacnetObjectIdentifier(BacnetObjectType.ANALOG_INPUT, instance),
                new LoopbackObject(new BacnetValue.RealValue(value), unit, false)
        );
    }

    void addBinaryValue(int instance, boolean active, boolean writable) {
        objects.put(
                new BacnetObjectIdentifier(BacnetObjectType.BINARY_VALUE, instance),
                new LoopbackObject(new BacnetValue.BinaryValue(active), BacnetEngineeringUnit.NO_UNITS, writable)
        );
    }

    void addBinaryInput(int instance, boolean active) {
        objects.put(
                new BacnetObjectIdentifier(BacnetObjectType.BINARY_INPUT, instance),
                new LoopbackObject(new BacnetValue.BinaryValue(active), BacnetEngineeringUnit.NO_UNITS, false)
        );
    }

    BacnetValue read(BacnetObjectIdentifier objectId) {
        LoopbackObject object = objects.get(objectId);
        return object == null ? null : object.value();
    }

    private void serve() {
        byte[] buffer = new byte[2048];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                handle(packet.getData(), packet.getLength(), packet.getSocketAddress());
            } catch (SocketException e) {
                if (running) {
                    throw new IllegalStateException("BACnet loopback socket failed", e);
                }
            } catch (Exception ignored) {
                // Ignore malformed or unsupported requests so tests exercise client timeouts.
            }
        }
    }

    private void handle(byte[] data, int length, SocketAddress sender) throws IOException, BacnetException {
        BacnetPacketCodec.Message message = BacnetPacketCodec.decode(data, length);
        switch (message) {
            case BacnetPacketCodec.WhoIsMessage ignored -> send(BacnetPacketCodec.encodeIAm(deviceId), sender);
            case BacnetPacketCodec.ReadPropertyRequestMessage read -> handleRead(read, sender);
            case BacnetPacketCodec.WritePropertyRequestMessage write -> handleWrite(write, sender);
            default -> {
            }
        }
    }

    private void handleRead(BacnetPacketCodec.ReadPropertyRequestMessage message, SocketAddress sender)
            throws IOException {
        LoopbackObject object = objects.get(message.request().objectId());
        if (object == null) {
            return;
        }
        BacnetValue value;
        if (message.request().property() == BacnetPropertyIdentifier.PRESENT_VALUE) {
            value = object.value();
        } else if (message.request().property() == BacnetPropertyIdentifier.UNITS) {
            value = new BacnetValue.UnsignedValue(object.unit().id());
        } else {
            return;
        }
        send(BacnetPacketCodec.encodeReadPropertyAck(message.invokeId(), message.request(), value), sender);
    }

    private void handleWrite(BacnetPacketCodec.WritePropertyRequestMessage message, SocketAddress sender)
            throws IOException {
        LoopbackObject object = objects.get(message.request().objectId());
        if (object == null || !object.writable()
                || message.request().property() != BacnetPropertyIdentifier.PRESENT_VALUE) {
            return;
        }
        object.value(message.request().value());
        send(BacnetPacketCodec.encodeSimpleAck(message.invokeId(), BacnetPacketCodec.SERVICE_WRITE_PROPERTY), sender);
    }

    private void send(byte[] data, SocketAddress address) throws IOException {
        socket.send(new DatagramPacket(data, data.length, address));
    }

    @Override
    public void close() {
        running = false;
        socket.close();
        try {
            worker.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class LoopbackObject {
        private volatile BacnetValue value;
        private final BacnetEngineeringUnit unit;
        private final boolean writable;

        private LoopbackObject(BacnetValue value, BacnetEngineeringUnit unit, boolean writable) {
            this.value = value;
            this.unit = unit;
            this.writable = writable;
        }

        private BacnetValue value() {
            return value;
        }

        private void value(BacnetValue value) {
            this.value = value;
        }

        private BacnetEngineeringUnit unit() {
            return unit;
        }

        private boolean writable() {
            return writable;
        }
    }
}
