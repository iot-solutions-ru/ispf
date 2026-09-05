package com.ispf.driver.iec101.codec;

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
 * Synchronous TCP session for the IEC101-lab subset (interrogation + optional commands).
 */
public final class Iec101LabSession implements AutoCloseable {

    private static final byte[] STARTDT_ACT = { 0x07, 0x00, 0x00, 0x00 };
    private static final byte[] STARTDT_CON = { 0x0B, 0x00, 0x00, 0x00 };

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final int commonAddress;
    private final int timeoutMs;
    private int sendSequence;
    private int receiveSequence;

    public Iec101LabSession(String host, int port, int commonAddress, int timeoutMs) throws IOException {
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
        writeFully(Iec101LabCodec.encodeUFrame(STARTDT_ACT));
        byte[] response = readApdu();
        Iec101LabCodec.ParsedApdu parsed = Iec101LabCodec.parseApdu(response);
        if (parsed.kind() != Iec101LabCodec.ApduKind.U || (parsed.ctrl0() & 0xFF) != (STARTDT_CON[0] & 0xFF)) {
            throw new IOException("IEC101-lab expected STARTDT_CON, got ctrl=0x"
                    + Integer.toHexString(parsed.ctrl0()));
        }
    }

    /**
     * Issues general interrogation ({@code C_IC_NA_1}) and collects measured / single-point replies
     * until activation confirmation or timeout with at least one information object.
     */
    public Map<Integer, Iec101LabValue> generalInterrogation() throws IOException {
        byte[] asdu = Iec101LabCodec.encodeInterrogation(commonAddress);
        writeFully(Iec101LabCodec.encodeIFrame(sendSequence, receiveSequence, asdu));
        sendSequence = (sendSequence + 1) & 0x7FFF;

        Map<Integer, Iec101LabValue> cache = new LinkedHashMap<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean sawConfirmation = false;
        while (System.currentTimeMillis() < deadline) {
            int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
            socket.setSoTimeout(remaining);
            byte[] frame;
            try {
                frame = readApdu();
            } catch (IOException e) {
                if (sawConfirmation || !cache.isEmpty()) {
                    break;
                }
                throw e;
            }
            Iec101LabCodec.ParsedApdu parsed = Iec101LabCodec.parseApdu(frame);
            if (parsed.kind() != Iec101LabCodec.ApduKind.I) {
                continue;
            }
            receiveSequence = (receiveSequence + 1) & 0x7FFF;
            byte[] payload = parsed.asdu();
            int typeId = Iec101LabCodec.asduTypeId(payload);
            List<Iec101LabValue> values = parsed.values();
            if (typeId == Iec101LabTypes.C_IC_NA_1) {
                sawConfirmation = true;
                if (!cache.isEmpty()) {
                    break;
                }
                continue;
            }
            for (Iec101LabValue value : values) {
                if (value.typeId() == Iec101LabTypes.M_ME_NC_1
                        || value.typeId() == Iec101LabTypes.M_SP_NA_1) {
                    cache.put(value.ioa(), value);
                }
            }
            if (sawConfirmation && !cache.isEmpty()) {
                break;
            }
        }
        if (cache.isEmpty()) {
            throw new IOException("IEC101-lab interrogation returned no information objects");
        }
        return cache;
    }

    public void writeSingleCommand(int ioa, boolean on) throws IOException {
        byte[] asdu = Iec101LabCodec.encodeSingleCommand(commonAddress, ioa, on);
        writeFully(Iec101LabCodec.encodeIFrame(sendSequence, receiveSequence, asdu));
        sendSequence = (sendSequence + 1) & 0x7FFF;
        awaitCommandAck(Iec101LabTypes.C_SC_NA_1, ioa);
    }

    public void writeSetpointFloat(int ioa, float value) throws IOException {
        byte[] asdu = Iec101LabCodec.encodeSetpointFloat(commonAddress, ioa, value);
        writeFully(Iec101LabCodec.encodeIFrame(sendSequence, receiveSequence, asdu));
        sendSequence = (sendSequence + 1) & 0x7FFF;
        awaitCommandAck(Iec101LabTypes.C_SE_NC_1, ioa);
    }

    private void awaitCommandAck(int expectedType, int ioa) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
            socket.setSoTimeout(remaining);
            Iec101LabCodec.ParsedApdu parsed = Iec101LabCodec.parseApdu(readApdu());
            if (parsed.kind() != Iec101LabCodec.ApduKind.I) {
                continue;
            }
            receiveSequence = (receiveSequence + 1) & 0x7FFF;
            if (Iec101LabCodec.asduTypeId(parsed.asdu()) != expectedType) {
                continue;
            }
            for (Iec101LabValue value : parsed.values()) {
                if (value.ioa() == ioa) {
                    return;
                }
            }
        }
        throw new IOException("IEC101-lab command ack timeout for IOA " + ioa);
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
            throw new EOFException("IEC101-lab EOF");
        }
        if (start != Iec101LabCodec.START) {
            throw new IOException("IEC101-lab expected 0x68, got 0x" + Integer.toHexString(start));
        }
        int length = in.read();
        if (length < 0) {
            throw new EOFException("IEC101-lab EOF in length");
        }
        byte[] body = in.readNBytes(length);
        if (body.length != length) {
            throw new EOFException("IEC101-lab truncated APDU body");
        }
        byte[] frame = new byte[2 + length];
        frame[0] = (byte) Iec101LabCodec.START;
        frame[1] = (byte) length;
        System.arraycopy(body, 0, frame, 2, length);
        return frame;
    }
}
