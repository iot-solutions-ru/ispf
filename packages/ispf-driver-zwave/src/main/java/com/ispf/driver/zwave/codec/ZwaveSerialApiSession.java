package com.ispf.driver.zwave.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Synchronous Z-Wave Serial API session over TCP (not RF).
 * <p>
 * Each host request expects ACK {@code 0x06}, then a Serial API response frame; the host ACKs
 * the response. Clean-room Apache-2.0 — JDK sockets only.
 */
public final class ZwaveSerialApiSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public ZwaveSerialApiSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    /**
     * Sends {@link ZwaveSerialApiCodec#FUNC_ID_ZW_GET_VERSION} and returns response payload bytes
     * (after FuncID, before checksum).
     */
    public byte[] getVersion() throws IOException {
        byte[] response = transact(ZwaveSerialApiCodec.encodeRequest(
                ZwaveSerialApiCodec.FUNC_ID_ZW_GET_VERSION));
        return ZwaveSerialApiCodec.params(response);
    }

    /**
     * Sends {@link ZwaveSerialApiCodec#FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO} for {@code nodeId}.
     * Returns response payload (capability and class bytes when present).
     */
    public byte[] getNodeProtocolInfo(int nodeId) throws IOException {
        byte[] response = transact(ZwaveSerialApiCodec.encodeRequest(
                ZwaveSerialApiCodec.FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO,
                (byte) (nodeId & 0xFF)));
        return ZwaveSerialApiCodec.params(response);
    }

    private byte[] transact(byte[] request) throws IOException {
        ZwaveSerialApiCodec.writeFrame(out, request);
        ZwaveSerialApiCodec.readAck(in);
        byte[] response = ZwaveSerialApiCodec.readFrame(in);
        ZwaveSerialApiCodec.writeAck(out);
        return response;
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
