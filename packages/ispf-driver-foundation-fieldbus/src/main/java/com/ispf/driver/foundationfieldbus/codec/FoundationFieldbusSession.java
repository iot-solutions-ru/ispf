package com.ispf.driver.foundationfieldbus.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session for the Foundation Fieldbus length-prefixed probe. Not an HSE FDA session and not native H1.
 */
public final class FoundationFieldbusSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public FoundationFieldbusSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(int kindCode, int index) throws IOException {
        ByteArrayOutputStream request = new ByteArrayOutputStream(8);
        request.writeBytes(FoundationFieldbusCodec.encodeFdaProbe(4));
        request.write(0x01); // read
        request.write(kindCode & 0xFF);
        request.write((index >> 8) & 0xFF);
        request.write(index & 0xFF);
        writeFully(request.toByteArray());
        byte[] response = FoundationFieldbusCodec.readFully(in, 4);
        return FoundationFieldbusCodec.decodeFloat(response);
    }

    public void writeValue(int kindCode, int index, double value) throws IOException {
        byte[] floatBytes = FoundationFieldbusCodec.encodeFloat((float) value);
        ByteArrayOutputStream request = new ByteArrayOutputStream(12);
        request.writeBytes(FoundationFieldbusCodec.encodeFdaProbe(8));
        request.write(0x02); // write
        request.write(kindCode & 0xFF);
        request.write((index >> 8) & 0xFF);
        request.write(index & 0xFF);
        request.writeBytes(floatBytes);
        writeFully(request.toByteArray());
        byte[] ack = FoundationFieldbusCodec.readFully(in, 1);
        if ((ack[0] & 0xFF) != 0x00) {
            throw new IOException("Foundation Fieldbus write rejected: status=" + (ack[0] & 0xFF));
        }
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

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }
}
