package com.ispf.driver.iec104.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Iec104Connection implements AutoCloseable {

    private static final int START = 0x68;
    private static final byte[] STARTDT_ACT = {0x07, 0x00, 0x00, 0x00};
    private static final byte[] STARTDT_CON = {0x0b, 0x00, 0x00, 0x00};
    private static final byte[] STOPDT_ACT = {0x13, 0x00, 0x00, 0x00};
    private static final byte[] STOPDT_CON = {0x23, 0x00, 0x00, 0x00};
    private static final byte[] TESTFR_ACT = {0x43, 0x00, 0x00, 0x00};
    private static final byte[] TESTFR_CON = {(byte) 0x83, 0x00, 0x00, 0x00};

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Iec104ConnectionListener listener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean dataTransferActive = new AtomicBoolean();
    private int sendSequence;
    private int receiveSequence;
    private final ArrayBlockingQueue<Iec104Asdu> commandReplies = new ArrayBlockingQueue<>(4);
    private Thread readerThread;

    public static Iec104Connection connect(
            InetAddress address,
            int port,
            int timeoutMs,
            Iec104ConnectionListener listener
    ) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(address, port), timeoutMs);
        socket.setTcpNoDelay(true);
        return new Iec104Connection(socket, listener);
    }

    public Iec104Connection(Socket socket, Iec104ConnectionListener listener) throws IOException {
        this(socket, listener, true);
    }

    /**
     * @param startReaderImmediately false when the listener is not fully wired yet (server accept path)
     */
    Iec104Connection(Socket socket, Iec104ConnectionListener listener, boolean startReaderImmediately)
            throws IOException {
        this.socket = socket;
        this.listener = listener;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        if (startReaderImmediately) {
            startReader();
        }
    }

    void startReaderIfNeeded() {
        synchronized (this) {
            if (readerThread == null && !closed.get()) {
                startReader();
            }
        }
    }

    public void startDataTransfer() throws IOException {
        sendUFrame(STARTDT_ACT);
    }

    public void readCommand(int commonAddress, int ioa) throws IOException {
        sendAsdu(Iec104Asdu.single(Iec104Type.C_RD_NA_1, Iec104Cause.REQUEST, commonAddress,
                new Iec104Value(ioa, Iec104Type.C_RD_NA_1, 0.0, "GOOD")));
    }

    public void interrogationCommand(int commonAddress) throws IOException {
        sendAsdu(Iec104Asdu.single(Iec104Type.C_IC_NA_1, Iec104Cause.ACTIVATION, commonAddress,
                new Iec104Value(0, Iec104Type.C_IC_NA_1, 20.0, "GOOD")));
    }

    public void singleCommand(int commonAddress, int ioa, boolean on) throws IOException {
        command(commonAddress, ioa, on, false, true);
    }

    public void singleCommandSelect(int commonAddress, int ioa, boolean on) throws IOException {
        command(commonAddress, ioa, on, true, true);
    }

    public void doubleCommandSelect(int commonAddress, int ioa, boolean on) throws IOException {
        command(commonAddress, ioa, on, true, false);
    }

    private void command(int commonAddress, int ioa, boolean on, boolean select, boolean single) throws IOException {
        String quality = select ? "SELECT" : "GOOD";
        if (single) {
            sendAsdu(Iec104Asdu.single(Iec104Type.C_SC_NA_1, Iec104Cause.ACTIVATION, commonAddress,
                    new Iec104Value(ioa, Iec104Type.C_SC_NA_1, on, quality)));
        } else {
            int dcs = on ? 2 : 1;
            sendAsdu(Iec104Asdu.single(Iec104Type.C_DC_NA_1, Iec104Cause.ACTIVATION, commonAddress,
                    new Iec104Value(ioa, Iec104Type.C_DC_NA_1, dcs, quality)));
        }
    }

    public void clearCommandReplies() {
        commandReplies.clear();
    }

    public Iec104Asdu awaitCommandReply(
            long timeoutMs,
            int typeId,
            int commonAddress,
            int ioa,
            boolean select
    ) throws IOException {
        try {
            Iec104Asdu reply = commandReplies.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (reply == null) {
                throw new IOException("IEC104 command confirmation timed out");
            }
            if ((reply.cause() & 0x40) != 0) {
                throw new IOException("IEC104 command rejected");
            }
            if (reply.typeId() != typeId || reply.commonAddress() != commonAddress || reply.values().isEmpty()) {
                throw new IOException("IEC104 command confirmation does not match the request");
            }
            Iec104Value echoed = reply.values().get(0);
            boolean echoedSelect = "SELECT".equals(echoed.quality());
            if (echoed.ioa() != ioa || echoedSelect != select) {
                throw new IOException("IEC104 command confirmation does not match the request");
            }
            return reply;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("IEC104 command confirmation interrupted", ex);
        }
    }

    /**
     * Double command, direct execute. DCS is 2 (ON) or 1 (OFF); qualifier and select bit stay 0.
     */
    public void doubleCommand(int commonAddress, int ioa, boolean on) throws IOException {
        int dcs = on ? 2 : 1;
        sendAsdu(Iec104Asdu.single(Iec104Type.C_DC_NA_1, Iec104Cause.ACTIVATION, commonAddress,
                new Iec104Value(ioa, Iec104Type.C_DC_NA_1, dcs, "GOOD")));
    }

    public void setShortFloatSelect(int commonAddress, int ioa, double value) throws IOException {
        sendAsdu(Iec104Asdu.single(Iec104Type.C_SE_NC_1, Iec104Cause.ACTIVATION, commonAddress,
                new Iec104Value(ioa, Iec104Type.C_SE_NC_1, value, "SELECT")));
    }

    public void setNormalizedSelect(int commonAddress, int ioa, double value) throws IOException {
        sendAsdu(Iec104Asdu.single(Iec104Type.C_SE_NA_1, Iec104Cause.ACTIVATION, commonAddress,
                new Iec104Value(ioa, Iec104Type.C_SE_NA_1, value, "SELECT")));
    }

    private static boolean isControlCommand(int typeId) {
        return typeId == Iec104Type.C_SC_NA_1
                || typeId == Iec104Type.C_DC_NA_1
                || typeId == Iec104Type.C_SE_NA_1
                || typeId == Iec104Type.C_SE_NC_1;
    }

    public void setShortFloatCommand(int commonAddress, int ioa, double value) throws IOException {
        sendAsdu(Iec104Asdu.single(Iec104Type.C_SE_NC_1, Iec104Cause.ACTIVATION, commonAddress,
                new Iec104Value(ioa, Iec104Type.C_SE_NC_1, value, "GOOD")));
    }

    public void setNormalizedValueCommand(int commonAddress, int ioa, double value) throws IOException {
        sendAsdu(Iec104Asdu.single(Iec104Type.C_SE_NA_1, Iec104Cause.ACTIVATION, commonAddress,
                new Iec104Value(ioa, Iec104Type.C_SE_NA_1, value, "GOOD")));
    }

    public synchronized void sendAsdu(Iec104Asdu asdu) throws IOException {
        byte[] asduBytes = Iec104Codec.encodeAsdu(asdu);
        if (asduBytes.length > 249) {
            throw new IOException("IEC104 ASDU too long: " + asduBytes.length);
        }
        byte[] apdu = new byte[asduBytes.length + 6];
        apdu[0] = START;
        apdu[1] = (byte) (asduBytes.length + 4);
        writeControl(apdu, 2);
        System.arraycopy(asduBytes, 0, apdu, 6, asduBytes.length);
        out.write(apdu);
        out.flush();
        sendSequence = (sendSequence + 1) & 0x7fff;
    }

    public boolean isOpen() {
        return !closed.get() && socket.isConnected() && !socket.isClosed();
    }

    public String remoteAddress() {
        InetAddress address = socket.getInetAddress();
        return address == null ? "" : address.getHostAddress();
    }

    public int originatorAddress() {
        return 0;
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            socket.close();
        }
    }

    private void startReader() {
        readerThread = new Thread(this::readLoop, "iec104-connection-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        IOException failure = null;
        try {
            while (!closed.get()) {
                int start = in.read();
                if (start < 0) {
                    throw new EOFException("IEC104 connection closed by peer");
                }
                if (start != START) {
                    continue;
                }
                int length = in.read();
                if (length < 4) {
                    throw new IOException("Invalid IEC104 APDU length: " + length);
                }
                byte[] body = readFully(length);
                handleApdu(body);
            }
        } catch (IOException e) {
            failure = e;
        } finally {
            boolean notify = closed.compareAndSet(false, true);
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort close
            }
            if (notify) {
                listener.onConnectionClosed(this, failure);
            }
        }
    }

    private byte[] readFully(int length) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(bytes, offset, length - offset);
            if (read < 0) {
                throw new EOFException("IEC104 APDU truncated");
            }
            offset += read;
        }
        return bytes;
    }

    private void handleApdu(byte[] body) throws IOException {
        int control0 = body[0] & 0xff;
        if ((control0 & 0x01) == 0) {
            receiveSequence = (receiveSequence + 1) & 0x7fff;
            byte[] asduBytes = new byte[body.length - 4];
            System.arraycopy(body, 4, asduBytes, 0, asduBytes.length);
            Iec104Asdu asdu = Iec104Codec.decodeAsdu(asduBytes);
            int cause = asdu.cause() & 0x3f;
            if (cause == Iec104Cause.ACTIVATION_CONFIRMATION && isControlCommand(asdu.typeId())) {
                commandReplies.offer(asdu);
            }
            listener.onAsdu(this, asdu);
            return;
        }
        if ((control0 & 0x03) == 0x03) {
            handleUFrame(body);
        }
    }

    private void handleUFrame(byte[] body) throws IOException {
        if (matches(body, STARTDT_ACT)) {
            sendUFrame(STARTDT_CON);
            if (dataTransferActive.compareAndSet(false, true)) {
                listener.onDataTransferStateChanged(this, true);
            }
        } else if (matches(body, STARTDT_CON)) {
            if (dataTransferActive.compareAndSet(false, true)) {
                listener.onDataTransferStateChanged(this, true);
            }
        } else if (matches(body, STOPDT_ACT)) {
            sendUFrame(STOPDT_CON);
            if (dataTransferActive.compareAndSet(true, false)) {
                listener.onDataTransferStateChanged(this, false);
            }
        } else if (matches(body, TESTFR_ACT)) {
            sendUFrame(TESTFR_CON);
        }
    }

    private synchronized void sendUFrame(byte[] control) throws IOException {
        out.write(START);
        out.write(4);
        out.write(control);
        out.flush();
    }

    private void writeControl(byte[] apdu, int offset) {
        int send = sendSequence << 1;
        int receive = receiveSequence << 1;
        apdu[offset] = (byte) (send & 0xff);
        apdu[offset + 1] = (byte) ((send >>> 8) & 0xff);
        apdu[offset + 2] = (byte) (receive & 0xff);
        apdu[offset + 3] = (byte) ((receive >>> 8) & 0xff);
    }

    private static boolean matches(byte[] body, byte[] control) {
        return body.length >= 4 && List.of(0, 1, 2, 3).stream().allMatch(index -> body[index] == control[index]);
    }
}
