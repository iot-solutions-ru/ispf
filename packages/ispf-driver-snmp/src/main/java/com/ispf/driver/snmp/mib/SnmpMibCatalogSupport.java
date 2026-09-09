package com.ispf.driver.snmp.mib;

import com.ispf.driver.DriverException;
import com.ispf.driver.DriverPointCatalog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bridges {@link SnmpMibLibrary} to {@link DriverPointCatalog}.
 */
public final class SnmpMibCatalogSupport {

    private SnmpMibCatalogSupport() {
    }

    public static List<DriverPointCatalog.ArtifactInfo> listArtifacts() throws DriverException {
        try {
            return SnmpMibLibrary.get().listFiles().stream()
                    .map(f -> new DriverPointCatalog.ArtifactInfo(
                            f.fileName(), f.moduleName(), f.sizeBytes(), f.status()))
                    .toList();
        } catch (IOException e) {
            throw new DriverException("Failed to list MIB artifacts: " + e.getMessage(), e);
        }
    }

    public static DriverPointCatalog.ArtifactInfo importArtifact(String fileName, byte[] content)
            throws DriverException {
        try {
            SnmpMibModule.FileInfo info = SnmpMibLibrary.get().importFile(fileName, content);
            return new DriverPointCatalog.ArtifactInfo(
                    info.fileName(), info.moduleName(), info.sizeBytes(), info.status());
        } catch (IOException e) {
            throw new DriverException("Failed to import MIB: " + e.getMessage(), e);
        }
    }

    public static void deleteArtifact(String name) throws DriverException {
        try {
            SnmpMibLibrary.get().deleteFile(name);
        } catch (IOException e) {
            throw new DriverException("Failed to delete MIB: " + e.getMessage(), e);
        }
    }

    public static List<DriverPointCatalog.CatalogNode> browse(String parentNodeId) {
        SnmpMibLibrary.get().ensureLoaded();
        String parent = parentNodeId == null ? "" : parentNodeId.trim();
        if (parent.isBlank() || "root".equalsIgnoreCase(parent)) {
            Map<String, String> modules = new LinkedHashMap<>();
            for (SnmpMibObject object : SnmpMibLibrary.get().listObjects()) {
                modules.putIfAbsent(object.moduleName(), object.moduleName());
            }
            // also include files that parsed with no OBJECT-TYPE yet
            try {
                for (SnmpMibModule.FileInfo file : SnmpMibLibrary.get().listFiles()) {
                    modules.putIfAbsent(file.moduleName(), file.moduleName());
                }
            } catch (IOException ignored) {
                // ignore
            }
            List<DriverPointCatalog.CatalogNode> nodes = new ArrayList<>();
            for (String module : modules.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
                nodes.add(new DriverPointCatalog.CatalogNode(
                        "module:" + module,
                        module,
                        "Module",
                        "",
                        "",
                        "",
                        "",
                        "MIB module",
                        false
                ));
            }
            return nodes;
        }
        if (parent.startsWith("module:")) {
            String module = parent.substring("module:".length());
            return SnmpMibLibrary.get().listObjects().stream()
                    .filter(o -> o.moduleName().equals(module))
                    .filter(SnmpMibObject::selectable)
                    .sorted(Comparator.comparing(SnmpMibObject::name, String.CASE_INSENSITIVE_ORDER))
                    .map(SnmpMibCatalogSupport::toNode)
                    .toList();
        }
        return List.of();
    }

