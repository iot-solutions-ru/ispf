package com.ispf.driver.ansic12.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * TCP client for the ANSI C12 lab subset (logon + standard table read/write).
 */
public final class AnsiC12LabClient implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final int timeoutMs;
    private boolean loggedOn;

    public AnsiC12LabClient(String host, int port, int timeoutMs) throws IOException {
        this.timeoutMs = timeoutMs;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public void logon(String user, String password) throws IOException {
        byte[] request = AnsiC12LabCodec.encodeRequest(
                AnsiC12LabCodec.SVC_LOGON,
                AnsiC12LabCodec.logonPayload(user, password)
        );
        writeFully(request);
        AnsiC12LabCodec.ParsedFrame response = readFrame();
        ensureOk(response, AnsiC12LabCodec.SVC_LOGON);
        loggedOn = true;
    }

    public byte[] readTable(int tableId) throws IOException {
        requireLogon();
        byte[] request = AnsiC12LabCodec.encodeRequest(
                AnsiC12LabCodec.SVC_READ_TABLE,
                AnsiC12LabCodec.readTablePayload(tableId)
        );
        writeFully(request);
        AnsiC12LabCodec.ParsedFrame response = readFrame();
        ensureOk(response, AnsiC12LabCodec.SVC_READ_TABLE);
        byte[] payload = response.payload();
        if (payload.length < 1) {
            throw new IOException("ANSI C12-lab read response missing ack");
        }
        // response payload: ack already checked via ensureOk layout — ensureOk expects ack as first byte
        // After ensureOk, payload[0] is ack; data follows.
        byte[] data = new byte[payload.length - 1];
        System.arraycopy(payload, 1, data, 0, data.length);
        return data;
    }

    public void writeTable(int tableId, byte[] data) throws IOException {
        requireLogon();
        byte[] request = AnsiC12LabCodec.encodeRequest(
                AnsiC12LabCodec.SVC_WRITE_TABLE,
                AnsiC12LabCodec.writeTablePayload(tableId, data)
        );
        writeFully(request);
        AnsiC12LabCodec.ParsedFrame response = readFrame();
        ensureOk(response, AnsiC12LabCodec.SVC_WRITE_TABLE);
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    public boolean isLoggedOn() {
        return loggedOn && isConnected();
    }

    @Override
    public void close() throws IOException {
        loggedOn = false;
        socket.close();
    }

    public static String asciiOrHex(byte[] data) {
        boolean printable = true;
        for (byte value : data) {
            int unsigned = value & 0xFF;
            if (unsigned != 0 && (unsigned < 0x20 || unsigned > 0x7E)) {
                printable = false;
                break;
            }
        }
        if (printable) {
            int end = data.length;
            while (end > 0 && data[end - 1] == 0) {
                end--;
            }
            return new String(data, 0, end, StandardCharsets.US_ASCII);
        }
        return HexFormat.of().formatHex(data);
    }

    private void requireLogon() throws IOException {
        if (!loggedOn) {
            throw new IOException("ANSI C12-lab requires logon before table access");
        }
    }

    private void ensureOk(AnsiC12LabCodec.ParsedFrame response, byte expectedService) throws IOException {
        if (!response.isResponse()) {
            throw new IOException("ANSI C12-lab expected response frame");
        }
        if (response.service() != expectedService) {
            throw new IOException("ANSI C12-lab unexpected service 0x"
                    + Integer.toHexString(response.service() & 0xFF));
        }
        byte[] payload = response.payload();
        if (payload.length < 1 || payload[0] != AnsiC12LabCodec.ACK_OK) {
            throw new IOException("ANSI C12-lab service rejected (ack="
                    + (payload.length == 0 ? "missing" : payload[0]) + ")");
        }
    }

    private void writeFully(byte[] data) throws IOException {
        out.write(data);
        out.flush();
    }

    private AnsiC12LabCodec.ParsedFrame readFrame() throws IOException {
        int stp = in.read();
        if (stp < 0) {
            throw new EOFException("ANSI C12-lab EOF");
        }
        if ((byte) stp != AnsiC12LabCodec.STP) {
            throw new IOException("ANSI C12-lab expected STP 0xEE, got 0x" + Integer.toHexString(stp));
        }
        byte identity = readByte();
        byte ctrl = readByte();
        int length = ((readByte() & 0xFF) << 8) | (readByte() & 0xFF);
        byte[] serviceAndPayload = in.readNBytes(length);
        if (serviceAndPayload.length != length) {
            throw new EOFException("ANSI C12-lab truncated payload");
        }
        byte crcLo = readByte();
        byte crcHi = readByte();
        byte[] frame = new byte[5 + length + 2];
        frame[0] = AnsiC12LabCodec.STP;
        frame[1] = identity;
        frame[2] = ctrl;
        frame[3] = (byte) ((length >>> 8) & 0xFF);
        frame[4] = (byte) (length & 0xFF);
        System.arraycopy(serviceAndPayload, 0, frame, 5, length);
        frame[5 + length] = crcLo;
        frame[6 + length] = crcHi;
        return AnsiC12LabCodec.parse(frame);
    }

    private byte readByte() throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new EOFException("ANSI C12-lab EOF");
        }
        return (byte) value;
    }
}
