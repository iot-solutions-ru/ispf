package com.ispf.driver.profibuspa.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * PROFIBUS PA FDL session over a TCP serial-server socket.
 * Connect opens TCP and sends an SD1 station probe; the peer replies with SD2.
 * Slot/addr/pa points are read and written with SD2 user-data PDUs (same FDL framing as DP).
 */
public final class ProfibusPaFdlSession implements AutoCloseable {

    private final int localStation;
    private final int remoteStation;
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public ProfibusPaFdlSession(String host, int port, int timeoutMs) throws IOException {
        this(host, port, timeoutMs, ProfibusPaFdlTypes.DEFAULT_LOCAL_STATION,
                ProfibusPaFdlTypes.DEFAULT_REMOTE_STATION);
    }

    public ProfibusPaFdlSession(
            String host,
            int port,
            int timeoutMs,
            int localStation,
            int remoteStation
    ) throws IOException {
        this.localStation = localStation & 0xFF;
        this.remoteStation = remoteStation & 0xFF;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        probe();
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    public double readValue(String kind, int index) throws IOException {
        byte[] pdu = ProfibusPaFdlCodec.encodeReadRequest(kind, index);
        writeFully(ProfibusPaFdlCodec.encodeSd2(
                remoteStation, localStation, ProfibusPaFdlTypes.FC_SRD, pdu));
        ProfibusPaFdlFrame reply = ProfibusPaFdlCodec.readFrame(in);
        if (reply.kind() != ProfibusPaFdlFrame.Kind.SD2) {
            throw new IOException("PROFIBUS FDL read expected SD2 reply");
        }
        return ProfibusPaFdlCodec.decodeReadResponse(reply.pdu());
    }

    public void writeValue(String kind, int index, double value) throws IOException {
        byte[] pdu = ProfibusPaFdlCodec.encodeWriteRequest(kind, index, value);
        writeFully(ProfibusPaFdlCodec.encodeSd2(
                remoteStation, localStation, ProfibusPaFdlTypes.FC_SDA, pdu));
        ProfibusPaFdlFrame reply = ProfibusPaFdlCodec.readFrame(in);
        if (reply.kind() != ProfibusPaFdlFrame.Kind.SD2) {
            throw new IOException("PROFIBUS FDL write expected SD2 reply");
        }
        ProfibusPaFdlCodec.requireWriteResponse(reply.pdu());
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private void probe() throws IOException {
        writeFully(ProfibusPaFdlCodec.encodeSd1(
                remoteStation, localStation, ProfibusPaFdlTypes.FC_REQUEST_FDL_STATUS));
        ProfibusPaFdlFrame reply = ProfibusPaFdlCodec.readFrame(in);
        if (reply.kind() != ProfibusPaFdlFrame.Kind.SD2 && reply.kind() != ProfibusPaFdlFrame.Kind.SD1) {
            throw new IOException("PROFIBUS FDL station probe was not acknowledged");
        }
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }
}
