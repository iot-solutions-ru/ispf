package com.ispf.driver.thread.codec;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session carrying OpenThread Spinel HDLC frames (default host bridge port).
 * <p>
 * Not 802.15.4 Thread radio / RCP silicon RF. JDK sockets only.
 */
public final class SpinelHdlcSession implements AutoCloseable {

    private final Socket socket;
    private final OutputStream out;

    public SpinelHdlcSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        out = socket.getOutputStream();
    }

    /** Sends Spinel CMD_RESET and returns the exact octets written. */
    public byte[] sendCmdReset() throws IOException {
        byte[] frame = SpinelHdlcCodec.encodeCmdReset();
        out.write(frame);
        out.flush();
        return frame;
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
