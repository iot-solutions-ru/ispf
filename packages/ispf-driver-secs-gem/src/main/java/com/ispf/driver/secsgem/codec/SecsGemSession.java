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
 * Synchronous HSMS session: Select.req / Select.rsp, then S1F13/S1F14 and GEM data messages.
 */
public final class SecsGemSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final int sessionId;
    private final int timeoutMs;
    private final AtomicInteger systemBytes = new AtomicInteger(1);
    private boolean selected;

    public SecsGemSession(String host, int port, int sessionId, int timeoutMs) throws IOException {
        this.sessionId = sessionId & 0xFFFF;
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
        writeFully(HsmsCodec.encodeSelectReq(tx));
        HsmsCodec.HsmsMessage rsp = readMessage();
        if (!rsp.isSelectRsp() || rsp.systemBytes() != tx) {
            throw new IOException(
                    "HSMS expected Select.rsp with systemBytes=" + tx + ", got SType=" + rsp.sType()
                            + " systemBytes=" + rsp.systemBytes());
        }
        selected = true;
    }

    private void establishCommunications() throws IOException {
        requireSelected();
        int tx = nextSystemBytes();
        byte[] body = Secs2Codec.encodeEmptyList();
        writeFully(HsmsCodec.encodeData(
                sessionId, SecsGemTypes.STREAM_1, SecsGemTypes.S1F13, true, tx, body));
        HsmsCodec.HsmsMessage rsp = awaitData(SecsGemTypes.STREAM_1, SecsGemTypes.S1F14, tx);
        if (rsp.body().length == 0) {
            throw new IOException("HSMS empty S1F14");
        }
    }

    /**
     * S1F1 Are You There → S1F2 On Line Data (model/softrev as ASCII).
     */
    public Map<String, String> areYouThere() throws IOException {
        requireSelected();
        int tx = nextSystemBytes();
        writeFully(HsmsCodec.encodeData(
                sessionId, SecsGemTypes.STREAM_1, SecsGemTypes.S1F1, true, tx,
                Secs2Codec.encodeEmptyList()));
        HsmsCodec.HsmsMessage rsp = awaitData(SecsGemTypes.STREAM_1, SecsGemTypes.S1F2, tx);
        Secs2Codec.Item root = Secs2Codec.parse(rsp.body());
        Map<String, String> result = new LinkedHashMap<>();
        result.put("online", "true");
        if (root.isList() && root.children().size() >= 2) {
            result.put("mdln", nullToEmpty(root.children().get(0).ascii()));
            result.put("softrev", nullToEmpty(root.children().get(1).ascii()));
        } else if (root.isList() && root.children().size() == 1) {
            result.put("mdln", nullToEmpty(root.children().get(0).ascii()));
            result.put("softrev", "");
        } else {
            result.put("mdln", "");
            result.put("softrev", "");
        }
        return result;
    }

    /**
     * S2F13 Equipment status request for one or more VIDs → S2F14 values.
     */
    public Map<Long, Double> readVids(List<Long> vids) throws IOException {
        requireSelected();
        List<byte[]> items = new ArrayList<>();
        for (Long vid : vids) {
            items.add(Secs2Codec.encodeU4(vid));
        }
        int tx = nextSystemBytes();
        writeFully(HsmsCodec.encodeData(
                sessionId, SecsGemTypes.STREAM_2, SecsGemTypes.S2F13, true, tx,
                Secs2Codec.encodeList(items)));
        HsmsCodec.HsmsMessage rsp = awaitData(SecsGemTypes.STREAM_2, SecsGemTypes.S2F14, tx);
        Secs2Codec.Item root = Secs2Codec.parse(rsp.body());
        Map<Long, Double> values = new LinkedHashMap<>();
        if (!root.isList()) {
            throw new IOException("HSMS S2F14 expected LIST");
        }
        int n = Math.min(vids.size(), root.children().size());
        for (int i = 0; i < n; i++) {
            Secs2Codec.Item child = root.children().get(i);
            double numeric = child.format() == Secs2Codec.FORMAT_F4
                    ? child.numeric()
                    : child.unsigned();
            values.put(vids.get(i), numeric);
        }
        return values;
    }

    /**
     * S6F1-style status read: empty request → single U1/U4/F4/ASCII status value.
     */
    public String readStatus() throws IOException {
        requireSelected();
        int tx = nextSystemBytes();
        writeFully(HsmsCodec.encodeData(
                sessionId, SecsGemTypes.STREAM_6, SecsGemTypes.S6F1, true, tx,
                Secs2Codec.encodeEmptyList()));
        HsmsCodec.HsmsMessage rsp = awaitData(SecsGemTypes.STREAM_6, 2, tx);
        Secs2Codec.Item root = Secs2Codec.parse(rsp.body());
        if (root.ascii() != null) {
            return root.ascii();
        }
        if (root.isList() && !root.children().isEmpty()) {
            Secs2Codec.Item first = root.children().get(0);
            if (first.ascii() != null) {
                return first.ascii();
            }
            return String.valueOf(first.format() == Secs2Codec.FORMAT_F4 ? first.numeric() : first.unsigned());
        }
        return String.valueOf(root.format() == Secs2Codec.FORMAT_F4 ? root.numeric() : root.unsigned());
    }

    /**
     * S2F41 Host Command Send — {@code rcmd} as ASCII RCMD, expects HCACK in S2F42.
     */
    public int sendRemoteCommand(String rcmd) throws IOException {
        requireSelected();
        List<byte[]> bodyItems = List.of(
                Secs2Codec.encodeAscii(rcmd),
                Secs2Codec.encodeEmptyList()
        );
        int tx = nextSystemBytes();
        writeFully(HsmsCodec.encodeData(
                sessionId, SecsGemTypes.STREAM_2, SecsGemTypes.S2F41, true, tx,
                Secs2Codec.encodeList(bodyItems)));
        HsmsCodec.HsmsMessage rsp = awaitData(SecsGemTypes.STREAM_2, 42, tx);
        Secs2Codec.Item root = Secs2Codec.parse(rsp.body());
        if (root.isList() && !root.children().isEmpty()) {
            return (int) root.children().get(0).unsigned();
        }
        if (root.format() == Secs2Codec.FORMAT_U1) {
            return (int) root.unsigned();
        }
        return 0;
    }

    private void requireSelected() throws IOException {
        if (!selected) {
            throw new IOException("HSMS not selected; Select.rsp required before data");
        }
    }

    private HsmsCodec.HsmsMessage awaitData(int stream, int function, int expectedSystemBytes) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
            socket.setSoTimeout(remaining);
            HsmsCodec.HsmsMessage msg = readMessage();
            if (msg.sType() == SecsGemTypes.STYPE_LINKTEST_REQ) {
                writeFully(HsmsCodec.encodeControl(sessionId, msg.systemBytes(),
                        SecsGemTypes.STYPE_LINKTEST_RSP));
                continue;
            }
            if (!msg.isData()) {
                continue;
            }
            if (msg.stream() == stream && msg.function() == function && msg.systemBytes() == expectedSystemBytes) {
                return msg;
            }
        }
        throw new IOException("HSMS timeout waiting for S" + stream + "F" + function);
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        try {
            if (selected) {
                writeFully(HsmsCodec.encodeControl(sessionId, nextSystemBytes(),
                        SecsGemTypes.STYPE_SEPARATE_REQ));
            }
        } catch (IOException ignored) {
            // best effort
        }
        selected = false;
        socket.close();
    }

    private int nextSystemBytes() {
        return systemBytes.getAndIncrement() & 0x7FFFFFFF;
    }

    private void writeFully(byte[] data) throws IOException {
        out.write(data);
        out.flush();
    }

    private HsmsCodec.HsmsMessage readMessage() throws IOException {
        byte[] lengthBytes = in.readNBytes(4);
        if (lengthBytes.length != 4) {
            throw new EOFException("HSMS EOF in length");
        }
        int length = ByteBuffer.wrap(lengthBytes).getInt();
        if (length < 10) {
            throw new IOException("HSMS invalid length " + length);
        }
        byte[] rest = in.readNBytes(length);
        if (rest.length != length) {
            throw new EOFException("HSMS truncated header/body");
        }
        byte[] frame = new byte[4 + length];
        System.arraycopy(lengthBytes, 0, frame, 0, 4);
        System.arraycopy(rest, 0, frame, 4, length);
        return HsmsCodec.parse(frame);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