    public static List<DriverPointCatalog.PointProposal> propose(
            List<DriverPointCatalog.PointSelection> selections
    ) throws DriverException {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }
        Map<String, SnmpMibObject> byId = new LinkedHashMap<>();
        for (SnmpMibObject object : SnmpMibLibrary.get().listObjects()) {
            byId.put(object.qualifiedName(), object);
            byId.put("object:" + object.qualifiedName(), object);
            byId.put(object.name(), object);
        }
        List<DriverPointCatalog.PointProposal> out = new ArrayList<>();
        for (DriverPointCatalog.PointSelection selection : selections) {
            SnmpMibObject object = byId.get(selection.nodeId());
            if (object == null && selection.nodeId().startsWith("object:")) {
                object = byId.get(selection.nodeId().substring("object:".length()));
            }
            if (object == null || !object.selectable()) {
                throw new DriverException("Unknown or non-selectable MIB object: " + selection.nodeId());
            }
            if (object.resolvedOid().isBlank()) {
                throw new DriverException(
                        "OID not resolved for " + object.qualifiedName()
                                + " — upload imported modules (IMPORTS) first"
                );
            }
            if (object.kind() == SnmpMibObject.Kind.COLUMN && selection.index().isBlank()) {
                throw new DriverException(
                        "Table column " + object.name() + " requires an index (e.g. ifIndex)"
                );
            }
            out.add(toProposal(object, selection.index()));
        }
        return out;
    }

    private static DriverPointCatalog.CatalogNode toNode(SnmpMibObject object) {
        return new DriverPointCatalog.CatalogNode(
                "object:" + object.qualifiedName(),
                object.name(),
                object.kind().name(),
                object.resolvedOid(),
                object.syntax(),
                object.maxAccess(),
                object.units(),
                truncate(object.description(), 240),
                object.selectable()
        );
    }

    private static DriverPointCatalog.PointProposal toProposal(SnmpMibObject object, String index) {
        String valueKind = mapValueKind(object.syntax());
        String instanceOid = object.resolvedOid();
        if (object.kind() == SnmpMibObject.Kind.SCALAR) {
            instanceOid = instanceOid + ".0";
        } else if (object.kind() == SnmpMibObject.Kind.COLUMN) {
            instanceOid = instanceOid + "." + index.trim();
        }
        String pointAddress = instanceOid + ":" + valueKind;
        boolean writable = isWritable(object.maxAccess());
        boolean history = isNumericSyntax(object.syntax());
        String schemaName = switch (valueKind) {
            case "STRING" -> "snmpString";
            case "BOOLEAN" -> "snmpBoolean";
            default -> "snmpNumeric";
        };
        List<DriverPointCatalog.SchemaField> fields = switch (schemaName) {
            case "snmpString" -> List.of(
                    new DriverPointCatalog.SchemaField("value", "STRING"),
                    new DriverPointCatalog.SchemaField("raw", "STRING"),
                    new DriverPointCatalog.SchemaField("type", "STRING")
            );
            case "snmpBoolean" -> List.of(
                    new DriverPointCatalog.SchemaField("value", "BOOLEAN"),
                    new DriverPointCatalog.SchemaField("raw", "STRING"),
                    new DriverPointCatalog.SchemaField("type", "STRING")
            );
            default -> List.of(
                    new DriverPointCatalog.SchemaField("value", "DOUBLE"),
                    new DriverPointCatalog.SchemaField("raw", "STRING"),
                    new DriverPointCatalog.SchemaField("type", "STRING")
            );
        };
        String varName = object.name();
        if (object.kind() == SnmpMibObject.Kind.COLUMN && !index.isBlank()) {
            varName = object.name() + "_" + index.trim().replace('.', '_');
        }
        return new DriverPointCatalog.PointProposal(
                varName,
                pointAddress,
                schemaName,
                fields,
                writable,
                history,
                object.name(),
                object.units(),
                truncate(object.description(), 500)
        );
    }

    static String mapValueKind(String syntax) {
        String s = syntax == null ? "" : syntax.toLowerCase(Locale.ROOT);
        if (s.contains("truthvalue") || s.contains("boolean")) {
            return "BOOLEAN";
        }
        if (s.contains("displaystring")
                || s.contains("octet string")
                || s.contains("octetstring")
                || s.contains("ipaddress")
                || s.contains("object identifier")
                || s.contains("oid")
                || s.contains("datetime")
                || s.contains("macaddress")
                || s.contains("snmadminstring")) {
            return "STRING";
        }
        if (s.contains("integer")
                || s.contains("counter")
                || s.contains("gauge")
                || s.contains("timeticks")
                || s.contains("unsigned")
                || s.contains("bits")) {
            return "INTEGER";
        }
        if (s.isBlank()) {
            return "AUTO";
        }
        // named textual conventions often numeric; default AUTO
        return "AUTO";
    }

    private static boolean isNumericSyntax(String syntax) {
        return "INTEGER".equals(mapValueKind(syntax));
    }

    private static boolean isWritable(String maxAccess) {
        String a = maxAccess == null ? "" : maxAccess.toLowerCase(Locale.ROOT);
        return a.contains("read-write") || a.contains("write-only") || a.contains("read-create");
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max - 1) + "…";
    }
}
