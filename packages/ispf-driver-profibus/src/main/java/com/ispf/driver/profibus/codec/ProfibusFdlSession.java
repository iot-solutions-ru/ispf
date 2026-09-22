package com.ispf.driver.profibus.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * PROFIBUS FDL session over a TCP serial-server socket.
 * Connect opens TCP and sends an SD1 station probe; the peer replies with SD2.
 * Point octets are read and written with SD2 user-data PDUs.
 */
public final class ProfibusFdlSession implements AutoCloseable {

    private final int localStation;
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public ProfibusFdlSession(String host, int port, int timeoutMs) throws IOException {
        this(host, port, timeoutMs, ProfibusFdlTypes.DEFAULT_LOCAL_STATION,
                ProfibusFdlTypes.DEFAULT_REMOTE_STATION);
    }

    public ProfibusFdlSession(
            String host,
            int port,
            int timeoutMs,
            int localStation,
            int remoteStation
    ) throws IOException {
        this.localStation = localStation & 0xFF;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        probe(remoteStation & 0xFF);
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    public double readValue(int slave, int byteOffset) throws IOException {
        byte[] pdu = ProfibusFdlCodec.encodeReadRequest(slave, byteOffset);
        writeFully(ProfibusFdlCodec.encodeSd2(slave, localStation, ProfibusFdlTypes.FC_SRD, pdu));
        ProfibusFdlFrame reply = ProfibusFdlCodec.readFrame(in);
        if (reply.kind() != ProfibusFdlFrame.Kind.SD2) {
            throw new IOException("PROFIBUS FDL read expected SD2 reply");
        }
        return ProfibusFdlCodec.decodeReadResponse(reply.pdu());
    }

    public void writeValue(int slave, int byteOffset, double value) throws IOException {
        byte[] pdu = ProfibusFdlCodec.encodeWriteRequest(slave, byteOffset, value);
        writeFully(ProfibusFdlCodec.encodeSd2(slave, localStation, ProfibusFdlTypes.FC_SDA, pdu));
        ProfibusFdlFrame reply = ProfibusFdlCodec.readFrame(in);
        if (reply.kind() != ProfibusFdlFrame.Kind.SD2) {
            throw new IOException("PROFIBUS FDL write expected SD2 reply");
        }
        ProfibusFdlCodec.requireWriteResponse(reply.pdu());
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private void probe(int remoteStation) throws IOException {
        writeFully(ProfibusFdlCodec.encodeSd1(
                remoteStation, localStation, ProfibusFdlTypes.FC_REQUEST_FDL_STATUS));
        ProfibusFdlFrame reply = ProfibusFdlCodec.readFrame(in);
        if (reply.kind() != ProfibusFdlFrame.Kind.SD2 && reply.kind() != ProfibusFdlFrame.Kind.SD1) {
            throw new IOException("PROFIBUS FDL station probe was not acknowledged");
        }
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }
}
