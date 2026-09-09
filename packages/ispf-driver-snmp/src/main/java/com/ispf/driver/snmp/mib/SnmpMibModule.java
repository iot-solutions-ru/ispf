package com.ispf.driver.snmp.mib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed SMIv2 module (subset). */
public final class SnmpMibModule {

    public record FileInfo(String fileName, String moduleName, long sizeBytes, String status) {
    }

    private final String fileName;
    private final String moduleName;
    private final Map<String, String> oidAssignments;
    private final Map<String, SnmpMibObject> objects;
    private final List<String> imports;
    private final List<String> parseErrors;

    public SnmpMibModule(
            String fileName,
            String moduleName,
            Map<String, String> oidAssignments,
            Map<String, SnmpMibObject> objects,
            List<String> imports,
            List<String> parseErrors
    ) {
        this.fileName = fileName;
        this.moduleName = moduleName != null && !moduleName.isBlank() ? moduleName : fileName;
        this.oidAssignments = new LinkedHashMap<>(oidAssignments != null ? oidAssignments : Map.of());
        this.objects = new LinkedHashMap<>(objects != null ? objects : Map.of());
        this.imports = imports != null ? List.copyOf(imports) : List.of();
        this.parseErrors = parseErrors != null ? List.copyOf(parseErrors) : List.of();
    }

    public String fileName() {
        return fileName;
    }

    public String moduleName() {
        return moduleName;
    }

    public Map<String, String> oidAssignments() {
        return oidAssignments;
    }

    public Map<String, SnmpMibObject> objects() {
        return objects;
    }

    public List<String> imports() {
        return imports;
    }

    public List<String> parseErrors() {
        return parseErrors;
    }

    void applyResolvedOids(Map<String, String> oidByName) {
        for (Map.Entry<String, String> e : oidAssignments.entrySet()) {
            String resolved = SnmpMibLibrary.resolveOidExpression(e.getValue(), oidByName);
            if (resolved != null && SnmpMibLibrary.isNumericOid(resolved)) {
                e.setValue(resolved);
            }
        }
        for (SnmpMibObject object : objects.values()) {
            String expr = object.oidExpression();
            String resolved = oidByName.get(object.name());
            if (resolved == null) {
                resolved = SnmpMibLibrary.resolveOidExpression(expr, oidByName);
            }
            if (resolved != null && SnmpMibLibrary.isNumericOid(resolved)) {
                object.setResolvedOid(resolved);
                oidAssignments.put(object.name(), resolved);
            }
        }
    }
}
