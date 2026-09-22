package com.ispf.driver.lorawan.codec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semtech UDP packet-forwarder codec (gateway ↔ network server, protocol version 2).
 * <p>
 * Identifiers: {@code PUSH_DATA=0x00}, {@code PUSH_ACK=0x01}, {@code PULL_DATA=0x02},
 * {@code PULL_RESP=0x03}, {@code PULL_ACK=0x04}. PUSH_DATA carries an 8-byte gateway EUI
 * then a JSON object; PUSH_ACK is version + token + identifier only.
 */
public final class LorawanSemtechCodec {

    public static final int PROTOCOL_VERSION = 0x02;
    public static final int PUSH_DATA = 0x00;
    public static final int PUSH_ACK = 0x01;
    public static final int PULL_DATA = 0x02;
    public static final int PULL_RESP = 0x03;
    public static final int PULL_ACK = 0x04;

    private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern RSSI = Pattern.compile("\"rssi\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern FREQ = Pattern.compile("\"freq\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern DEVEUI = Pattern.compile("\"deveui\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA = Pattern.compile("\"data\"\\s*:\\s*\"([^\"]*)\"");

    private LorawanSemtechCodec() {
    }

    /** PUSH_ACK for {@code token}: version, token hi/lo, {@code 0x01}. */
    public static byte[] encodePushAck(int token) {
        return new byte[]{
                (byte) PROTOCOL_VERSION,
                (byte) ((token >> 8) & 0xFF),
                (byte) (token & 0xFF),
                (byte) PUSH_ACK
        };
    }

    public static byte[] encodePullAck(int token) {
        return new byte[]{
                (byte) PROTOCOL_VERSION,
                (byte) ((token >> 8) & 0xFF),
                (byte) (token & 0xFF),
                (byte) PULL_ACK
        };
    }

    public static byte[] encodePushData(int token, byte[] gatewayEui, String json) {
        byte[] eui = gatewayEui == null ? new byte[8] : gatewayEui;
        if (eui.length != 8) {
            throw new IllegalArgumentException("Gateway EUI must be 8 bytes");
        }
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + 8 + body.length);
        buf.put((byte) PROTOCOL_VERSION);
        buf.putShort((short) (token & 0xFFFF));
        buf.put((byte) PUSH_DATA);
        buf.put(eui);
        buf.put(body);
        return buf.array();
    }

    public static byte[] encodePullData(int token, byte[] gatewayEui) {
        byte[] eui = gatewayEui == null ? new byte[8] : gatewayEui;
        if (eui.length != 8) {
            throw new IllegalArgumentException("Gateway EUI must be 8 bytes");
        }
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.put((byte) PROTOCOL_VERSION);
        buf.putShort((short) (token & 0xFFFF));
        buf.put((byte) PULL_DATA);
        buf.put(eui);
        return buf.array();
    }

    public static byte[] encodePullResp(int token, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + body.length);
        buf.put((byte) PROTOCOL_VERSION);
        buf.putShort((short) (token & 0xFFFF));
        buf.put((byte) PULL_RESP);
        buf.put(body);
        return buf.array();
    }

    public static SemtechPacket decode(byte[] datagram) {
        if (datagram == null || datagram.length < 4) {
            throw new IllegalArgumentException("Semtech datagram too short");
        }
        int version = datagram[0] & 0xFF;
        if (version != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported Semtech protocol version: " + version);
        }
        int token = ((datagram[1] & 0xFF) << 8) | (datagram[2] & 0xFF);
        int identifier = datagram[3] & 0xFF;
        return switch (identifier) {
            case PUSH_DATA -> {
                if (datagram.length < 12) {
                    throw new IllegalArgumentException("PUSH_DATA missing gateway EUI");
                }
                byte[] eui = new byte[8];
                System.arraycopy(datagram, 4, eui, 0, 8);
                String json = new String(datagram, 12, datagram.length - 12, StandardCharsets.UTF_8);
                yield new SemtechPacket(identifier, token, euiHex(eui), json);
            }
            case PULL_DATA -> {
                if (datagram.length < 12) {
                    throw new IllegalArgumentException("PULL_DATA missing gateway EUI");
                }
                byte[] eui = new byte[8];
                System.arraycopy(datagram, 4, eui, 0, 8);
                yield new SemtechPacket(identifier, token, euiHex(eui), "");
            }
            case PUSH_ACK, PULL_ACK -> new SemtechPacket(identifier, token, "", "");
            case PULL_RESP -> {
                String json = datagram.length > 4
                        ? new String(datagram, 4, datagram.length - 4, StandardCharsets.UTF_8)
                        : "";
                yield new SemtechPacket(identifier, token, "", json);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown Semtech identifier: 0x" + Integer.toHexString(identifier));
        };
    }

    public static Uplink parseUplinkJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Empty Semtech JSON payload");
        }
        String trimmed = json.trim();
        float value = 0f;
        boolean hasValue = false;
        Matcher valueMatch = VALUE.matcher(trimmed);
        if (valueMatch.find()) {
            value = Float.parseFloat(valueMatch.group(1));
            hasValue = true;
        }
        double rssi = -90.0;
        Matcher rssiMatch = RSSI.matcher(trimmed);
        if (rssiMatch.find()) {
            rssi = Double.parseDouble(rssiMatch.group(1));
        }
        double freq = 0.0;
        Matcher freqMatch = FREQ.matcher(trimmed);
        if (freqMatch.find()) {
            freq = Double.parseDouble(freqMatch.group(1));
        }
        String deveui = "";
        Matcher deveuiMatch = DEVEUI.matcher(trimmed);
        if (deveuiMatch.find()) {
            deveui = normalizeDevEui(deveuiMatch.group(1));
        }
        String data = "";
        Matcher dataMatch = DATA.matcher(trimmed);
        if (dataMatch.find()) {
            data = dataMatch.group(1);
            if (!hasValue && !data.isBlank()) {
                value = decodeNumericData(data);
            }
        }
        return new Uplink(value, rssi, freq, deveui, data, trimmed);
    }

    public static String downlinkJson(String deveui, float value) {
        return "{\"txpk\":{\"deveui\":\"" + deveui + "\",\"value\":" + Float.toString(value) + "}}";
    }

    public static byte[] euiFromHex(String hex) {
        String clean = hex == null ? "" : hex.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
        if (clean.startsWith("0X")) {
            clean = clean.substring(2);
        }
        if (clean.length() != 16 || !clean.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException("Gateway EUI must be 8 hex bytes: " + hex);
        }
        byte[] out = new byte[8];
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static String euiHex(byte[] eui) {
        StringBuilder sb = new StringBuilder(16);
        for (byte b : eui) {
            sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
        }
        return sb.toString();
    }

    public static String normalizeDevEui(String raw) {
        String hex = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
        if (hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        return hex;
    }

    private static float decodeNumericData(String data) {
        try {
            return Float.parseFloat(data.trim());
        } catch (NumberFormatException ignored) {
            return 0f;
        }
    }

    /**
     * Decoded Semtech UDP packet. Gateway EUI and JSON are strings (no {@code byte[]} components).
     */
    public record SemtechPacket(int identifier, int token, String gatewayEuiHex, String json) {
    }

    public record Uplink(float value, double rssi, double freq, String deveui, String data, String raw) {
    }
}
