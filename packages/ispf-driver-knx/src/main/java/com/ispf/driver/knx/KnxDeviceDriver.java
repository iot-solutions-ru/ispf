package com.ispf.driver.knx;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KNX/IP Tunneling driver — SEARCH/CONNECT plus GroupValue Read/Write (cEMI) over UDP.
 * <p>
 * Point mapping is a 3-level group address {@code main/middle/sub} (e.g. {@code 1/2/3}).
 * Write maps the record {@code value} field to a 6-bit GroupValue_Write.
 * <p>
 * Public KNXnet/IP / ISO 22510 documentation only. Clean-room ISPF code, Apache-2.0 —
 * no proprietary KNX stack (no Calimero / commercial SDKs).
 */
public class KnxDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("knxValue")
            .field("value", FieldType.STRING)
            .field("groupAddress", FieldType.STRING)
            .field("raw", FieldType.INTEGER)
            .field("channelId", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "knx",
            "KNX/IP Driver",
            "0.1.0",
            "KNXnet/IP Tunneling subset: search/connect + GroupValue Read/Write (cEMI) over UDP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "3671",
                    "timeoutMs", "3000",
                    "searchOnConnect", "true",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 3671;
    private int timeoutMs = 3000;
    private boolean searchOnConnect = true;
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile boolean connected;
    private DatagramSocket socket;
    private InetSocketAddress remote;
    private int channelId = -1;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "searchOnConnect" -> searchOnConnect = Boolean.parseBoolean(value.trim());
            default -> { }
        }
    }

    @Override
    public synchronized void connect() throws DriverException {
        if (connected) {
            return;
        }
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(timeoutMs);
            remote = new InetSocketAddress(InetAddress.getByName(host), port);
            byte[] localIp = socket.getLocalAddress().getAddress();
            if (localIp.length != 4) {
                localIp = new byte[]{127, 0, 0, 1};
            }
            byte[] hpai = KnxnetIpCodec.udpHpai(localIp, socket.getLocalPort());

            if (searchOnConnect) {
                send(KnxnetIpCodec.searchRequest(hpai));
                byte[] searchResp = receiveMatching(KnxnetIpCodec.SERVICE_SEARCH_RESPONSE);
                if (searchResp == null) {
                    throw new DriverException("KNX SEARCH_RESPONSE timeout from " + host + ":" + port);
                }
            }

            send(KnxnetIpCodec.connectRequest(hpai, hpai));
            byte[] connectResp = receiveMatching(KnxnetIpCodec.SERVICE_CONNECT_RESPONSE);
            if (connectResp == null) {
                throw new DriverException("KNX CONNECT_RESPONSE timeout from " + host + ":" + port);
            }
            KnxnetIpCodec.ConnectResponse parsed = KnxnetIpCodec.parseConnectResponse(connectResp);
            if (parsed.status() != 0) {
                throw new DriverException("KNX CONNECT failed status=" + parsed.status());
            }
            channelId = parsed.channelId();
            sequence.set(0);
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "KNX Tunneling ready for " + host + ":" + port + " channel=" + channelId);
        } catch (DriverException e) {
            closeQuietly();
            throw e;
        } catch (IOException e) {
            closeQuietly();
            throw new DriverException("KNX connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public synchronized void disconnect() {
        connected = false;
        closeQuietly();
        points.clear();
        channelId = -1;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public synchronized void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = entry.getValue() == null || entry.getValue().isBlank() ? pointId : entry.getValue();
            points.put(pointId, mapping);
            int ga = KnxnetIpCodec.parseGroupAddress(mapping);
            int value = groupValueRead(ga);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", Integer.toString(value),
                    "groupAddress", KnxnetIpCodec.formatGroupAddress(ga),
                    "raw", value,
                    "channelId", channelId
            )));
        }
    }

    @Override
    public synchronized void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = points.getOrDefault(pointId, pointId);
        int ga = KnxnetIpCodec.parseGroupAddress(mapping);
        int raw = parseWriteValue(value);
        groupValueWrite(ga, raw);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", Integer.toString(raw),
                "groupAddress", KnxnetIpCodec.formatGroupAddress(ga),
                "raw", raw,
                "channelId", channelId
        )));
    }

    private int groupValueRead(int groupAddress) throws DriverException {
        try {
            int seq = sequence.getAndIncrement() & 0xFF;
            byte[] cemi = KnxnetIpCodec.groupValueReadCemi(groupAddress);
            send(KnxnetIpCodec.tunnellingRequest(channelId, seq, cemi));

            Integer value = null;
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline && value == null) {
                byte[] frame = receiveRaw(Math.max(1, (int) (deadline - System.currentTimeMillis())));
                if (frame == null) {
                    break;
                }
                int service = KnxnetIpCodec.serviceType(frame);
                if (service == KnxnetIpCodec.SERVICE_TUNNELLING_ACK) {
                    continue;
                }
                if (service == KnxnetIpCodec.SERVICE_TUNNELLING_REQUEST) {
                    KnxnetIpCodec.TunnellingFrame tun = KnxnetIpCodec.parseTunnelling(frame);
                    send(KnxnetIpCodec.tunnellingAck(tun.channelId(), tun.sequence(), 0));
                    if (KnxnetIpCodec.extractGroupAddressFromCemi(tun.cemi()) == groupAddress) {
                        value = KnxnetIpCodec.extractGroupValue(tun.cemi());
                    }
                }
            }
            if (value == null) {
                throw new DriverException("KNX GroupValue_Read timeout for "
                        + KnxnetIpCodec.formatGroupAddress(groupAddress));
            }
            return value;
        } catch (DriverException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new DriverException("KNX GroupValue_Read failed", e);
        }
    }

    private void groupValueWrite(int groupAddress, int raw) throws DriverException {
        try {
            int seq = sequence.getAndIncrement() & 0xFF;
            byte[] cemi = KnxnetIpCodec.groupValueWriteCemi(groupAddress, raw);
            send(KnxnetIpCodec.tunnellingRequest(channelId, seq, cemi));
            long deadline = System.currentTimeMillis() + timeoutMs;
            boolean acked = false;
            while (System.currentTimeMillis() < deadline && !acked) {
                byte[] frame = receiveRaw(Math.max(1, (int) (deadline - System.currentTimeMillis())));
                if (frame == null) {
                    break;
                }
                if (KnxnetIpCodec.serviceType(frame) == KnxnetIpCodec.SERVICE_TUNNELLING_ACK) {
                    acked = true;
                } else if (KnxnetIpCodec.serviceType(frame) == KnxnetIpCodec.SERVICE_TUNNELLING_REQUEST) {
                    KnxnetIpCodec.TunnellingFrame tun = KnxnetIpCodec.parseTunnelling(frame);
                    send(KnxnetIpCodec.tunnellingAck(tun.channelId(), tun.sequence(), 0));
                }
            }
            if (!acked) {
                throw new DriverException("KNX GroupValue_Write ACK timeout for "
                        + KnxnetIpCodec.formatGroupAddress(groupAddress));
            }
        } catch (DriverException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new DriverException("KNX GroupValue_Write failed", e);
        }
    }

    private void send(byte[] frame) throws IOException {
        DatagramPacket packet = new DatagramPacket(frame, frame.length, remote);
        socket.send(packet);
    }

    private byte[] receiveMatching(int serviceType) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] frame = receiveRaw(Math.max(1, (int) (deadline - System.currentTimeMillis())));
            if (frame == null) {
                return null;
            }
            if (KnxnetIpCodec.serviceType(frame) == serviceType) {
                return frame;
            }
        }
        return null;
    }

    private byte[] receiveRaw(int waitMs) throws IOException {
        byte[] buf = new byte[512];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        int previous = socket.getSoTimeout();
        try {
            socket.setSoTimeout(Math.max(1, waitMs));
            socket.receive(packet);
            byte[] data = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
            return data;
        } catch (SocketTimeoutException e) {
            return null;
        } finally {
            try {
                socket.setSoTimeout(previous);
            } catch (Exception ignored) {
            }
        }
    }

    private void closeQuietly() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
    }

    private static int parseWriteValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return 0;
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "raw", "payload", "data")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return (int) Double.parseDouble(String.valueOf(candidate).trim()) & 0x3F;
            }
        }
        if (row.size() == 1) {
            return (int) Double.parseDouble(String.valueOf(row.values().iterator().next()).trim()) & 0x3F;
        }
        return 0;
    }
}
