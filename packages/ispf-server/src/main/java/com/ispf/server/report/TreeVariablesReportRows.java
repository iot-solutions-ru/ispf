package com.ispf.server.report;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.VariableAclRequestContext;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Row source for {@code tree-variables} reports: walks DEVICE objects matching a path pattern and
 * flattens one variable of each into report rows (a RECORD_LIST field expands to one row per item).
 * Interactive MEMBER runs omit devices / variables the caller cannot read (BL-154).
 */
@Component
public class TreeVariablesReportRows {

    private final ObjectManager objectManager;
    private final VariableMemberAccessService variableMemberAccessService;

    public TreeVariablesReportRows(
            ObjectManager objectManager,
            VariableMemberAccessService variableMemberAccessService
    ) {
        this.objectManager = objectManager;
        this.variableMemberAccessService = variableMemberAccessService;
    }

    public List<Map<String, Object>> collect(String devicePathPattern, String variableName) {
        if (devicePathPattern == null || devicePathPattern.isBlank()) {
            throw new IllegalArgumentException("Report devicePathPattern is required for tree-variables reports");
        }
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("Report variableName is required for tree-variables reports");
        }

        Authentication memberAuthentication = null;
        if (VariableAclRequestContext.isMemberEnforced()) {
            memberAuthentication = VariableAclRequestContext.requireAuthentication();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            if (!matchesDevicePathPattern(node.path(), devicePathPattern)) {
                continue;
            }
            if (memberAuthentication != null
                    && !variableMemberAccessService.canRead(node.path(), variableName, memberAuthentication)) {
                continue;
            }
            Optional<DataRecord> record = node.getVariable(variableName).flatMap(Variable::value);
            if (record.isPresent()) {
                flattenVariableToRows(node.path(), record.get(), rows);
            }
        }
        return rows;
    }

    /** Exact path, path prefix, or glob with {@code *}. */
    static boolean matchesDevicePathPattern(String path, String pattern) {
        if (path == null || pattern == null || pattern.isBlank()) {
            return false;
        }
        if (pattern.contains("*")) {
            String regex = "^" + pattern.replace(".", "\\.").replace("*", ".*") + "$";
            return Pattern.compile(regex).matcher(path).matches();
        }
        return path.equals(pattern) || path.startsWith(pattern);
    }

    private static void flattenVariableToRows(
            String devicePath,
            DataRecord record,
            List<Map<String, Object>> rows
    ) {
        Optional<String> listField = record.schema().fields().stream()
                .filter(field -> field.type() == FieldType.RECORD_LIST)
                .map(FieldDefinition::name)
                .findFirst();
        if (listField.isPresent() && record.rowCount() > 0) {
            Object tableRowsObject = record.firstRow().get(listField.get());
            if (tableRowsObject instanceof List<?> tableRows) {
                for (Object rowObject : tableRows) {
                    if (rowObject instanceof Map<?, ?> row) {
                        rows.add(treeVariableRow(devicePath, row));
                    }
                }
                return;
            }
        }
        for (Map<String, Object> row : record.rows()) {
            rows.add(treeVariableRow(devicePath, row));
        }
    }

    private static Map<String, Object> treeVariableRow(String devicePath, Map<?, ?> row) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("devicepath", devicePath);
        for (Map.Entry<?, ?> entry : row.entrySet()) {
            if (entry.getKey() != null) {
                mapped.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return mapped;
    }
}
