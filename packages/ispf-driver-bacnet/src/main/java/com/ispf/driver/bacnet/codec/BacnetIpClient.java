package com.ispf.driver.bacnet.codec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UDP client for the limited BACnet/IP service subset implemented by {@link BacnetPacketCodec}.
 */
public final class BacnetIpClient implements AutoCloseable {

    private final DatagramSocket socket;
    private final InetSocketAddress configuredRemote;
    private final int timeoutMs;
    private final AtomicInteger invokeIds = new AtomicInteger(1);
    private InetSocketAddress remote;
    private int remoteDeviceId;

    public BacnetIpClient(String bindAddress, int bindPort, String host, int port, int timeoutMs)
            throws IOException {
        InetAddress localAddress = InetAddress.getByName(bindAddress);
        this.socket = new DatagramSocket(new InetSocketAddress(localAddress, Math.max(bindPort, 0)));
        this.socket.setSoTimeout(timeoutMs);
        this.configuredRemote = new InetSocketAddress(InetAddress.getByName(host), port);
        this.timeoutMs = timeoutMs;
    }

    public void connectStatic(int remoteDeviceId) {
        this.remoteDeviceId = remoteDeviceId;
        this.remote = configuredRemote;
    }

    public int discoverRemoteDevice(int remoteDeviceId) throws BacnetException {
        this.remoteDeviceId = remoteDeviceId;
        send(BacnetPacketCodec.encodeWhoIs(), configuredRemote);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            BacnetPacketCodec.Message message = receive(deadline);
            if (message instanceof BacnetPacketCodec.IAmMessage iAm && iAm.deviceId() == remoteDeviceId) {
                return iAm.deviceId();
            }
        }
        throw new BacnetException("Who-Is did not discover BACnet device " + remoteDeviceId);
    }

    public boolean isConnected() {
        return remote != null;
    }

    public BacnetValue readProperty(BacnetObjectIdentifier objectId, BacnetPropertyIdentifier property)
            throws BacnetException {
        ensureConnected();
        int invokeId = nextInvokeId();
        send(BacnetPacketCodec.encodeReadPropertyRequest(invokeId, objectId, property), remote);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            BacnetPacketCodec.Message message = receive(deadline);
            if (message instanceof BacnetPacketCodec.ReadPropertyAckMessage ack
                    && ack.invokeId() == invokeId
                    && ack.request().objectId().equals(objectId)
                    && ack.request().property() == property) {
                return ack.value();
            }
        }
        throw new BacnetException("BACnet ReadProperty timed out for " + objectId + ":" + property);
    }

    public void writeProperty(
            BacnetObjectIdentifier objectId,
            BacnetPropertyIdentifier property,
            BacnetValue value
    ) throws BacnetException {
        ensureConnected();
        int invokeId = nextInvokeId();
        send(BacnetPacketCodec.encodeWritePropertyRequest(invokeId, objectId, property, value), remote);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            BacnetPacketCodec.Message message = receive(deadline);
            if (message instanceof BacnetPacketCodec.SimpleAckMessage ack
                    && ack.invokeId() == invokeId
                    && ack.serviceChoice() == BacnetPacketCodec.SERVICE_WRITE_PROPERTY) {
                return;
            }
        }
        throw new BacnetException("BACnet WriteProperty timed out for " + objectId + ":" + property);
    }

    private BacnetPacketCodec.Message receive(long deadline) throws BacnetException {
        byte[] data = new byte[2048];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        int remainingMs = (int) Math.max(1, deadline - System.currentTimeMillis());
        try {
            socket.setSoTimeout(remainingMs);
            socket.receive(packet);
            BacnetPacketCodec.Message message = BacnetPacketCodec.decode(packet.getData(), packet.getLength());
            if (message instanceof BacnetPacketCodec.IAmMessage iAm
                    && remoteDeviceId == iAm.deviceId()
                    && packet.getSocketAddress() instanceof InetSocketAddress address) {
                remote = address;
            }
            return message;
        } catch (SocketTimeoutException e) {
            throw new BacnetException("BACnet UDP receive timed out", e);
        } catch (IOException e) {
            throw new BacnetException("BACnet UDP receive failed", e);
        }
    }

    private void send(byte[] data, SocketAddress address) throws BacnetException {
        try {
            socket.send(new DatagramPacket(data, data.length, address));
        } catch (IOException e) {
            throw new BacnetException("BACnet UDP send failed", e);
        }
    }

    private int nextInvokeId() {
        return invokeIds.getAndUpdate(current -> current == 255 ? 1 : current + 1);
    }

    private void ensureConnected() throws BacnetException {
        if (remote == null) {
            throw new BacnetException("BACnet remote device is not connected");
        }
    }

    @Override
    public void close() {
        socket.close();
    }
}
