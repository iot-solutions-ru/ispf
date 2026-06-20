package com.ispf.driver.snmp;

import com.ispf.core.model.DataRecord;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.CommunityTarget;
import org.snmp4j.UserTarget;
import org.snmp4j.security.SecurityProtocols;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SNMP driver (v1/v2c/v3) — polls OIDs and maps values to ISPF object variables.
 * <p>
 * Point mapping: {@code oid}, {@code oid:VALUE_KIND}, or {@code oid:VALUE_KIND:optional}.
 */
public class SnmpDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "snmp",
            "SNMP Driver",
            "0.2.0",
            "Polls SNMP v1/v2c/v3 agents (GET/SET) and maps OID values to ISPF variables",
            "ISPF",
            Map.ofEntries(
                    Map.entry("host", "127.0.0.1"),
                    Map.entry("port", "161"),
                    Map.entry("community", "public"),
                    Map.entry("version", "2c"),
                    Map.entry("securityName", ""),
                    Map.entry("authProtocol", "MD5"),
                    Map.entry("authPassphrase", ""),
                    Map.entry("privProtocol", "DES"),
                    Map.entry("privPassphrase", ""),
                    Map.entry("timeoutMs", "3000"),
                    Map.entry("retries", "1"),
                    Map.entry("pollIntervalMs", "5000")
            )
    );

    private DriverObject driverObject;
    private Snmp snmp;
    private TransportMapping<UdpAddress> transport;
    private Target<Address> target;

    private String host = "127.0.0.1";
    private int port = 161;
    private String community = "public";
    private int snmpVersion = SnmpConstants.version2c;
    private String securityName = "";
    private String authProtocol = "MD5";
    private String authPassphrase = "";
    private String privProtocol = "DES";
    private String privPassphrase = "";
    private long timeoutMs = 3000;
    private int retries = 1;

    private final Map<String, SnmpPoint> points = new ConcurrentHashMap<>();
    private final Set<String> optionalOidWarnings = ConcurrentHashMap.newKeySet();
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
            case "community" -> community = value.trim();
            case "version" -> snmpVersion = parseVersion(value.trim());
            case "securityName" -> securityName = value.trim();
            case "authProtocol" -> authProtocol = value.trim();
            case "authPassphrase" -> authPassphrase = value;
            case "privProtocol" -> privProtocol = value.trim();
            case "privPassphrase" -> privPassphrase = value;
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "retries" -> retries = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();

            Address address = parseAddress(host, port);
            if (snmpVersion == SnmpConstants.version3) {
                target = buildV3Target(address);
            } else {
                CommunityTarget<Address> communityTarget = new CommunityTarget<>();
                communityTarget.setCommunity(new OctetString(community));
                communityTarget.setVersion(snmpVersion);
                communityTarget.setAddress(address);
                communityTarget.setRetries(retries);
                communityTarget.setTimeout(timeoutMs);
                target = communityTarget;
            }

            connected = true;
            driverObject.log(
                    DriverLogLevel.INFO,
                    "SNMP connected to " + host + ":" + port + " (v" + versionLabel() + ")"
            );
        } catch (IOException e) {
            connected = false;
            closeSession();
            throw new DriverException("SNMP connect failed", e);
        } catch (IllegalArgumentException e) {
            connected = false;
            closeSession();
            throw new DriverException(e.getMessage(), e);
        }
    }

    private UserTarget<Address> buildV3Target(Address address) {
        if (securityName.isBlank()) {
            throw new IllegalArgumentException("SNMPv3 requires securityName");
        }
        SecurityProtocols.getInstance().addDefaultProtocols();
        USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
        SecurityModels.getInstance().addSecurityModel(usm);
        snmp.getUSM().addUser(
                new OctetString(securityName),
                new UsmUser(
                        new OctetString(securityName),
                        resolveAuthProtocol(authProtocol),
                        authPassphrase.isBlank() ? null : new OctetString(authPassphrase),
                        resolvePrivProtocol(privProtocol),
                        privPassphrase.isBlank() ? null : new OctetString(privPassphrase)
                )
        );

        UserTarget<Address> userTarget = new UserTarget<>();
        userTarget.setAddress(address);
        userTarget.setVersion(SnmpConstants.version3);
        userTarget.setSecurityName(new OctetString(securityName));
        userTarget.setSecurityLevel(resolveSecurityLevel());
        userTarget.setRetries(retries);
        userTarget.setTimeout(timeoutMs);
        return userTarget;
    }

    private int resolveSecurityLevel() {
        boolean hasAuth = !authPassphrase.isBlank();
        boolean hasPriv = !privPassphrase.isBlank();
        if (hasAuth && hasPriv) {
            return SecurityLevel.AUTH_PRIV;
        }
        if (hasAuth) {
            return SecurityLevel.AUTH_NOPRIV;
        }
        return SecurityLevel.NOAUTH_NOPRIV;
    }

    private static OID resolveAuthProtocol(String value) {
        return switch (value.toUpperCase()) {
            case "SHA", "SHA1" -> AuthSHA.ID;
            default -> AuthMD5.ID;
        };
    }

    private static OID resolvePrivProtocol(String value) {
        return switch (value.toUpperCase()) {
            case "AES", "AES128" -> PrivAES128.ID;
            default -> PrivDES.ID;
        };
    }

    @Override
    public void disconnect() {
        connected = false;
        closeSession();
        driverObject.log(DriverLogLevel.INFO, "SNMP disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected && snmp != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            SnmpPoint point = SnmpPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            try {
                DataRecord record = getOid(point);
                driverObject.updateVariable(entry.getKey(), record);
            } catch (DriverException e) {
                if (point.optional()) {
                    if (optionalOidWarnings.add(entry.getKey())) {
                        driverObject.log(
                                DeviceDriver.DriverLogLevel.DEBUG,
                                "Optional SNMP point " + entry.getKey() + " (" + point.oid() + "): " + e.getMessage()
                        );
                    }
                    continue;
                }
                throw e;
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        SnmpPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown SNMP point: " + pointId);
        }
        setOid(point, SnmpValueMapper.fromRecord(value, point.valueKind()));
        driverObject.updateVariable(pointId, getOid(point));
    }

    private DataRecord getOid(SnmpPoint point) throws DriverException {
        try {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(point.oid())));
            pdu.setType(PDU.GET);

            ResponseEvent event = snmp.send(pdu, target);
            if (event == null || event.getResponse() == null) {
                throw new DriverException("SNMP GET timeout for OID " + point.oid());
            }
            if (event.getResponse().getErrorStatus() != PDU.noError) {
                throw new DriverException(
                        "SNMP GET error for OID " + point.oid() + ": "
                                + event.getResponse().getErrorStatusText()
                );
            }
            VariableBinding binding = event.getResponse().get(0);
            if (binding == null || binding.getVariable() == null || binding.getVariable() instanceof Null) {
                throw new DriverException("SNMP OID not available: " + point.oid());
            }
            return SnmpValueMapper.toRecord(binding.getVariable(), point.valueKind());
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("SNMP GET failed for OID " + point.oid(), e);
        }
    }

    private void setOid(SnmpPoint point, org.snmp4j.smi.Variable variable) throws DriverException {
        try {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(point.oid()), variable));
            pdu.setType(PDU.SET);

            ResponseEvent event = snmp.send(pdu, target);
            if (event == null || event.getResponse() == null) {
                throw new DriverException("SNMP SET timeout for OID " + point.oid());
            }
            if (event.getResponse().getErrorStatus() != PDU.noError) {
                throw new DriverException(
                        "SNMP SET error for OID " + point.oid() + ": "
                                + event.getResponse().getErrorStatusText()
                );
            }
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("SNMP SET failed for OID " + point.oid(), e);
        }
    }

    private void closeSession() {
        if (snmp != null) {
            try {
                snmp.close();
            } catch (IOException ignored) {
                // ignore on shutdown
            }
            snmp = null;
        }
        transport = null;
        target = null;
    }

    private static Address parseAddress(String host, int port) throws DriverException {
        Address address = GenericAddress.parse("udp:" + host + "/" + port);
        if (address == null) {
            throw new DriverException("Invalid SNMP address: " + host + ":" + port);
        }
        return address;
    }

    private int parseVersion(String version) {
        return switch (version.toLowerCase()) {
            case "1", "v1" -> SnmpConstants.version1;
            case "3", "v3" -> SnmpConstants.version3;
            default -> SnmpConstants.version2c;
        };
    }

    private String versionLabel() {
        return switch (snmpVersion) {
            case SnmpConstants.version1 -> "1";
            case SnmpConstants.version3 -> "3";
            default -> "2c";
        };
    }
}
