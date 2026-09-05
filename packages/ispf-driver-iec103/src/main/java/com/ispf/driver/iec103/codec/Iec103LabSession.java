package com.ispf.driver.iec103.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronous TCP session for the IEC103-lab subset (GI + optional general command).
 */
public final class Iec103LabSession implements AutoCloseable {

    private static final byte[] STARTDT_ACT = { 0x07, 0x00, 0x00, 0x00 };
    private static final byte[] STARTDT_CON = { 0x0B, 0x00, 0x00, 0x00 };

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final int commonAddress;
    private final int timeoutMs;
    private int sendSequence;
    private int receiveSequence;

    public Iec103LabSession(String host, int port, int commonAddress, int timeoutMs) throws IOException {
        this.commonAddress = commonAddress;
        this.timeoutMs = timeoutMs;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        handshake();
    }

    private void handshake() throws IOException {
        writeFully(Iec103LabCodec.encodeUFrame(STARTDT_ACT));
        byte[] response = readApdu();
        Iec103LabCodec.ParsedApdu parsed = Iec103LabCodec.parseApdu(response);
        if (parsed.kind() != Iec103LabCodec.ApduKind.U || (parsed.ctrl0() & 0xFF) != (STARTDT_CON[0] & 0xFF)) {
            throw new IOException("IEC103-lab expected STARTDT_CON, got ctrl=0x"
                    + Integer.toHexString(parsed.ctrl0()));
        }
    }

    /**
     * Issues general interrogation (ASDU 7) and collects status / measured replies
     * until GI termination (ASDU 8) or timeout with at least one object.
     */
    public Map<Integer, Iec103LabValue> generalInterrogation() throws IOException {
        byte[] asdu = Iec103LabCodec.encodeInterrogation(commonAddress);
        writeFully(Iec103LabCodec.encodeIFrame(sendSequence, receiveSequence, asdu));
        sendSequence = (sendSequence + 1) & 0x7FFF;

        Map<Integer, Iec103LabValue> cache = new LinkedHashMap<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean sawTermination = false;
        while (System.currentTimeMillis() < deadline) {
            int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
            socket.setSoTimeout(remaining);
            byte[] frame;
            try {
                frame = readApdu();
            } catch (IOException e) {
                if (sawTermination || !cache.isEmpty()) {
                    break;
                }
                throw e;
            }
            Iec103LabCodec.ParsedApdu parsed = Iec103LabCodec.parseApdu(frame);
            if (parsed.kind() != Iec103LabCodec.ApduKind.I) {
                continue;
            }
            receiveSequence = (receiveSequence + 1) & 0x7FFF;
            byte[] payload = parsed.asdu();
            int typeId = Iec103LabCodec.asduTypeId(payload);
            List<Iec103LabValue> values = parsed.values();
            if (typeId == Iec103LabTypes.ASDU_GI_TERMINATION || typeId == Iec103LabTypes.ASDU_GI) {
                sawTermination = typeId == Iec103LabTypes.ASDU_GI_TERMINATION;
                if (sawTermination && !cache.isEmpty()) {
                    break;
                }
                continue;
            }
            for (Iec103LabValue value : values) {
                if (value.typeId() == Iec103LabTypes.ASDU_TIME_TAGGED
                        || value.typeId() == Iec103LabTypes.ASDU_MEASURANDS_II
                        || value.typeId() == Iec103LabTypes.ASDU_LAB_MEAS_FLOAT) {
                    cache.put(value.packedIoa(), value);
                }
            }
            if (sawTermination && !cache.isEmpty()) {
                break;
            }
        }
        if (cache.isEmpty()) {
            throw new IOException("IEC103-lab interrogation returned no information objects");
        }
        return cache;
    }

    public void writeGeneralCommand(int fun, int inf, boolean on) throws IOException {
        byte[] asdu = Iec103LabCodec.encodeGeneralCommand(commonAddress, fun, inf, on);
        writeFully(Iec103LabCodec.encodeIFrame(sendSequence, receiveSequence, asdu));
        sendSequence = (sendSequence + 1) & 0x7FFF;
        awaitCommandAck(fun, inf);
    }

    private void awaitCommandAck(int fun, int inf) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
            socket.setSoTimeout(remaining);
            Iec103LabCodec.ParsedApdu parsed = Iec103LabCodec.parseApdu(readApdu());
            if (parsed.kind() != Iec103LabCodec.ApduKind.I) {
                continue;
            }
            receiveSequence = (receiveSequence + 1) & 0x7FFF;
            if (Iec103LabCodec.asduTypeId(parsed.asdu()) != Iec103LabTypes.ASDU_GENERAL_COMMAND) {
                continue;
            }
            for (Iec103LabValue value : parsed.values()) {
                if (value.fun() == fun && value.inf() == inf) {
                    return;
                }
            }
        }
        throw new IOException("IEC103-lab command ack timeout for FUN=" + fun + " INF=" + inf);
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private void writeFully(byte[] data) throws IOException {
        out.write(data);
        out.flush();
    }

    private byte[] readApdu() throws IOException {
        int start = in.read();
        if (start < 0) {
            throw new EOFException("IEC103-lab EOF");
        }
        if (start != Iec103LabCodec.START) {
            throw new IOException("IEC103-lab expected 0x68, got 0x" + Integer.toHexString(start));
        }
        int length = in.read();
        if (length < 0) {
            throw new EOFException("IEC103-lab EOF in length");
        }
        byte[] body = in.readNBytes(length);
        if (body.length != length) {
            throw new EOFException("IEC103-lab truncated APDU body");
        }
        byte[] frame = new byte[2 + length];
        frame[0] = (byte) Iec103LabCodec.START;
        frame[1] = (byte) length;
        System.arraycopy(body, 0, frame, 2, length);
        return frame;
    }
}
