package com.ispf.driver.smpp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SMPP driver — bind check and optional submit_sm.
 */
public class SmppDeviceDriver implements DeviceDriver {

    private static final DataSchema SMPP_SCHEMA = DataSchema.builder("smppResult")
            .field("value", FieldType.STRING)
            .field("bound", FieldType.BOOLEAN)
            .field("messageId", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "smpp",
            "SMPP Driver",
            "0.1.0",
            "Checks SMPP bind status or submits SMS messages via jSMPP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "2775",
                    "systemId", "smppclient",
                    "password", ""
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 2775;
    private String systemId = "smppclient";
    private String password = "";
    private final Map<String, SmppPoint> points = new ConcurrentHashMap<>();
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
        if (value == null) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "systemId" -> systemId = value.trim();
            case "password" -> password = value;
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "SMPP driver ready for " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        connected = false;
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
            SmppPoint point = SmppPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), execute(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("SMPP driver is read-only in v0.1");
    }

    private DataRecord execute(SmppPoint point) throws DriverException {
        SMPPSession session = new SMPPSession();
        try {
            session.connectAndBind(host, port,
                    new BindParameter(BindType.BIND_TRX, systemId, password, "cp",
                            TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, null));
            if (point.mode() == SmppPoint.SmppMode.BIND) {
                return DataRecord.single(SMPP_SCHEMA, Map.of(
                        "value", "bound",
                        "bound", true,
                        "messageId", ""
                ));
            }
            org.jsmpp.session.SubmitSmResult result = session.submitShortMessage(
                    "CMT",
                    TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN,
                    point.destination(),
                    TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN,
                    systemId,
                    new org.jsmpp.bean.ESMClass(),
                    (byte) 0, (byte) 1, null, null,
                    new org.jsmpp.bean.RegisteredDelivery(),
                    (byte) 0,
                    new org.jsmpp.bean.GeneralDataCoding(),
                    (byte) 0,
                    point.message().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String messageId = result == null ? "" : result.getMessageId();
            return DataRecord.single(SMPP_SCHEMA, Map.of(
                    "value", "sent",
                    "bound", true,
                    "messageId", messageId
            ));
        } catch (Exception e) {
            if (point.mode() == SmppPoint.SmppMode.BIND) {
                return DataRecord.single(SMPP_SCHEMA, Map.of(
                        "value", "unbound",
                        "bound", false,
                        "messageId", ""
                ));
            }
            throw new DriverException("SMPP submit failed", e);
        } finally {
            session.unbindAndClose();
        }
    }
}
