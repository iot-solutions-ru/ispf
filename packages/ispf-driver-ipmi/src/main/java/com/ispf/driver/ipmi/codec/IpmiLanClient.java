package com.ispf.driver.ipmi.codec;

import com.ispf.driver.DriverException;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal ISPF-owned IPMI LAN/RMCP command client used by the IPMI driver.
 */
public final class IpmiLanClient implements AutoCloseable {

    public static final int CMD_OPEN_SESSION = 0x10;
    public static final int CMD_GET_CHASSIS_STATUS = 0x20;
    public static final int CMD_RESERVE_SDR = 0x30;
    public static final int CMD_GET_SDR = 0x31;
    public static final int CMD_GET_SENSOR_READING = 0x32;
    public static final int SDR_END_RECORD_ID = 0xFFFF;

    private final DatagramSocket socket;
    private final InetAddress address;
    private final int port;
    private int sequence = 1;
    private int reservationId;

    public IpmiLanClient(String host, int port, int timeoutMs) throws Exception {
        this.address = InetAddress.getByName(host);
        this.port = port;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(timeoutMs);
    }

    public void openSession(String username, String password) throws DriverException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeSizedString(payload, username);
        writeSizedString(payload, password);
        request(CMD_OPEN_SESSION, payload.toByteArray());
    }

    public boolean getChassisPowerOn() throws DriverException {
        byte[] response = request(CMD_GET_CHASSIS_STATUS, new byte[0]);
        return response.length > 0 && (response[0] & 0x01) != 0;
    }

    public List<IpmiSdrRecord> readSdrRepository() throws DriverException {
        byte[] reserve = request(CMD_RESERVE_SDR, new byte[0]);
        if (reserve.length < 2) {
            throw new DriverException("IPMI SDR reserve response too short");
        }
        reservationId = Short.toUnsignedInt(ByteBuffer.wrap(reserve).order(ByteOrder.LITTLE_ENDIAN).getShort());
        List<IpmiSdrRecord> records = new ArrayList<>();
        int recordId = 0;
        while (recordId != SDR_END_RECORD_ID) {
            ByteBuffer query = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            query.putShort((short) reservationId);
            query.putShort((short) recordId);
            byte[] data = request(CMD_GET_SDR, query.array());
            if (data.length < 4) {
                throw new DriverException("IPMI SDR response too short");
            }
            ByteBuffer response = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            recordId = Short.toUnsignedInt(response.getShort());
            int length = Short.toUnsignedInt(response.getShort());
            if (length > response.remaining()) {
                throw new DriverException("IPMI SDR record length exceeds response");
            }
            byte[] recordData = new byte[length];
            response.get(recordData);
            records.add(IpmiSdrRecord.parse(recordData));
        }
        return records;
    }

    public int getSensorReading(int sensorNumber) throws DriverException {
        byte[] response = request(CMD_GET_SENSOR_READING, new byte[] {(byte) sensorNumber});
        if (response.length < 1) {
            throw new DriverException("IPMI sensor reading response too short");
        }
        return Byte.toUnsignedInt(response[0]);
    }

    private byte[] request(int command, byte[] payload) throws DriverException {
        int seq = sequence++ & 0xFF;
        byte[] request = packet(seq, command, payload);
        try {
            socket.send(new DatagramPacket(request, request.length, address, port));
            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            if (packet.getLength() < 5 || Byte.toUnsignedInt(buffer[0]) != 0x07) {
                throw new DriverException("Invalid IPMI LAN response");
            }
            if (Byte.toUnsignedInt(buffer[1]) != seq || Byte.toUnsignedInt(buffer[2]) != command) {
                throw new DriverException("IPMI LAN response sequence mismatch");
            }
            int completion = Byte.toUnsignedInt(buffer[3]);
            if (completion != 0) {
                throw new DriverException("IPMI command 0x" + Integer.toHexString(command)
                        + " failed with completion 0x" + Integer.toHexString(completion));
            }
            int length = Byte.toUnsignedInt(buffer[4]);
            if (5 + length > packet.getLength()) {
                throw new DriverException("IPMI LAN response length mismatch");
            }
            byte[] response = new byte[length];
            System.arraycopy(buffer, 5, response, 0, length);
            return response;
        } catch (DriverException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DriverException("IPMI LAN command failed", ex);
        }
    }

    public static byte[] response(int seq, int command, int completion, byte[] payload) {
        byte[] packet = new byte[5 + payload.length];
        packet[0] = 0x07;
        packet[1] = (byte) seq;
        packet[2] = (byte) command;
        packet[3] = (byte) completion;
        packet[4] = (byte) payload.length;
        System.arraycopy(payload, 0, packet, 5, payload.length);
        return packet;
    }

    private static byte[] packet(int seq, int command, byte[] payload) {
        byte[] packet = new byte[5 + payload.length];
        packet[0] = 0x07;
        packet[1] = (byte) seq;
        packet[2] = (byte) command;
        packet[3] = 0;
        packet[4] = (byte) payload.length;
        System.arraycopy(payload, 0, packet, 5, payload.length);
        return packet;
    }

    private static void writeSizedString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        out.write(bytes.length);
        out.writeBytes(bytes);
    }

    @Override
    public void close() {
        socket.close();
    }
}
