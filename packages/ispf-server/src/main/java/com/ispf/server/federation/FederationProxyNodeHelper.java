package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.BindingStateVariables;
import com.ispf.server.object.ObjectManager;

import java.util.Locale;
import java.util.UUID;

public final class FederationProxyNodeHelper {

    private FederationProxyNodeHelper() {
    }

    public static void markProxy(ObjectManager objectManager, String localPath, UUID peerId, String remotePath) {
        PlatformObject node = objectManager.require(localPath);
        FederationProxyMetadata.applyTo(node, peerId, remotePath);
        objectManager.persistNodeTree(localPath);
    }

    public static PlatformObject createProxyNode(
            ObjectManager objectManager,
            String localPath,
            JsonNode remote,
            UUID peerId,
            String remotePath
    ) {
        int lastDot = localPath.lastIndexOf('.');
        String parentPath = localPath.substring(0, lastDot);
        String name = localPath.substring(lastDot + 1);
        ObjectType type = parseType(textOrDefault(remote, "type", "AGENT"));
        String displayName = textOrDefault(remote, "displayName", name);
        String description = textOrDefault(remote, "description", "");
        objectManager.create(parentPath, name, type, displayName, description, null);
        markProxy(objectManager, localPath, peerId, remotePath);
        return objectManager.require(localPath);
    }

    public static void syncFromRemoteProbe(
            ObjectManager objectManager,
            String localPath,
            JsonNode remote
    ) {
        ObjectType type = parseType(textOrDefault(remote, "type", "AGENT"));
        objectManager.reconcileType(localPath, type);
        String displayName = textOrNull(remote, "displayName");
        String description = textOrNull(remote, "description");
        if (displayName != null || description != null) {
            PlatformObject node = objectManager.require(localPath);
            objectManager.updateInfo(
                    localPath,
                    displayName != null ? displayName : node.displayName(),
                    description != null ? description : node.description()
            );
        }
        ensureLocalVariableShells(objectManager, localPath, remote);
    }

    /**
     * Create+bind starts from an empty node. Variable reads keep a remote field only when the
     * local node already has a variable of the same name, so the probe names must exist locally.
     */
    private static void ensureLocalVariableShells(ObjectManager objectManager, String localPath, JsonNode remote) {
        JsonNode names = remote.get("variableNames");
        if (names == null || !names.isArray()) {
            return;
        }
        for (JsonNode item : names) {
            if (item == null || !item.isString()) {
                continue;
            }
            String varName = item.asString().trim();
            if (varName.isEmpty()
                    || FederationProxyMetadata.isFederationVariable(varName)
                    || BindingStateVariables.isReserved(varName)) {
                continue;
            }
            PlatformObject node = objectManager.require(localPath);
            if (node.getVariable(varName).isPresent()) {
                continue;
            }
            DataSchema schema = DataSchema.builder(varName).field("value", FieldType.STRING).build();
            objectManager.createVariable(localPath, varName, schema, true, true, null, false, null);
        }
    }

    public static ObjectType parseType(String raw) {
        try {
            return ObjectType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ObjectType.AGENT;
        }
    }

    public static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isString()) {
            return null;
        }
        String text = value.asString();
        return text.isBlank() ? null : text;
    }

    public static String textOrDefault(JsonNode node, String field, String defaultValue) {
        String text = textOrNull(node, field);
        return text != null ? text : defaultValue;
    }
}
