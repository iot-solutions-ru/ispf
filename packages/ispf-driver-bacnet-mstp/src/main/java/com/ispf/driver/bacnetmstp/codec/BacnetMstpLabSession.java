package com.ispf.driver.bacnetmstp.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP session for the BACnet MS/TP gateway lab (BVLC-less framed APDUs).
 */
public final class BacnetMstpLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final AtomicInteger invokeId = new AtomicInteger(1);

    public BacnetMstpLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public float readPresentValue(int encodedObjectId) throws IOException {
        int id = invokeId.getAndIncrement() & 0xFF;
        writeFully(BacnetMstpLabCodec.encodeReadProperty(id, encodedObjectId, BacnetMstpLabCodec.PRESENT_VALUE));
        BacnetMstpLabCodec.Message message = BacnetMstpLabCodec.decode(readFrame());
        if (message instanceof BacnetMstpLabCodec.ReadPropertyAck ack) {
            return ack.value();
        }
        throw new IOException("BACnet MS/TP lab expected ReadProperty ACK");
    }

    public void writePresentValue(int encodedObjectId, float value) throws IOException {
        int id = invokeId.getAndIncrement() & 0xFF;
        writeFully(BacnetMstpLabCodec.encodeWriteProperty(id, encodedObjectId, BacnetMstpLabCodec.PRESENT_VALUE, value));
        BacnetMstpLabCodec.Message message = BacnetMstpLabCodec.decode(readFrame());
        if (!(message instanceof BacnetMstpLabCodec.SimpleAck)) {
            throw new IOException("BACnet MS/TP lab expected WriteProperty SimpleAck");
        }
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    private byte[] readFrame() throws IOException {
        byte[] header = readFully(2);
        int length = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        byte[] body = readFully(length);
        byte[] frame = new byte[2 + length];
        System.arraycopy(header, 0, frame, 0, 2);
        System.arraycopy(body, 0, frame, 2, length);
        return frame;
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("EOF reading BACnet MS/TP lab frame");
            }
            offset += read;
        }
        return buffer;
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
