package com.ispf.driver.lorawan.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * TCP session for the LoRaWAN NS/AS JSON gateway lab (port 1700 style).
 * <p>
 * Not a LoRa PHY / Semtech HAL.
 */
public final class LorawanLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public LorawanLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public LorawanLabCodec.Uplink readUplink(String deveui) throws IOException {
        writeFully(LorawanLabCodec.encodeGet(deveui));
        return LorawanLabCodec.parseUplink(readLine());
    }

    public void writeValue(String deveui, float value) throws IOException {
        writeFully(LorawanLabCodec.encodeTx(deveui, value));
        LorawanLabCodec.parseOkAck(readLine());
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (buffer.size() == 0) {
                    throw new EOFException("EOF reading LoRaWAN lab line");
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buffer.write(b);
            }
        }
        return buffer.toString(StandardCharsets.US_ASCII);
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
