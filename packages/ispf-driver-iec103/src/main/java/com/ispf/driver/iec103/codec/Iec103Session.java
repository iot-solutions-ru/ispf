package com.ispf.driver.iec103.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unbalanced IEC 60870-5-103 primary over a TCP socket (serial-server style).
 * Connect sends reset-of-remote-link and waits for fixed ACK or {@code E5}.
 * General interrogation is ASDU 7 in a variable FT1.2 frame; the session collects
 * status and measured replies until ASDU 8 termination.
 */
public final class Iec103Session implements AutoCloseable {

    private static final int MAX_FRAMES = 64;

    private final int linkAddress;
    private final int asduAddress;
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private boolean fcb = true;

    public Iec103Session(String host, int port, int linkAddress, int asduAddress, int timeoutMs)
            throws IOException {
        this.linkAddress = linkAddress & 0xFF;
        this.asduAddress = asduAddress & 0xFF;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        resetLink();
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    public Map<Integer, Iec103Value> generalInterrogation() throws IOException {
        sendUserData(Iec103Codec.encodeInterrogation(asduAddress));
        Map<Integer, Iec103Value> cache = new LinkedHashMap<>();
        boolean sawTermination = false;
        for (int attempt = 0; attempt < MAX_FRAMES; attempt++) {
            Iec103Frame frame = Iec103Codec.readFrame(in);
            if (frame.kind() != Iec103Frame.Kind.VARIABLE) {
                if (isNoData(frame)) {
                    break;
                }
                continue;
            }
            byte[] asdu = frame.asdu();
            int typeId = Iec103Codec.asduTypeId(asdu);
            if (typeId == Iec103Types.ASDU_GI_TERMINATION) {
                sawTermination = true;
                if (!cache.isEmpty()) {
                    return cache;
                }
                continue;
            }
            if (typeId == Iec103Types.ASDU_GI) {
                continue;
            }
            for (Iec103Value value : Iec103Codec.decodeAsdu(asdu)) {
                if (value.typeId() == Iec103Types.ASDU_TIME_TAGGED
                        || value.typeId() == Iec103Types.ASDU_RELATIVE_TIME_TAGGED
                        || value.typeId() == Iec103Types.ASDU_MEASURANDS_II
                        || value.typeId() == Iec103Types.ASDU_GENERIC_DATA
                        || value.typeId() == Iec103Types.ASDU_MEAS_FLOAT) {
                    cache.put(value.packedIoa(), value);
                }
            }
            if (sawTermination && !cache.isEmpty()) {
                return cache;
            }
        }
        if (!cache.isEmpty()) {
            return cache;
        }
        throw new IOException("IEC 103 interrogation returned no information objects");
    }

    public void writeGeneralCommand(int fun, int inf, boolean on) throws IOException {
        sendUserData(Iec103Codec.encodeGeneralCommand(asduAddress, fun, inf, on));
        awaitCommandAck(fun, inf);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }

    private void resetLink() throws IOException {
        int control = Iec103Codec.primaryControl(Iec103Types.FC_RESET_REMOTE_LINK, false, false);
        writeFully(Iec103Codec.encodeFixed(control, linkAddress));
        Iec103Frame frame = Iec103Codec.readFrame(in);
        if (frame.kind() == Iec103Frame.Kind.SINGLE_ACK) {
            return;
        }
        if (frame.kind() == Iec103Frame.Kind.FIXED
                && Iec103Codec.functionCode(frame.control()) == Iec103Types.FC_ACK) {
            return;
        }
        throw new IOException("IEC 103 reset link was not acknowledged");
    }

    private void sendUserData(byte[] asdu) throws IOException {
        int control = Iec103Codec.primaryControl(Iec103Types.FC_USER_DATA, true, fcb);
        fcb = !fcb;
        writeFully(Iec103Codec.encodeVariable(control, linkAddress, asdu));
    }

    private void awaitCommandAck(int fun, int inf) throws IOException {
        for (int attempt = 0; attempt < MAX_FRAMES; attempt++) {
            Iec103Frame frame = Iec103Codec.readFrame(in);
            if (frame.kind() != Iec103Frame.Kind.VARIABLE) {
                if (isNoData(frame)) {
                    break;
                }
                continue;
            }
            byte[] asdu = frame.asdu();
            if (Iec103Codec.asduTypeId(asdu) != Iec103Types.ASDU_GENERAL_COMMAND) {
                continue;
            }
            int cause = Iec103Codec.asduCause(asdu);
            if (cause != Iec103Types.COT_COMMAND_ACK && cause != Iec103Types.COT_COMMAND_NACK) {
                continue;
            }
            List<Iec103Value> values = Iec103Codec.decodeAsdu(asdu);
            for (Iec103Value value : values) {
                if (value.fun() == fun && value.inf() == inf) {
                    return;
                }
            }
        }
        throw new IOException("IEC 103 command ack timeout for FUN=" + fun + " INF=" + inf);
    }

    private static boolean isNoData(Iec103Frame frame) {
        return frame.kind() == Iec103Frame.Kind.FIXED
                && Iec103Codec.functionCode(frame.control()) == Iec103Types.FC_NO_DATA;
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }
}
