package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.SystemIntrinsicBlueprints;
import com.ispf.server.bootstrap.FixtureBlueprintDefinitions;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies built-in variable schemas for platform system object types (embedded structure, not relative-model mixins).
 */
@Service
public class SystemObjectStructureService {

    private final ObjectManager objectManager;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintEngine blueprintEngine;

    public SystemObjectStructureService(
            ObjectManager objectManager,
            BlueprintRegistry blueprintRegistry,
            BlueprintEngine blueprintEngine
    ) {
        this.objectManager = objectManager;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintEngine = blueprintEngine;
    }

    @Transactional
    public void ensureDataSourceStructure(String path) {
        if (objectManager.require(path).getVariable("schemaName").isPresent()) {
            return;
        }
        applyIntrinsic("data-source-v1", path);
    }

    @Transactional
    public void ensureScheduleStructure(String path) {
        if (objectManager.require(path).getVariable("scheduleId").isPresent()) {
            return;
        }
        applyIntrinsic("schedule-v1", path);
    }

    @Transactional
    public void ensureBindingStructure(String path) {
        if (objectManager.require(path).getVariable("query").isPresent()) {
            return;
        }
        applyIntrinsic("sql-binding-v1", path);
    }

    @Transactional
    public void ensureMigrationStructure(String path) {
        if (objectManager.require(path).getVariable("scriptId").isPresent()) {
            return;
        }
        applyIntrinsic("migration-v1", path);
    }

    @Transactional
    public void ensureDashboardStructure(String path) {
        if (objectManager.require(path).getVariable("layout").isPresent()) {
            return;
        }
        applyIntrinsic("dashboard-v1", path);
    }

    @Transactional
    public void ensureMimicStructure(String path) {
        if (objectManager.require(path).getVariable("diagram").isPresent()) {
            return;
        }
        applyIntrinsic("mimic-v1", path);
    }

    @Transactional
    public void ensureReportStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.getVariable("query").isPresent() || node.getVariable("reportType").isPresent()) {
            return;
        }
        applyIntrinsic("report-v1", path);
    }

    @Transactional
    public void ensureWorkflowStructure(String path) {
        if (objectManager.require(path).getVariable("bpmnXml").isPresent()) {
            return;
        }
        applyIntrinsic("workflow-v1", path);
    }

    @Transactional
    public void ensureAlertRuleStructure(String path) {
        if (objectManager.require(path).getVariable("targetObjectPath").isPresent()) {
            return;
        }
        applyIntrinsic("alert-rule-v1", path);
    }

    @Transactional
    public void ensureCorrelatorStructure(String path) {
        if (objectManager.require(path).getVariable("patternType").isPresent()) {
            return;
        }
        applyIntrinsic("correlator-v1", path);
    }

    @Transactional
    public void ensureDeviceDriverStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.getVariable("driverId").isEmpty()) {
            blueprintEngine.applyIntrinsicStructure(FixtureBlueprintDefinitions.buildDeviceDriverModel(), path);
            objectManager.persistNodeTree(path);
            return;
        }
        ensureDeviceTimeZoneVariable(path);
    }

    private void ensureDeviceTimeZoneVariable(String path) {
        if (objectManager.require(path).getVariable("timeZone").isPresent()) {
            return;
        }
        DataSchema schema = DataSchema.builder("timeZone").field("value", FieldType.STRING).build();
        DataRecord record = DataRecord.single(schema, java.util.Map.of("value", ""));
        PlatformObject node = objectManager.require(path);
        node.addVariable(new Variable("timeZone", schema, true, true, record));
        objectManager.persistNodeTree(path);
    }

    private void applyIntrinsic(String blueprintName, String path) {
        blueprintRegistry.findByName(blueprintName).ifPresent(model -> {
            blueprintEngine.applyIntrinsicStructure(model, path);
            objectManager.persistNodeTree(path);
        });
    }
}
