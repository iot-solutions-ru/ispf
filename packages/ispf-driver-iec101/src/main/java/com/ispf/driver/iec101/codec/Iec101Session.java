package com.ispf.driver.iec101.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unbalanced IEC 60870-5-101 primary over a TCP socket (serial-server style).
 * Connect sends reset-of-remote-link and waits for {@code ACK} or {@code E5}.
 * General interrogation is {@code C_IC_NA_1} with QOI 20; the session collects
 * {@code M_ME_NC_1} and {@code M_SP_NA_1} until activation termination.
 */
public final class Iec101Session implements AutoCloseable {

    private static final int MAX_FRAMES = 32;

    private final int linkAddress;
    private final int commonAddress;
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private boolean fcb = true;

    public Iec101Session(String host, int port, int linkAddress, int commonAddress, int timeoutMs) throws IOException {
        this.linkAddress = linkAddress & 0xFF;
        this.commonAddress = commonAddress & 0xFFFF;
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

    public Map<Integer, Iec101Value> generalInterrogation() throws IOException {
        sendUserData(Iec101Codec.encodeInterrogation(commonAddress));
        Map<Integer, Iec101Value> values = new LinkedHashMap<>();
        for (int attempt = 0; attempt < MAX_FRAMES; attempt++) {
            Iec101Frame frame = Iec101Codec.readFrame(in);
            if (frame.kind() != Iec101Frame.Kind.VARIABLE) {
                if (isNoData(frame)) {
                    break;
                }
                requestClass1();
                continue;
            }
            byte[] asdu = frame.asdu();
            int typeId = Iec101Codec.asduTypeId(asdu);
            if (typeId == Iec101Types.C_IC_NA_1
                    && Iec101Codec.asduCause(asdu) == Iec101Types.COT_ACTIVATION_TERMINATION) {
                return values;
            }
            for (Iec101Value value : Iec101Codec.decodeAsdu(asdu)) {
                if (value.typeId() == Iec101Types.M_ME_NC_1 || value.typeId() == Iec101Types.M_SP_NA_1) {
                    values.put(value.ioa(), value);
                }
            }
        }
        throw new IOException("IEC 101 interrogation ended without activation termination");
    }

    public void writeSingleCommand(int ioa, boolean on) throws IOException {
        sendUserData(Iec101Codec.encodeSingleCommand(commonAddress, ioa, on));
        awaitCommandAck(Iec101Types.C_SC_NA_1, ioa);
    }

    public void writeSetpointFloat(int ioa, float value) throws IOException {
        sendUserData(Iec101Codec.encodeSetpointFloat(commonAddress, ioa, value));
        awaitCommandAck(Iec101Types.C_SE_NC_1, ioa);
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
        int control = Iec101Codec.primaryControl(Iec101Types.FC_RESET_REMOTE_LINK, false, false);
        writeFully(Iec101Codec.encodeFixed(control, linkAddress));
        Iec101Frame frame = Iec101Codec.readFrame(in);
        if (frame.kind() == Iec101Frame.Kind.SINGLE_ACK) {
            return;
        }
        if (frame.kind() == Iec101Frame.Kind.FIXED
                && Iec101Codec.functionCode(frame.control()) == Iec101Types.FC_ACK) {
            return;
        }
        throw new IOException("IEC 101 reset link was not acknowledged");
    }

    private void sendUserData(byte[] asdu) throws IOException {
        int control = Iec101Codec.primaryControl(Iec101Types.FC_USER_DATA, true, fcb);
        fcb = !fcb;
        writeFully(Iec101Codec.encodeVariable(control, linkAddress, asdu));
    }

    private void requestClass1() throws IOException {
        int control = Iec101Codec.primaryControl(Iec101Types.FC_REQUEST_CLASS1, true, fcb);
        fcb = !fcb;
        writeFully(Iec101Codec.encodeFixed(control, linkAddress));
    }

    private void awaitCommandAck(int expectedType, int ioa) throws IOException {
        for (int attempt = 0; attempt < MAX_FRAMES; attempt++) {
            Iec101Frame frame = Iec101Codec.readFrame(in);
            if (frame.kind() != Iec101Frame.Kind.VARIABLE) {
                if (isNoData(frame)) {
                    break;
                }
                requestClass1();
                continue;
            }
            byte[] asdu = frame.asdu();
            if (Iec101Codec.asduTypeId(asdu) != expectedType) {
                continue;
            }
            int cause = Iec101Codec.asduCause(asdu);
            if (cause != Iec101Types.COT_ACTIVATION_CON && cause != Iec101Types.COT_ACTIVATION_TERMINATION) {
                continue;
            }
            List<Iec101Value> values = Iec101Codec.decodeAsdu(asdu);
            for (Iec101Value value : values) {
                if (value.ioa() == ioa) {
                    return;
                }
            }
        }
        throw new IOException("IEC 101 command " + expectedType + " for IOA " + ioa + " was not confirmed");
    }

    private static boolean isNoData(Iec101Frame frame) {
        return frame.kind() == Iec101Frame.Kind.FIXED
                && Iec101Codec.functionCode(frame.control()) == Iec101Types.FC_NO_DATA;
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }
}
