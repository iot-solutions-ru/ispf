package com.ispf.driver.knx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

/**
 * Clean-room KNXnet/IP Tunneling + cEMI helpers from public KNXnet/IP / ISO 22510 documentation.
 * Subset: SEARCH, CONNECT, TUNNELLING_REQUEST/ACK, GroupValue_Read / GroupValue_Write (3-level GA).
 */
final class KnxnetIpCodec {

    static final int SERVICE_SEARCH_REQUEST = 0x0201;
    static final int SERVICE_SEARCH_RESPONSE = 0x0202;
    static final int SERVICE_CONNECT_REQUEST = 0x0205;
    static final int SERVICE_CONNECT_RESPONSE = 0x0206;
    static final int SERVICE_TUNNELLING_REQUEST = 0x0420;
    static final int SERVICE_TUNNELLING_ACK = 0x0421;

    static final byte CEMI_L_DATA_REQ = 0x11;
    static final byte CEMI_L_DATA_IND = 0x29;

    private KnxnetIpCodec() {
    }

    /** Encodes 3-level group address {@code main/middle/sub} (e.g. {@code 1/2/3}). */
    static int parseGroupAddress(String mapping) {
        String text = mapping.trim();
        String[] parts = text.split("/");
        if (parts.length != 3) {
            throw new IllegalArgumentException("KNX group address must be main/middle/sub, got: " + mapping);
        }
        int main = Integer.parseInt(parts[0].trim());
        int middle = Integer.parseInt(parts[1].trim());
        int sub = Integer.parseInt(parts[2].trim());
        if (main < 0 || main > 31 || middle < 0 || middle > 7 || sub < 0 || sub > 255) {
            throw new IllegalArgumentException("KNX group address out of range: " + mapping);
        }
        return (main << 11) | (middle << 8) | sub;
    }

    static String formatGroupAddress(int ga) {
        int main = (ga >> 11) & 0x1F;
        int middle = (ga >> 8) & 0x07;
        int sub = ga & 0xFF;
        return main + "/" + middle + "/" + sub;
    }

    static byte[] searchRequest(byte[] hpai) {
        ByteBuffer buf = ByteBuffer.allocate(6 + hpai.length).order(ByteOrder.BIG_ENDIAN);
        writeHeader(buf, SERVICE_SEARCH_REQUEST, 6 + hpai.length);
        buf.put(hpai);
        return buf.array();
    }

