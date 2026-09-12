package com.ispf.driver.opcuaserver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.server.security.DefaultServerCertificateValidator;
import org.eclipse.milo.opcua.stack.server.security.ServerCertificateValidator;

import java.nio.file.Files;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Embedded OPC UA server driver — exposes mapped variables via Eclipse Milo 0.6.15.
 * <p>
 * Point mapping: {@code nodeId} e.g. {@code ns=2;s=Temperature} or bare identifier in configured namespace.
 */
public class OpcUaServerDeviceDriver implements DeviceDriver {

    private static final String APPLICATION_URI = OpcUaServerInterop.APPLICATION_URI;

    private static final DriverMetadata METADATA = new DriverMetadata(
            "opcua-server",
            "OPC UA Server Driver",
            "0.1.0",
            "Hosts an embedded OPC UA server (None / Sign / SignAndEncrypt) and maps node values to ISPF variables",
            "ISPF",
            Map.of(
                    "bindPort", String.valueOf(OpcUaServerInterop.DEFAULT_BIND_PORT),
                    "namespace", String.valueOf(OpcUaServerInterop.DEFAULT_NAMESPACE_INDEX),
                    "timeoutMs", "5000",
                    "endpointPath", OpcUaServerInterop.ENDPOINT_PATH,
                    "securityPolicy", "None",
                    "securityMode", "None"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("opcUaServerValue")
            .field("value", FieldType.STRING)
            .field("quality", FieldType.STRING)
            .field("nodeId", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private OpcUaServer server;
    private IspfOpcUaNamespace namespace;
    private int bindPort = 4840;
    private int namespaceIndex = 2;
    private int timeoutMs = 5000;
    private String securityPolicyRaw = "None";
    private String securityModeRaw = "";
    private String pkiDir = "";
    private OpcUaServerPki pki;
    private final Map<String, OpcUaServerPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("bindPort", value -> bindPort = Integer.parseInt(value));
        readConfig("namespace", value -> namespaceIndex = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
        readConfig("securityPolicy", value -> securityPolicyRaw = value.trim());
        readConfig("securityMode", value -> securityModeRaw = value.trim());
        readConfig("pkiDir", value -> pkiDir = value.trim());
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "bindPort" -> bindPort = Integer.parseInt(value.trim());
            case "namespace" -> namespaceIndex = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "securityPolicy" -> securityPolicyRaw = value.trim();
            case "securityMode" -> securityModeRaw = value.trim();
            case "pkiDir" -> pkiDir = value.trim();
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            SecurityPolicy policy = parsePolicy(securityPolicyRaw);
            MessageSecurityMode mode = parseMode(securityModeRaw, policy);
            if (policy != SecurityPolicy.None && pkiDir.isBlank()) {
                throw new DriverException("pkiDir is required when securityPolicy is not None");
            }
            java.nio.file.Path securityPath = pkiDir.isBlank()
                    ? Files.createTempDirectory("ispf-opcua-server-")
                    : java.nio.file.Path.of(pkiDir);
            pki = OpcUaServerPki.loadOrCreate(securityPath, "ISPF OPC UA Server", APPLICATION_URI);
            KeyPair keyPair = pki.keyPair();
            X509Certificate certificate = pki.certificate();

            DefaultCertificateManager certificateManager = new DefaultCertificateManager(keyPair, certificate);
            DefaultTrustListManager trustListManager = pki.trustList();
            ServerCertificateValidator certificateValidator = new DefaultServerCertificateValidator(trustListManager);

            EndpointConfiguration.Builder endpointBuilder = EndpointConfiguration.newBuilder()
                    .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                    .setBindAddress("0.0.0.0")
                    .setBindPort(bindPort)
                    .setHostname("localhost")
                    .setCertificate(certificate)
                    .addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
                    .addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME);

            EndpointConfiguration endpoint = endpointBuilder.copy()
                    .setPath("/ispf")
                    .setSecurityPolicy(policy)
                    .setSecurityMode(mode)
                    .build();
            // Milo clients GetEndpoints via "{path}/discovery" before selecting Sign/SignAndEncrypt.
            EndpointConfiguration discovery = endpointBuilder.copy()
                    .setPath("/ispf/discovery")
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None)
                    .build();

            OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
                    .setApplicationUri(APPLICATION_URI)
                    .setApplicationName(LocalizedText.english("ISPF OPC UA Server"))
                    .setProductUri(APPLICATION_URI)
                    .setEndpoints(Set.of(endpoint, discovery))
                    .setCertificateManager(certificateManager)
                    .setTrustListManager(trustListManager)
                    .setCertificateValidator(certificateValidator)
                    .setIdentityValidator(new CompositeValidator(
                            AnonymousIdentityValidator.INSTANCE,
                            new UsernameIdentityValidator(true, authChallenge -> true)
                    ))
                    .setBuildInfo(new BuildInfo(
                            APPLICATION_URI,
                            "ISPF",
                            "OPC UA Server Driver",
                            OpcUaServer.SDK_VERSION,
                            "0.1.0",
                            DateTime.now()
                    ))
                    .build();

            server = new OpcUaServer(serverConfig);
            namespace = new IspfOpcUaNamespace(server);
            namespace.setExternalWriteListener(this::onExternalWrite);
            server.getAddressSpaceManager().register(namespace);
            server.startup().get(timeoutMs, TimeUnit.MILLISECONDS);
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "OPC UA server listening on " + endpointUrl()
                            + " (" + policy.name() + " / " + mode
                            + ", browse: " + OpcUaServerInterop.browsePath("<tag>") + ")");
        } catch (DriverException e) {
            connected = false;
            shutdownServer();
            throw e;
        } catch (Exception e) {
            connected = false;
            shutdownServer();
            throw new DriverException("OPC UA server start failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        shutdownServer();
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return connected && server != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            OpcUaServerPoint point = OpcUaServerPoint.parse(entry.getValue(), namespace.getNamespaceIndex().intValue());
            points.put(entry.getKey(), point);
            namespace.ensureVariable(point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        OpcUaServerPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        String newValue = extractString(value);
        namespace.writeValue(point.nodeId(), newValue);
        driverObject.updateVariable(pointId, readPoint(point));
    }

    private void onExternalWrite(NodeId nodeId, String value) {
        for (Map.Entry<String, OpcUaServerPoint> entry : points.entrySet()) {
            if (entry.getValue().nodeId().equals(nodeId)) {
                OpcUaServerPoint point = entry.getValue();
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", value,
                        "quality", StatusCode.GOOD.toString(),
                        "nodeId", point.nodeIdText()
                )));
                return;
            }
        }
    }

    private DataRecord readPoint(OpcUaServerPoint point) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", namespace.readValue(point.nodeId()),
                "quality", StatusCode.GOOD.toString(),
                "nodeId", point.nodeIdText()
        ));
    }

    private static String extractString(DataRecord value) {
        Object raw = value.firstRow().get("value");
        return raw == null ? "" : raw.toString();
    }

    private void shutdownServer() {
        if (server != null) {
            try {
                server.shutdown().get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // best effort
            }
            server = null;
            namespace = null;
        }
        if (pki != null) {
            pki.close();
            pki = null;
        }
    }

    private static SecurityPolicy parsePolicy(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw.trim())) {
            return SecurityPolicy.None;
        }
        String normalized = raw.trim();
        if (normalized.startsWith("SecurityPolicy.")) {
            normalized = normalized.substring("SecurityPolicy.".length());
        }
        for (SecurityPolicy candidate : SecurityPolicy.values()) {
            if (candidate.name().equalsIgnoreCase(normalized)
                    || candidate.name().replace('_', '-').equalsIgnoreCase(normalized)
                    || candidate.getUri().equalsIgnoreCase(normalized)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported securityPolicy: " + raw);
    }

    private static MessageSecurityMode parseMode(String raw, SecurityPolicy policy) {
        if (policy == SecurityPolicy.None) {
            return MessageSecurityMode.None;
        }
        if (raw == null || raw.isBlank()
                || "signandencrypt".equalsIgnoreCase(raw.trim())
                || "sign-and-encrypt".equalsIgnoreCase(raw.trim())) {
            return MessageSecurityMode.SignAndEncrypt;
        }
        if ("sign".equalsIgnoreCase(raw.trim())) {
            return MessageSecurityMode.Sign;
        }
        throw new IllegalArgumentException("Unsupported securityMode: " + raw);
    }

    private void readConfig(String name, java.util.function.Consumer<String> consumer) {
        driverObject.getVariable(name).ifPresent(record -> {
            Object raw = record.firstRow().get("raw");
            if (raw == null) {
                raw = record.firstRow().get("value");
            }
            if (raw != null) {
                consumer.accept(raw.toString());
            }
        });
    }

    /** Documented interop endpoint URL for external OPC UA clients. */
    public String endpointUrl() {
        return OpcUaServerInterop.endpointUrl(bindPort);
    }
}
