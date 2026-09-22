package com.ispf.driver.bacnetmstp.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BACnet MS/TP session over a TCP serial-server socket.
 * <p>
 * Sends clause-9 frames with confirmed ReadProperty / WriteProperty APDUs in the data field
 * (frame type 5) and expects matching ACKs.
 */
public final class BacnetMstpSession implements AutoCloseable {

    private final int localMac;
    private final int remoteMac;
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final AtomicInteger invokeId = new AtomicInteger(1);

    public BacnetMstpSession(
            String host,
            int port,
            int localMac,
            int remoteMac,
            int timeoutMs
    ) throws IOException {
        this.localMac = localMac & 0xFF;
        this.remoteMac = remoteMac & 0xFF;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public float readPresentValue(int encodedObjectId) throws IOException {
        int id = invokeId.getAndIncrement() & 0xFF;
        writeFully(BacnetMstpCodec.encodeReadProperty(
                remoteMac, localMac, id, encodedObjectId, BacnetMstpCodec.PRESENT_VALUE));
        BacnetMstpCodec.Message message = BacnetMstpCodec.decode(BacnetMstpCodec.readFrame(in));
        if (message instanceof BacnetMstpCodec.ReadPropertyAck ack) {
            return ack.value();
        }
        throw new IOException("BACnet MS/TP expected ReadProperty ACK");
    }

    public void writeValue(int encodedObjectId, float value) throws IOException {
        int id = invokeId.getAndIncrement() & 0xFF;
        writeFully(BacnetMstpCodec.encodeWriteProperty(
                remoteMac, localMac, id, encodedObjectId, BacnetMstpCodec.PRESENT_VALUE, value));
        BacnetMstpCodec.Message message = BacnetMstpCodec.decode(BacnetMstpCodec.readFrame(in));
        if (!(message instanceof BacnetMstpCodec.SimpleAck)) {
            throw new IOException("BACnet MS/TP expected WriteProperty SimpleAck");
        }
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
