package com.ispf.driver.wago;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modbus-TCP-compatible lab driver for WAGO PFC controllers — holding register read (FC3)
 * and single-register write (FC6). Default TCP port {@code 502} (standard Modbus TCP).
 * Catalog / stub YAML may list {@code 2455}; override {@code port} when a non-standard
 * listener is used. Both ports are valid configuration — this pack defaults to 502.
 * <p>
 * This is <strong>not</strong> a CODESYS proprietary / e!COCKPIT binary stack. Many WAGO PFC
 * Ethernet interfaces expose standard Modbus TCP; this pack speaks that dialect only.
 * <p>
 * Point mapping: {@code HR:100}, {@code 100}, or {@code MW100} — see {@link WagoPoint}.
 * Lab mapping is 1:1 (HR/MW numeric address → Modbus holding register). Write uses FC6 for a
 * single register ({@code value}/{@code raw}). Clean-room ISPF code, Apache-2.0 — JDK sockets
 * only; no PLC4X, no vendor SDK.
 */
public class WagoDeviceDriver implements DeviceDriver {

    private static final byte FC_READ_HOLDING = 3;
    private static final byte FC_WRITE_SINGLE = 6;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("wagoValue")
            .field("value", FieldType.STRING)
            .field("address", FieldType.INTEGER)
            .field("count", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "wago",
            "WAGO Driver",
            "0.1.0",
            "Modbus-TCP-compatible FC3/FC6 lab driver for WAGO PFC holding registers (not CODESYS proprietary)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "502",
                    "unitId", "1",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 502;
    private int unitId = 1;
    private int timeoutMs = 3000;
    private final AtomicInteger transactionId = new AtomicInteger();
    private final Map<String, WagoPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

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
            case "unitId", "slaveId" -> unitId = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "WAGO Modbus TCP ready for " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            WagoPoint point = WagoPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readHolding(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        WagoPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        int word = (int) extractNumeric(value) & 0xFFFF;
        writeSingle(point.address(), word);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", String.valueOf(word),
                "address", point.address(),
                "count", 1
        )));
    }

    private DataRecord readHolding(WagoPoint point) throws DriverException {
        ByteBuffer pdu = ByteBuffer.allocate(5);
        pdu.put(FC_READ_HOLDING);
        pdu.putShort((short) point.address());
        pdu.putShort((short) point.count());
        byte[] response = transact(pdu.array());
        if (response.length < 2 || response[0] != FC_READ_HOLDING) {
            throw new DriverException("Unexpected WAGO FC3 response");
        }
        int byteCount = response[1] & 0xFF;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < point.count(); i++) {
            int offset = 2 + i * 2;
            if (offset + 1 >= 2 + byteCount) {
                break;
            }
            if (i > 0) {
                sb.append(',');
            }
            int word = ((response[offset] & 0xFF) << 8) | (response[offset + 1] & 0xFF);
            sb.append(word);
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", sb.toString(),
                "address", point.address(),
                "count", point.count()
        ));
    }

    private void writeSingle(int address, int word) throws DriverException {
        ByteBuffer pdu = ByteBuffer.allocate(5);
        pdu.put(FC_WRITE_SINGLE);
        pdu.putShort((short) address);
        pdu.putShort((short) (word & 0xFFFF));
        byte[] response = transact(pdu.array());
        if (response.length < 5 || response[0] != FC_WRITE_SINGLE) {
            throw new DriverException("Unexpected WAGO FC6 response");
        }
    }

    private byte[] transact(byte[] pdu) throws DriverException {
        int txId = transactionId.incrementAndGet() & 0xFFFF;
        ByteBuffer request = ByteBuffer.allocate(7 + pdu.length);
        request.putShort((short) txId);
        request.putShort((short) 0); // protocol id
        request.putShort((short) (1 + pdu.length));
        request.put((byte) (unitId & 0xFF));
        request.put(pdu);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request.array());
            out.flush();

            byte[] header = in.readNBytes(7);
            if (header.length < 7) {
                throw new IOException("Incomplete Modbus TCP header");
            }
            int length = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
            if (length < 1) {
                throw new IOException("Invalid Modbus length");
            }
            byte[] rest = in.readNBytes(length - 1);
            if (rest.length < length - 1) {
                throw new IOException("Truncated Modbus PDU");
            }
            if ((rest[0] & 0x80) != 0) {
                int ex = rest.length > 1 ? rest[1] & 0xFF : -1;
                throw new DriverException("Modbus exception " + ex);
            }
            return rest;
        } catch (IOException e) {
            throw new DriverException("WAGO Modbus I/O failed for " + host + ":" + port, e);
        }
    }

    private static long extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("WAGO write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("raw", "value")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.longValue();
            }
            if (candidate != null) {
                String text = String.valueOf(candidate);
                int comma = text.indexOf(',');
                return Long.parseLong(comma < 0 ? text.trim() : text.substring(0, comma).trim());
            }
        }
        throw new IllegalArgumentException("WAGO write requires numeric raw/value field");
    }
}