    static byte[] connectRequest(byte[] controlHpai, byte[] dataHpai) {
        byte[] cri = new byte[]{0x04, 0x04, 0x02, 0x00};
        int total = 6 + controlHpai.length + dataHpai.length + cri.length;
        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);
        writeHeader(buf, SERVICE_CONNECT_REQUEST, total);
        buf.put(controlHpai);
        buf.put(dataHpai);
        buf.put(cri);
        return buf.array();
    }

    static byte[] tunnellingRequest(int channelId, int sequence, byte[] cemi) {
        int total = 6 + 4 + cemi.length;
        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);
        writeHeader(buf, SERVICE_TUNNELLING_REQUEST, total);
        buf.put((byte) 0x04);
        buf.put((byte) (channelId & 0xFF));
        buf.put((byte) (sequence & 0xFF));
        buf.put((byte) 0x00);
        buf.put(cemi);
        return buf.array();
    }

    static byte[] tunnellingAck(int channelId, int sequence, int status) {
        ByteBuffer buf = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN);
        writeHeader(buf, SERVICE_TUNNELLING_ACK, 10);
        buf.put((byte) 0x04);
        buf.put((byte) (channelId & 0xFF));
        buf.put((byte) (sequence & 0xFF));
        buf.put((byte) (status & 0xFF));
        return buf.array();
    }

    static byte[] groupValueReadCemi(int groupAddress) {
        return new byte[]{
                CEMI_L_DATA_REQ,
                0x00,
                (byte) 0xBC,
                (byte) 0xE0,
                0x00, 0x00,
                (byte) ((groupAddress >> 8) & 0xFF),
                (byte) (groupAddress & 0xFF),
                0x01,
                0x00, 0x00
        };
    }

    static byte[] groupValueWriteCemi(int groupAddress, int valueByte) {
        int apci = 0x80 | (valueByte & 0x3F);
        return new byte[]{
                CEMI_L_DATA_REQ,
                0x00,
                (byte) 0xBC,
                (byte) 0xE0,
                0x00, 0x00,
                (byte) ((groupAddress >> 8) & 0xFF),
                (byte) (groupAddress & 0xFF),
                0x01,
                0x00, (byte) apci
        };
    }

    static byte[] groupValueResponseCemi(int groupAddress, int valueByte) {
        int apci = 0x40 | (valueByte & 0x3F);
        return new byte[]{
                CEMI_L_DATA_IND,
                0x00,
                (byte) 0xBC,
                (byte) 0xE0,
                0x11, 0x01,
                (byte) ((groupAddress >> 8) & 0xFF),
                (byte) (groupAddress & 0xFF),
                0x01,
                0x00, (byte) apci
        };
    }

    static byte[] udpHpai(byte[] ipv4, int port) {
        return new byte[]{
                0x08,
                0x01,
                ipv4[0], ipv4[1], ipv4[2], ipv4[3],
                (byte) ((port >> 8) & 0xFF),
                (byte) (port & 0xFF)
        };
    }

    static void writeHeader(ByteBuffer buf, int serviceType, int totalLength) {
        buf.put((byte) 0x06);
        buf.put((byte) 0x10);
        buf.putShort((short) serviceType);
        buf.putShort((short) totalLength);
    }

    static int serviceType(byte[] frame) {
        if (frame == null || frame.length < 6) {
            return -1;
        }
        return ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
    }

    static ConnectResponse parseConnectResponse(byte[] frame) {
        if (serviceType(frame) != SERVICE_CONNECT_RESPONSE || frame.length < 8) {
            throw new IllegalArgumentException("Not a CONNECT_RESPONSE");
        }
        int channelId = frame[6] & 0xFF;
        int status = frame[7] & 0xFF;
        return new ConnectResponse(channelId, status);
    }

    static TunnellingFrame parseTunnelling(byte[] frame) {
        int service = serviceType(frame);
        if (service != SERVICE_TUNNELLING_REQUEST && service != SERVICE_TUNNELLING_ACK) {
            throw new IllegalArgumentException("Not a TUNNELLING frame");
        }
        if (frame.length < 10) {
            throw new IllegalArgumentException("Short TUNNELLING frame");
        }
        int channelId = frame[7] & 0xFF;
        int sequence = frame[8] & 0xFF;
        int statusOrReserved = frame[9] & 0xFF;
        byte[] cemi = Arrays.copyOfRange(frame, 10, frame.length);
        return new TunnellingFrame(service, channelId, sequence, statusOrReserved, cemi);
    }

    static Integer extractGroupValue(byte[] cemi) {
        if (cemi == null || cemi.length < 11) {
            return null;
        }
        byte code = cemi[0];
        if (code != CEMI_L_DATA_IND && code != CEMI_L_DATA_REQ) {
            return null;
        }
        int apci = ((cemi[9] & 0xFF) << 8) | (cemi[10] & 0xFF);
        int command = (apci >> 6) & 0x03;
        if (command == 1 || command == 2) {
            return apci & 0x3F;
        }
        return null;
    }

    static int extractGroupAddressFromCemi(byte[] cemi) {
        if (cemi == null || cemi.length < 9) {
            return -1;
        }
        return ((cemi[6] & 0xFF) << 8) | (cemi[7] & 0xFF);
    }

    static boolean isGroupValueRead(byte[] cemi) {
        if (cemi == null || cemi.length < 11 || cemi[0] != CEMI_L_DATA_REQ) {
            return false;
        }
        int apci = ((cemi[9] & 0xFF) << 8) | (cemi[10] & 0xFF);
        return ((apci >> 6) & 0x03) == 0;
    }

    static boolean isGroupValueWrite(byte[] cemi) {
        if (cemi == null || cemi.length < 11 || cemi[0] != CEMI_L_DATA_REQ) {
            return false;
        }
        int apci = ((cemi[9] & 0xFF) << 8) | (cemi[10] & 0xFF);
        return ((apci >> 6) & 0x03) == 2;
    }

    record ConnectResponse(int channelId, int status) {
    }

    record TunnellingFrame(int service, int channelId, int sequence, int statusOrReserved, byte[] cemi) {
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02X", b));
        }
        return sb.toString();
    }
}
