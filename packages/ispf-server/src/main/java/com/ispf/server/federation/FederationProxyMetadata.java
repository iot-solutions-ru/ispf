package com.ispf.server.federation;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FederationProxyMetadata {

    public static final String VAR_PROXY = "federationProxy";
    public static final String VAR_PEER_ID = "federationPeerId";
    public static final String VAR_REMOTE_PATH = "federationRemotePath";

    private static final DataSchema BOOLEAN_VALUE = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private FederationProxyMetadata() {
    }

    public static boolean isProxy(PlatformObject node) {
        return readBoolean(node, VAR_PROXY);
    }

    public static Optional<UUID> peerId(PlatformObject node) {
        return readString(node, VAR_PEER_ID).flatMap(FederationProxyMetadata::parseUuid);
    }

    public static Optional<String> remotePath(PlatformObject node) {
        return readString(node, VAR_REMOTE_PATH);
    }

    public static DataRecord proxyFlag(boolean value) {
        return DataRecord.single(BOOLEAN_VALUE, Map.of("value", value));
    }

    public static DataRecord peerIdValue(UUID peerId) {
        return DataRecord.single(STRING_VALUE, Map.of("value", peerId.toString()));
    }

    public static DataRecord remotePathValue(String remotePath) {
        return DataRecord.single(STRING_VALUE, Map.of("value", remotePath));
    }

    public static void applyTo(PlatformObject node, UUID peerId, String remotePath) {
        setVariable(node, VAR_PROXY, BOOLEAN_VALUE, proxyFlag(true));
        setVariable(node, VAR_PEER_ID, STRING_VALUE, peerIdValue(peerId));
        setVariable(node, VAR_REMOTE_PATH, STRING_VALUE, remotePathValue(remotePath));
    }

    public static void clearFrom(PlatformObject node) {
        node.removeVariable(VAR_PROXY);
        node.removeVariable(VAR_PEER_ID);
        node.removeVariable(VAR_REMOTE_PATH);
    }

    public static boolean isFederationVariable(String name) {
        return VAR_PROXY.equals(name) || VAR_PEER_ID.equals(name) || VAR_REMOTE_PATH.equals(name);
    }

    private static void setVariable(PlatformObject node, String name, DataSchema schema, DataRecord value) {
        if (node.getVariable(name).isEmpty()) {
            node.addVariable(new Variable(name, schema, true, false, null, value));
            return;
        }
        node.getVariable(name).orElseThrow().setComputedValue(value);
    }

    private static boolean readBoolean(PlatformObject node, String name) {
        return node.getVariable(name)
                .flatMap(variable -> variable.value())
                .map(record -> record.firstRow().get("value"))
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return node.getVariable(name)
                .flatMap(variable -> variable.value())
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank());
    }

    private static Optional<UUID> parseUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
