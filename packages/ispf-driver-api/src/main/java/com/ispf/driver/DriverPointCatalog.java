package com.ispf.driver;

import java.util.List;

/**
 * Optional driver capability for shared point catalogs (e.g. SNMP MIB library).
 * <p>
 * Artifacts are typically global for the driver type; {@link #proposePoints} is applied
 * per device by the platform runtime.
 */
public interface DriverPointCatalog {

    record ArtifactInfo(String name, String moduleName, long sizeBytes, String status) {
    }

    record CatalogNode(
            String nodeId,
            String displayName,
            String nodeClass,
            String oid,
            String syntax,
            String maxAccess,
            String units,
            String description,
            boolean selectable
    ) {
    }

    record SchemaField(String name, String fieldType) {
    }

    record PointProposal(
            String variableName,
            String pointAddress,
            String schemaName,
            List<SchemaField> schemaFields,
            boolean writable,
            boolean historySuggested,
            String dis,
            String unit,
            String description
    ) {
        public PointProposal {
            schemaFields = schemaFields != null ? List.copyOf(schemaFields) : List.of();
            dis = dis != null ? dis : "";
            unit = unit != null ? unit : "";
            description = description != null ? description : "";
        }
    }

    record PointSelection(String nodeId, String index) {
        public PointSelection {
            index = index != null ? index : "";
        }
    }

    List<ArtifactInfo> listArtifacts() throws DriverException;

    ArtifactInfo importArtifact(String fileName, byte[] content) throws DriverException;

    void deleteArtifact(String name) throws DriverException;

    List<CatalogNode> browseCatalog(String parentNodeId) throws DriverException;

    List<PointProposal> proposePoints(List<PointSelection> selections) throws DriverException;
}
