package com.ispf.server.driver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverPointCatalog;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Offline point-catalog operations: artifacts, browse, proposals, and mapping import.
 * Live connect, poll, and write stay on {@link DriverRuntimeService}.
 */
final class DriverPointCatalogService {

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final DriverRuntimeService runtime;
    private final ObjectManager objectManager;
    private final DriverFactory driverFactory;
    private final ObjectMapper objectMapper;
    private final SystemObjectStructureService structureService;
    private final DeviceTelemetryPolicyService telemetryPolicyService;

    DriverPointCatalogService(
            DriverRuntimeService runtime,
            ObjectManager objectManager,
            DriverFactory driverFactory,
            ObjectMapper objectMapper,
            SystemObjectStructureService structureService,
            DeviceTelemetryPolicyService telemetryPolicyService
    ) {
        this.runtime = runtime;
        this.objectManager = objectManager;
        this.driverFactory = driverFactory;
        this.objectMapper = objectMapper;
        this.structureService = structureService;
        this.telemetryPolicyService = telemetryPolicyService;
    }

    List<DriverPointCatalog.ArtifactInfo> listDriverArtifacts(String driverId) {
        return withCatalog(driverId, catalog -> {
            try {
                return catalog.listArtifacts();
            } catch (DriverException e) {
                throw new IllegalStateException("Driver artifact list failed: " + e.getMessage(), e);
            }
        });
    }

    DriverPointCatalog.ArtifactInfo importDriverArtifact(String driverId, String fileName, byte[] content) {
        return withCatalog(driverId, catalog -> {
            try {
                return catalog.importArtifact(fileName, content);
            } catch (DriverException e) {
                throw new IllegalStateException("Driver artifact import failed: " + e.getMessage(), e);
            }
        });
    }

    void deleteDriverArtifact(String driverId, String name) {
        withCatalog(driverId, catalog -> {
            try {
                catalog.deleteArtifact(name);
                return null;
            } catch (DriverException e) {
                throw new IllegalStateException("Driver artifact delete failed: " + e.getMessage(), e);
            }
        });
    }

    List<DriverPointCatalog.CatalogNode> browseDriverCatalog(String driverId, String parentNodeId) {
        return withCatalog(driverId, catalog -> {
            try {
                return catalog.browseCatalog(parentNodeId);
            } catch (DriverException e) {
                throw new IllegalStateException("Driver catalog browse failed: " + e.getMessage(), e);
            }
        });
    }

    List<DriverPointCatalog.PointProposal> proposeDriverPoints(
            String driverId,
            List<DriverPointCatalog.PointSelection> selections
    ) {
        return withCatalog(driverId, catalog -> {
            try {
                return catalog.proposePoints(selections);
            } catch (DriverException e) {
                throw new IllegalStateException("Driver propose points failed: " + e.getMessage(), e);
            }
        });
    }

