package com.ispf.server.federation;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.ObjectManager;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Preserves local object metadata before federation overlay; restored on unbind.
 */
public final class FederationBindSnapshot {

    public static final String VAR_SNAPSHOT = "federationBindSnapshot";

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private FederationBindSnapshot() {
    }

    public record LocalState(
            ObjectType type,
            String displayName,
            String description,
            boolean driverWasRunning
    ) {
    }

    public static void captureIfAbsent(
            ObjectManager objectManager,
            DriverRuntimeService driverRuntimeService,
            ObjectMapper objectMapper,
            String localPath
    ) {
        PlatformObject node = objectManager.require(localPath);
        if (read(node, objectMapper).isPresent()) {
            return;
        }
        boolean driverWasRunning = driverRuntimeService.status(localPath)
                .map(status -> "RUNNING".equalsIgnoreCase(status.status()))
                .orElse(false);
        LocalState state = new LocalState(
                node.type(),
                node.displayName(),
                node.description(),
                driverWasRunning
        );
        write(objectManager, localPath, state, objectMapper);
    }

    public static Optional<LocalState> restoreAndClear(
            ObjectManager objectManager,
            ObjectMapper objectMapper,
            String localPath
    ) {
        PlatformObject node = objectManager.require(localPath);
        Optional<LocalState> state = read(node, objectMapper);
        state.ifPresent(local -> {
            objectManager.reconcileType(localPath, local.type());
            objectManager.updateInfo(localPath, local.displayName(), local.description());
        });
        clear(objectManager, localPath);
        return state;
    }

    public static boolean isSnapshotVariable(String name) {
        return VAR_SNAPSHOT.equals(name);
    }

    private static void write(
            ObjectManager objectManager,
            String localPath,
            LocalState state,
            ObjectMapper objectMapper
    ) {
        try {
            String json = objectMapper.writeValueAsString(new SnapshotPayload(
                    state.type().name(),
                    state.displayName(),
                    state.description(),
                    state.driverWasRunning()
            ));
            DataRecord value = DataRecord.single(STRING_VALUE, Map.of("value", json));
            PlatformObject node = objectManager.require(localPath);
            if (node.getVariable(VAR_SNAPSHOT).isEmpty()) {
                objectManager.createVariable(
                        localPath,
                        VAR_SNAPSHOT,
                        STRING_VALUE,
                        false,
                        false,
                        null,
                        value,
                        false,
                        null
                );
            } else {
                objectManager.setVariableValue(localPath, VAR_SNAPSHOT, value);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store federation bind snapshot for " + localPath, ex);
        }
    }

    private static Optional<LocalState> read(PlatformObject node, ObjectMapper objectMapper) {
        return node.getVariable(VAR_SNAPSHOT)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(json -> !json.isBlank())
                .flatMap(json -> parse(json, objectMapper));
    }

    private static Optional<LocalState> parse(String json, ObjectMapper objectMapper) {
        try {
            SnapshotPayload payload = objectMapper.readValue(json, SnapshotPayload.class);
            if (payload.type == null || payload.type.isBlank()) {
                return Optional.empty();
            }
            ObjectType type = ObjectType.valueOf(payload.type.trim().toUpperCase(Locale.ROOT));
            return Optional.of(new LocalState(
                    type,
                    payload.displayName != null ? payload.displayName : "",
                    payload.description != null ? payload.description : "",
                    payload.driverWasRunning
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static void clear(ObjectManager objectManager, String localPath) {
        objectManager.deleteVariable(localPath, VAR_SNAPSHOT);
    }

    private record SnapshotPayload(
            String type,
            String displayName,
            String description,
            boolean driverWasRunning
    ) {
    }
}
