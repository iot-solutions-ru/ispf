package com.ispf.driver.smb;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SMB driver — checks file existence and size on a network share.
 */
public class SmbDeviceDriver implements DeviceDriver {

    private static final DataSchema FILE_SCHEMA = DataSchema.builder("smbFile")
            .field("exists", FieldType.BOOLEAN)
            .field("size", FieldType.LONG)
            .field("value", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "smb",
            "SMB Share Driver",
            "0.1.0",
            "Reads file existence and size from an SMB/CIFS network share",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "share", "shared",
                    "username", "",
                    "password", "",
                    "domain", ""
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private String share = "shared";
    private String username = "";
    private String password = "";
    private String domain = "";
    private final Map<String, SmbPoint> points = new ConcurrentHashMap<>();
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
            case "share" -> share = value.trim();
            case "username" -> username = value.trim();
            case "password" -> password = value;
            case "domain" -> domain = value.trim();
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "SMB driver ready for \\\\" + host + "\\" + share);
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
            SmbPoint point = SmbPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readFile(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("SMB driver is read-only in v0.1");
    }

    private DataRecord readFile(SmbPoint point) throws DriverException {
        SMBClient client = new SMBClient();
        try (Connection connection = client.connect(host)) {
            AuthenticationContext auth = new AuthenticationContext(
                    username.isEmpty() ? "guest" : username,
                    password.toCharArray(),
                    domain.isEmpty() ? null : domain);
            Session session = connection.authenticate(auth);
            try (DiskShare diskShare = (DiskShare) session.connectShare(share)) {
                String path = point.filePath().replace('/', '\\');
                boolean exists = diskShare.fileExists(path);
                if (!exists) {
                    return DataRecord.single(FILE_SCHEMA, Map.of(
                            "exists", false,
                            "size", 0L,
                            "value", ""
                    ));
                }
                try (com.hierynomus.smbj.share.File file = diskShare.openFile(
                        path,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null)) {
                    long size = file.getFileInformation(com.hierynomus.msfscc.fileinformation.FileStandardInformation.class)
                            .getEndOfFile();
                    return DataRecord.single(FILE_SCHEMA, Map.of(
                            "exists", true,
                            "size", size,
                            "value", String.valueOf(size)
                    ));
                }
            }
        } catch (Exception e) {
            throw new DriverException("SMB read failed for " + point.filePath(), e);
        }
    }
}
