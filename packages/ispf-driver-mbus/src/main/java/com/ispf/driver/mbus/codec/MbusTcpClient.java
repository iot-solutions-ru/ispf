package com.ispf.driver.mbus.codec;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal M-Bus TCP client for REQ_UD2 polling and RSP_UD variable data records.
 */
public final class MbusTcpClient implements AutoCloseable {

    private static final int SHORT_FRAME_START = 0x10;
    private static final int LONG_FRAME_START = 0x68;
    private static final int FRAME_STOP = 0x16;
    private static final int SINGLE_CHAR_ACK = 0xE5;
    private static final int CONTROL_REQ_UD2 = 0x5B;
    private static final int CI_VARIABLE_DATA_LONG_HEADER = 0x72;

    private final String host;
    private final int port;
    private final int timeoutMs;
    private Socket socket;
    private DataInputStream input;
    private OutputStream output;

    public MbusTcpClient(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        input = new DataInputStream(socket.getInputStream());
        output = socket.getOutputStream();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public List<Record> read(int primaryAddress) throws IOException {
        if (!isConnected()) {
            throw new IOException("M-Bus TCP client is not connected");
        }
        sendReqUd2(primaryAddress);
        byte[] payload = readLongFrame();
        if ((payload[1] & 0xFF) != (primaryAddress & 0xFF)) {
            throw new IOException("M-Bus response address mismatch");
        }
        return parseVariableData(payload);
    }

    private void sendReqUd2(int primaryAddress) throws IOException {
        int address = primaryAddress & 0xFF;
        int checksum = (CONTROL_REQ_UD2 + address) & 0xFF;
        output.write(new byte[] {
                (byte) SHORT_FRAME_START,
                (byte) CONTROL_REQ_UD2,
                (byte) address,
                (byte) checksum,
                (byte) FRAME_STOP
        });
        output.flush();
    }

    private byte[] readLongFrame() throws IOException {
        int start = input.readUnsignedByte();
        if (start == SINGLE_CHAR_ACK) {
            throw new IOException("M-Bus meter returned ACK without data");
        }
        if (start != LONG_FRAME_START) {
            throw new IOException("Expected M-Bus long frame");
        }
        int length1 = input.readUnsignedByte();
        int length2 = input.readUnsignedByte();
        int repeatedStart = input.readUnsignedByte();
        if (length1 != length2 || repeatedStart != LONG_FRAME_START) {
            throw new IOException("Invalid M-Bus long frame header");
        }

        byte[] payload = new byte[length1];
        input.readFully(payload);
        int checksum = input.readUnsignedByte();
        int stop = input.readUnsignedByte();
        if (stop != FRAME_STOP) {
            throw new IOException("Invalid M-Bus frame stop byte");
        }
        int computed = 0;
        for (byte value : payload) {
            computed = (computed + (value & 0xFF)) & 0xFF;
        }
        if (checksum != computed) {
            throw new IOException("Invalid M-Bus frame checksum");
        }
        if (payload.length < 3) {
            throw new IOException("M-Bus payload is shorter than C/A/CI");
        }
        return payload;
    }

    private List<Record> parseVariableData(byte[] payload) throws IOException {
        int ci = payload[2] & 0xFF;
        if (ci != CI_VARIABLE_DATA_LONG_HEADER) {
            throw new IOException("Unsupported M-Bus CI field: 0x" + Integer.toHexString(ci));
        }
        int offset = 15; // C, A, CI plus the 12-byte variable-data long header.
        List<Record> records = new ArrayList<>();
        while (offset < payload.length) {
            int marker = payload[offset] & 0xFF;
            if (marker == 0x2F) {
                offset++;
                continue;
            }
            if (marker == 0x0F || marker == 0x1F) {
                break;
            }

            int dif = payload[offset++] & 0xFF;
            while ((dif & 0x80) != 0) {
                if (offset >= payload.length) {
                    throw new IOException("Truncated M-Bus DIFE");
                }
                int dife = payload[offset++] & 0xFF;
                if ((dife & 0x80) == 0) {
                    break;
                }
            }

            if (offset >= payload.length) {
                throw new IOException("Missing M-Bus VIF");
            }
            int vif = payload[offset++] & 0xFF;
            while ((vif & 0x80) != 0) {
                if (offset >= payload.length) {
                    throw new IOException("Truncated M-Bus VIFE");
                }
                int vife = payload[offset++] & 0xFF;
                if ((vife & 0x80) == 0) {
                    break;
                }
            }

            int dataLength = dataLength(dif & 0x0F);
            if (offset + dataLength > payload.length) {
                throw new IOException("Truncated M-Bus data record");
            }
            byte[] data = new byte[dataLength];
            System.arraycopy(payload, offset, data, 0, dataLength);
            offset += dataLength;

            Value value = decodeValue(dif & 0x0F, data);
            Unit unit = unit(vif & 0x7F);
            records.add(new Record(
                    unit.register(),
                    unit.unit(),
                    value.text(),
                    dif & 0xFF,
                    vif & 0xFF
            ));
        }
        return records;
    }

    private static int dataLength(int dataCode) throws IOException {
        return switch (dataCode) {
            case 0x0 -> 0;
            case 0x1 -> 1;
            case 0x2 -> 2;
            case 0x3 -> 3;
            case 0x4, 0x5 -> 4;
            case 0x6 -> 6;
            case 0x7 -> 8;
            case 0x9 -> 1;
            case 0xA -> 2;
            case 0xB -> 3;
            case 0xC -> 4;
            default -> throw new IOException("Unsupported M-Bus DIF data code: 0x"
                    + Integer.toHexString(dataCode));
        };
    }

    private static Value decodeValue(int dataCode, byte[] data) throws IOException {
        return switch (dataCode) {
            case 0x0 -> new Value("");
            case 0x1, 0x2, 0x3, 0x4, 0x6, 0x7 -> new Value(String.valueOf(littleEndianSigned(data)));
            case 0x9, 0xA, 0xB, 0xC -> new Value(String.valueOf(littleEndianBcd(data)));
            default -> throw new IOException("Unsupported M-Bus numeric data code: 0x"
                    + Integer.toHexString(dataCode));
        };
    }

    private static long littleEndianSigned(byte[] data) {
        if (data.length == 0) {
            return 0;
        }
        long value = 0;
        for (int i = 0; i < data.length; i++) {
            value |= (long) (data[i] & 0xFF) << (8 * i);
        }
        if (data.length == Long.BYTES) {
            return value;
        }
        int bits = data.length * 8;
        long signBit = 1L << (bits - 1);
        if ((value & signBit) != 0) {
            value -= 1L << bits;
        }
        return value;
    }

    private static long littleEndianBcd(byte[] data) {
        long multiplier = 1;
        long value = 0;
        for (byte datum : data) {
            int raw = datum & 0xFF;
            value += (raw & 0x0F) * multiplier;
            multiplier *= 10;
            value += ((raw >> 4) & 0x0F) * multiplier;
            multiplier *= 10;
        }
        return value;
    }

    private static Unit unit(int vif) {
        return switch (vif) {
            case 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 ->
                    new Unit("energy", "WATT_HOUR");
            case 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F ->
                    new Unit("energy", "JOULE");
            case 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17 ->
                    new Unit("volume", "CUBIC_METER");
            case 0x28, 0x29, 0x2A, 0x2B -> new Unit("power", "WATT");
            case 0x38, 0x39, 0x3A, 0x3B -> new Unit("flow", "CUBIC_METER_PER_HOUR");
            case 0x58, 0x59, 0x5A, 0x5B -> new Unit("flow_temperature", "CELSIUS");
            case 0x5C, 0x5D, 0x5E, 0x5F -> new Unit("return_temperature", "CELSIUS");
            default -> new Unit("vif_0x" + Integer.toHexString(vif).toUpperCase(Locale.ROOT), "");
        };
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
            socket = null;
            input = null;
            output = null;
        }
    }

    public record Record(String register, String unit, String value, int dif, int vif) {

        public boolean matches(String expected) {
            String normalized = expected.trim();
            String difVif = String.format(Locale.ROOT, "0x%02X-0x%02X", dif, vif);
            String arrayStyle = "[" + dif + "]-[" + vif + "]";
            return normalized.equalsIgnoreCase(register)
                    || normalized.equalsIgnoreCase(unit)
                    || normalized.equalsIgnoreCase(difVif)
                    || normalized.equalsIgnoreCase(arrayStyle);
        }
    }

    private record Unit(String register, String unit) {
    }

    private record Value(String text) {
    }
}