    DriverRuntimeService.ImportPointsResult importDriverPoints(
            String devicePath,
            List<DriverPointCatalog.PointProposal> proposals
    ) {
        structureService.ensureDeviceDriverStructure(devicePath);
        if (runtime.readBinding(devicePath).isEmpty()) {
            throw new IllegalArgumentException("No driver binding for: " + devicePath);
        }
        if (proposals == null || proposals.isEmpty()) {
            return new DriverRuntimeService.ImportPointsResult(0, 0, List.of());
        }
        PlatformObject device = objectManager.require(devicePath);
        String mappingsJson = stringValue(device, "driverPointMappingsJson").orElse("{}");
        Map<String, Object> mappings = parseMappingsObject(mappingsJson);
        int createdVars = 0;
        int updatedMappings = 0;
        List<String> variableNames = new ArrayList<>();
        for (DriverPointCatalog.PointProposal proposal : proposals) {
            if (proposal.variableName() == null || proposal.variableName().isBlank()) {
                throw new IllegalArgumentException("Point proposal missing variableName");
            }
            if (proposal.pointAddress() == null || proposal.pointAddress().isBlank()) {
                throw new IllegalArgumentException("Point proposal missing pointAddress for " + proposal.variableName());
            }
            Object existingMapping = mappings.get(proposal.variableName());
            String existingPoint = mappingPointId(existingMapping);
            if (existingPoint != null && !existingPoint.isBlank() && !existingPoint.equals(proposal.pointAddress())) {
                throw new IllegalStateException(
                        "Variable " + proposal.variableName() + " already mapped to " + existingPoint
                                + "; cannot map to " + proposal.pointAddress()
                );
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("point", proposal.pointAddress());
            if (proposal.dis() != null && !proposal.dis().isBlank()) {
                entry.put("dis", proposal.dis());
            }
            if (proposal.unit() != null && !proposal.unit().isBlank()) {
                entry.put("unit", proposal.unit());
            }
            mappings.put(proposal.variableName(), entry);
            updatedMappings++;
            variableNames.add(proposal.variableName());

            if (device.getVariable(proposal.variableName()).isEmpty()) {
                DataSchema schema = schemaFromProposal(proposal);
                DataRecord initial = DataRecord.single(schema, emptyRowFor(schema));
                objectManager.createVariable(
                        devicePath,
                        proposal.variableName(),
                        schema,
                        true,
                        proposal.writable(),
                        initial,
                        proposal.historySuggested(),
                        proposal.historySuggested() ? 30 : null
                );
                createdVars++;
            }
        }
        try {
            objectManager.setSystemVariableValue(
                    devicePath,
                    "driverPointMappingsJson",
                    DataRecord.single(
                            STRING_VALUE_SCHEMA,
                            Map.of("value", objectMapper.writeValueAsString(mappings))
                    )
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist point mappings", e);
        }
        telemetryPolicyService.invalidateCache(devicePath);
        return new DriverRuntimeService.ImportPointsResult(createdVars, updatedMappings, List.copyOf(variableNames));
    }

    private <T> T withCatalog(String driverId, Function<DriverPointCatalog, T> action) {
        String id = driverId == null || driverId.isBlank() ? "snmp" : driverId.trim();
        DeviceDriver driver = driverFactory.create(id);
        if (!(driver instanceof DriverPointCatalog catalog)) {
            throw new IllegalArgumentException("Driver does not support point catalog: " + id);
        }
        return action.apply(catalog);
    }

    private Map<String, Object> parseMappingsObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new tools.jackson.core.type.TypeReference<>() {
            });
            return new LinkedHashMap<>(parsed != null ? parsed : Map.of());
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String mappingPointId(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            Object point = map.containsKey("point") ? map.get("point")
                    : map.containsKey("address") ? map.get("address") : map.get("pointId");
            return point != null ? point.toString() : "";
        }
        return value.toString();
    }

    private static DataSchema schemaFromProposal(DriverPointCatalog.PointProposal proposal) {
        String schemaName = proposal.schemaName() != null && !proposal.schemaName().isBlank()
                ? proposal.schemaName()
                : "driverPoint";
        DataSchema.Builder builder = DataSchema.builder(schemaName);
        if (proposal.schemaFields() == null || proposal.schemaFields().isEmpty()) {
            builder.field("value", FieldType.STRING);
            return builder.build();
        }
        for (DriverPointCatalog.SchemaField field : proposal.schemaFields()) {
            builder.field(field.name(), FieldType.valueOf(field.fieldType().toUpperCase(Locale.ROOT)));
        }
        return builder.build();
    }

    private static Map<String, Object> emptyRowFor(DataSchema schema) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (var field : schema.fields()) {
            Object value = switch (field.type()) {
                case BOOLEAN -> false;
                case INTEGER, LONG, DOUBLE -> 0;
                default -> "";
            };
            row.put(field.name(), value);
        }
        return row;
    }

    private static Optional<String> stringValue(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(com.ispf.core.object.Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }
}
