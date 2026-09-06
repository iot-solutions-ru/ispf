package com.ispf.driver.secsgem.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Synchronous HSMS-lab session: Select + S1F13/S1F14, then S1F1 / S2F13 / S2F41.
 */
public final class SecsGemLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final int sessionId;
    private final int timeoutMs;
    private final AtomicInteger systemBytes = new AtomicInteger(1);

    public SecsGemLabSession(String host, int port, int sessionId, int timeoutMs) throws IOException {
        this.sessionId = sessionId;
        this.timeoutMs = timeoutMs;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        select();
        establishCommunications();
    }

    private void select() throws IOException {
        int tx = nextSystemBytes();
        writeFully(HsmsLabCodec.encodeControl(sessionId, tx, SecsGemLabTypes.STYPE_SELECT_REQ));
        HsmsLabCodec.HsmsMessage rsp = readMessage();
        if (!rsp.isSelectRsp()) {
            throw new IOException("HSMS-lab expected Select.rsp, got SType=" + rsp.sType());
        }
    }

    private void establishCommunications() throws IOException {
        int tx = nextSystemBytes();
        byte[] body = Secs2LabCodec.encodeEmptyList();
        writeFully(HsmsLabCodec.encodeData(
                sessionId, SecsGemLabTypes.STREAM_1, SecsGemLabTypes.S1F13, true, tx, body));
        HsmsLabCodec.HsmsMessage rsp = awaitData(SecsGemLabTypes.STREAM_1, SecsGemLabTypes.S1F14, tx);
        // COMMACK in first list element if present — lab accepts any S1F14
        if (rsp.body().length == 0) {
            throw new IOException("HSMS-lab empty S1F14");
        }
    }

    /**
     * S1F1 Are You There → S1F2 On Line Data (model/softrev as ASCII).
     */
    public Map<String, String> areYouThere() throws IOException {
        int tx = nextSystemBytes();
        writeFully(HsmsLabCodec.encodeData(
                sessionId, SecsGemLabTypes.STREAM_1, SecsGemLabTypes.S1F1, true, tx,
                Secs2LabCodec.encodeEmptyList()));
        HsmsLabCodec.HsmsMessage rsp = awaitData(SecsGemLabTypes.STREAM_1, SecsGemLabTypes.S1F2, tx);
        Secs2LabCodec.Item root = Secs2LabCodec.parse(rsp.body());
        Map<String, String> out = new LinkedHashMap<>();
        out.put("online", "true");
        if (root.isList() && root.children().size() >= 2) {
            out.put("mdln", nullToEmpty(root.children().get(0).ascii()));
            out.put("softrev", nullToEmpty(root.children().get(1).ascii()));
        } else if (root.isList() && root.children().size() == 1) {
            out.put("mdln", nullToEmpty(root.children().get(0).ascii()));
            out.put("softrev", "");
        } else {
            out.put("mdln", "LAB");
            out.put("softrev", "");
        }
        return out;
    }

    /**
     * S2F13 Equipment status request for one or more VIDs → S2F14 values.
     */
    public Map<Long, Double> readVids(List<Long> vids) throws IOException {
        List<byte[]> items = new ArrayList<>();
        for (Long vid : vids) {
            items.add(Secs2LabCodec.encodeU4(vid));
        }
        int tx = nextSystemBytes();
        writeFully(HsmsLabCodec.encodeData(
                sessionId, SecsGemLabTypes.STREAM_2, SecsGemLabTypes.S2F13, true, tx,
                Secs2LabCodec.encodeList(items)));
        HsmsLabCodec.HsmsMessage rsp = awaitData(SecsGemLabTypes.STREAM_2, SecsGemLabTypes.S2F14, tx);
        Secs2LabCodec.Item root = Secs2LabCodec.parse(rsp.body());
        Map<Long, Double> values = new LinkedHashMap<>();
        if (!root.isList()) {
            throw new IOException("HSMS-lab S2F14 expected LIST");
        }
        int n = Math.min(vids.size(), root.children().size());
        for (int i = 0; i < n; i++) {
            Secs2LabCodec.Item child = root.children().get(i);
            double numeric = child.format() == Secs2LabCodec.FORMAT_F4
                    ? child.numeric()
                    : child.unsigned();
            values.put(vids.get(i), numeric);
        }
        return values;
    }

    /**
     * Lab S6F1-style status read: empty request → single U1/U4/F4/ASCII status value.
     */
    public String readStatus() throws IOException {
        int tx = nextSystemBytes();
        writeFully(HsmsLabCodec.encodeData(
                sessionId, SecsGemLabTypes.STREAM_6, SecsGemLabTypes.S6F1, true, tx,
                Secs2LabCodec.encodeEmptyList()));
        HsmsLabCodec.HsmsMessage rsp = awaitData(SecsGemLabTypes.STREAM_6, 2, tx);
        Secs2LabCodec.Item root = Secs2LabCodec.parse(rsp.body());
        if (root.ascii() != null) {
            return root.ascii();
        }
        if (root.isList() && !root.children().isEmpty()) {
            Secs2LabCodec.Item first = root.children().get(0);
            if (first.ascii() != null) {
                return first.ascii();
            }
            return String.valueOf(first.format() == Secs2LabCodec.FORMAT_F4 ? first.numeric() : first.unsigned());
        }
        return String.valueOf(root.format() == Secs2LabCodec.FORMAT_F4 ? root.numeric() : root.unsigned());
    }

    /**
     * S2F41 Host Command Send — {@code rcmd} as ASCII RCMD, expects HCACK in S2F42.
     */
    public int sendRemoteCommand(String rcmd) throws IOException {
        List<byte[]> bodyItems = List.of(
                Secs2LabCodec.encodeAscii(rcmd),
                Secs2LabCodec.encodeEmptyList()
        );
        int tx = nextSystemBytes();
        writeFully(HsmsLabCodec.encodeData(
                sessionId, SecsGemLabTypes.STREAM_2, SecsGemLabTypes.S2F41, true, tx,
                Secs2LabCodec.encodeList(bodyItems)));
        HsmsLabCodec.HsmsMessage rsp = awaitData(SecsGemLabTypes.STREAM_2, 42, tx);
        Secs2LabCodec.Item root = Secs2LabCodec.parse(rsp.body());
        if (root.isList() && !root.children().isEmpty()) {
            return (int) root.children().get(0).unsigned();
        }
        if (root.format() == Secs2LabCodec.FORMAT_U1) {
            return (int) root.unsigned();
        }
        return 0;
    }

    private HsmsLabCodec.HsmsMessage awaitData(int stream, int function, int systemBytes) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
            socket.setSoTimeout(remaining);
            HsmsLabCodec.HsmsMessage msg = readMessage();
            if (msg.sType() == SecsGemLabTypes.STYPE_LINKTEST_REQ) {
                writeFully(HsmsLabCodec.encodeControl(sessionId, msg.systemBytes(),
                        SecsGemLabTypes.STYPE_LINKTEST_RSP));
                continue;
            }
            if (!msg.isData()) {
                continue;
            }
            if (msg.stream() == stream && msg.function() == function && msg.systemBytes() == systemBytes) {
                return msg;
            }
        }
        throw new IOException("HSMS-lab timeout waiting for S" + stream + "F" + function);
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        try {
            writeFully(HsmsLabCodec.encodeControl(sessionId, nextSystemBytes(),
                    SecsGemLabTypes.STYPE_SEPARATE_REQ));
        } catch (IOException ignored) {
            // best effort
        }
        socket.close();
    }

    private int nextSystemBytes() {
        return systemBytes.getAndIncrement() & 0x7FFFFFFF;
    }

    private void writeFully(byte[] data) throws IOException {
        out.write(data);
        out.flush();
    }

    private HsmsLabCodec.HsmsMessage readMessage() throws IOException {
        byte[] lengthBytes = in.readNBytes(4);
        if (lengthBytes.length != 4) {
            throw new EOFException("HSMS-lab EOF in length");
        }
        int length = ByteBuffer.wrap(lengthBytes).getInt();
        if (length < 10) {
            throw new IOException("HSMS-lab invalid length " + length);
        }
        byte[] rest = in.readNBytes(length);
        if (rest.length != length) {
            throw new EOFException("HSMS-lab truncated header/body");
        }
        byte[] frame = new byte[4 + length];
        System.arraycopy(lengthBytes, 0, frame, 0, 4);
        System.arraycopy(rest, 0, frame, 4, length);
        return HsmsLabCodec.parse(frame);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
