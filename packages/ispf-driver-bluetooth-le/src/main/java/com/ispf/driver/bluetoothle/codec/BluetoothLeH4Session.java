package com.ispf.driver.bluetoothle.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session carrying H4 HCI Command / Event packets (default port {@code 9999}).
 * <p>
 * On {@link #reset()} sends HCI_Reset and expects Command Complete
 * {@code 04 0E 04 01 03 0C 00}. {@link #readBdAddr()} sends HCI_Read_BD_ADDR.
 * <p>
 * Not a BLE radio and not a full GATT client — JDK sockets only, Apache-2.0.
 */
public final class BluetoothLeH4Session implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public BluetoothLeH4Session(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    /**
     * Sends HCI_Reset ({@code 01 03 0C 00}) and requires Command Complete status 0.
     */
    public void reset() throws IOException {
        write(H4HciCodec.encodeReset());
        byte[] event = H4HciCodec.readEventPacket(in);
        int status = H4HciCodec.commandCompleteStatus(event, H4HciCodec.OPCODE_RESET);
        if (status != 0) {
            throw new IOException("HCI Reset Command Complete status 0x"
                    + Integer.toHexString(status));
        }
    }

    /**
     * Sends HCI_Read_BD_ADDR ({@code 01 09 10 00}) and returns a colon-hex address.
     */
    public String readBdAddr() throws IOException {
        write(H4HciCodec.encodeReadBdAddr());
        byte[] event = H4HciCodec.readEventPacket(in);
        return H4HciCodec.bdAddrFromCommandComplete(event);
    }

    private void write(byte[] frame) throws IOException {
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
