package com.ispf.driver.dnp3;

import com.ispf.driver.DriverException;
import com.ispf.driver.dnp3.codec.Dnp3TcpCodec;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * DNP3 master channel session (TCP) with Class 0/1/2/3 integrity poll support.
 */
final class Dnp3MasterSession implements AutoCloseable {

    private final String host;
    private final int port;
    private final int masterAddress;
    private final int outstationAddress;
    private final int timeoutMs;
    private final Dnp3ReadCache readCache = new Dnp3ReadCache();

    private Socket socket;
    private int sequence;

    Dnp3MasterSession(String host, int port, int masterAddress, int outstationAddress, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.masterAddress = masterAddress;
        this.outstationAddress = outstationAddress;
        this.timeoutMs = timeoutMs;
    }

    void connect() throws DriverException {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
        } catch (Exception ex) {
            close();
            throw new DriverException("DNP3 master session failed", ex);
        }
    }

    boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    void pollAllClasses() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        readCache.clear();
        try {
            int pollSequence = sequence++ & 0x0F;
            byte[] request = Dnp3TcpCodec.integrityPollRequest(masterAddress, outstationAddress, pollSequence);
            Dnp3TcpCodec.writeFrame(socket.getOutputStream(), request);
            Dnp3TcpCodec.Frame response = Dnp3TcpCodec.readFrame(socket.getInputStream());
            if (response.source() != outstationAddress || response.destination() != masterAddress) {
                throw new DriverException("DNP3 response address mismatch");
            }
            Dnp3TcpCodec.applyResponse(response, readCache);
        } catch (Exception ex) {
            throw new DriverException("DNP3 Class 0/1/2/3 poll failed", ex);
        }
    }

    Dnp3ReadCache cache() {
        return readCache;
    }

    @Override
    public void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // best effort
            }
            socket = null;
        }
    }
}
