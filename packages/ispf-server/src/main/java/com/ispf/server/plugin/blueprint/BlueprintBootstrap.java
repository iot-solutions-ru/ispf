package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.plugin.blueprint.SystemIntrinsicBlueprints;
import com.ispf.server.dashboard.DashboardLayouts;
import com.ispf.server.dashboard.DashboardContextSupport;
import com.ispf.server.mimic.MimicLayouts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds built-in models for the platform.
 */
@Component
public class BlueprintBootstrap {

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema THRESHOLD_SCHEMA = DataSchema.builder("threshold")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema BOOLEAN_VALUE_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema FUNCTION_RESULT_SCHEMA = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private static final DataSchema VOID_INPUT_SCHEMA = DataSchema.builder("voidInput").build();

    private static final DataSchema INTEGER_VALUE_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;

    public BlueprintBootstrap(BlueprintEngine blueprintEngine, BlueprintRegistry blueprintRegistry) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
    }

    /**
     * Registers built-in models on every startup (in-memory registry is empty after restart).
     */
    public void ensureBuiltInBlueprints() {
        seedModels();
        ensureAutomationModels();
    }

    private void ensureAutomationModels() {
        if (blueprintRegistry.findByName("alert-rule-v1").isEmpty()) {
            blueprintEngine.createBlueprint(buildAlertRuleModel().withSystemIntrinsicFlag());
        }
        if (blueprintRegistry.findByName("correlator-v1").isEmpty()) {
            blueprintEngine.createBlueprint(buildCorrelatorModel().withSystemIntrinsicFlag());
        }
        if (blueprintRegistry.findByName("mimic-v1").isEmpty()) {
            blueprintEngine.createBlueprint(buildMimicModel().withSystemIntrinsicFlag());
        }
    }

    static BlueprintDefinition buildMimicModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "mimic-v1",
                "SCADA mimic diagram with symbol library and live bindings",
                BlueprintType.MIXIN,
                ObjectType.MIMIC,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "title",
                                "Mimic title",
                                "info",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Mimic"))
                        ),
                        BlueprintVariableDefinition.of(
                                "refreshIntervalMs",
                                "Default polling interval in milliseconds",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        BlueprintVariableDefinition.of(
                                "diagram",
                                "SCADA mimic diagram JSON (symbols, connections, bindings)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", MimicLayouts.EMPTY_MIMIC))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    static BlueprintDefinition buildAlertRuleModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "alert-rule-v1",
                "CEL alert rule — watches a variable and publishes events",
                BlueprintType.MIXIN,
                ObjectType.ALERT,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "targetObjectPath",
                                "Object path to watch",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "watchVariable",
                                "Variable name that triggers evaluation",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "conditionExpr",
                                "CEL expression evaluated on the target object",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "anomalyModelId",
                                "Optional anomaly SPI model id (e.g. threshold-v1); when set, CEL is skipped",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "eventName",
                                "Event to publish when condition is met",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "payloadVariable",
                                "Optional variable for event payload",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "enabled",
                                "Whether the rule is active",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        BlueprintVariableDefinition.of(
                                "edgeTrigger",
                                "Fire only on false→true transition",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        BlueprintVariableDefinition.of(
                                "delaySeconds",
                                "Seconds condition must stay true before firing (with sustainWhileTrue)",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 0))
                        ),
                        BlueprintVariableDefinition.of(
                                "sustainWhileTrue",
                                "Require condition to remain true for delaySeconds before firing",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        ),
                        BlueprintVariableDefinition.of(
                                "rateLimitSeconds",
                                "Minimum seconds between event fires (0 = no limit)",
                                "config",
                                com.ispf.core.model.DataSchema.builder("rateLimitSeconds")
                                        .field("value", com.ispf.core.model.FieldType.INTEGER)
                                        .build(),
                                true,
                                true, DataRecord.single(
                                        com.ispf.core.model.DataSchema.builder("rateLimitSeconds")
                                                .field("value", com.ispf.core.model.FieldType.INTEGER)
                                                .build(),
                                        Map.of("value", 0)
                                )
                        ),
                        BlueprintVariableDefinition.of(
                                "priority",
                                "Alarm priority (CRITICAL, HIGH, MEDIUM, LOW)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "HIGH"))
                        ),
                        BlueprintVariableDefinition.of(
                                "ackRequired",
                                "Operator must acknowledge before alarm clears",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        ),
                        BlueprintVariableDefinition.of(
                                "deactivateExpr",
                                "CEL expression for alarm clear (empty = clear when conditionExpr is false)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "deactivateDelaySeconds",
                                "Seconds deactivate condition must hold before clear event",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 0))
                        ),
                        BlueprintVariableDefinition.of(
                                "pollIntervalMs",
                                "Periodic re-evaluation interval ms (0 = variable change only)",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 0))
                        ),
                        BlueprintVariableDefinition.of(
                                "triggerMessage",
                                "Optional CEL message included in notifications",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "clearEventName",
                                "Optional event published when alarm clears (latch mode)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "lastConditionMet",
                                "Runtime: last evaluated condition result",
                                "runtime",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        ),
                        BlueprintVariableDefinition.of(
                                "lastFiredAt",
                                "Runtime: last event fire timestamp (ISO-8601)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "conditionTrueSince",
                                "Runtime: when condition first became true (ISO-8601)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "latchedActive",
                                "Runtime: alarm latch active until clear",
                                "runtime",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        ),
                        BlueprintVariableDefinition.of(
                                "deactivateTrueSince",
                                "Runtime: when deactivate condition first became true (ISO-8601)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    static BlueprintDefinition buildCorrelatorModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "correlator-v1",
                "Event correlator — COUNT or SEQUENCE patterns trigger workflows",
                BlueprintType.MIXIN,
                ObjectType.CORRELATOR,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "targetObjectPath",
                                "Optional object path filter (empty = any)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "patternType",
                                "COUNT, SEQUENCE or EVENT_CHAIN",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "COUNT"))
                        ),
                        BlueprintVariableDefinition.of(
                                "eventName",
                                "Primary event name",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "secondEventName",
                                "Second event for SEQUENCE pattern",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "windowSeconds",
                                "Sliding window for COUNT pattern",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 0))
                        ),
                        BlueprintVariableDefinition.of(
                                "minOccurrences",
                                "Minimum occurrences in window",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 1))
                        ),
                        BlueprintVariableDefinition.of(
                                "cooldownSeconds",
                                "Cooldown after trigger",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 120))
                        ),
                        BlueprintVariableDefinition.of(
                                "sequenceGapSeconds",
                                "Max seconds between consecutive events (SEQUENCE / EVENT_CHAIN; 0 = no limit)",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 0))
                        ),
                        BlueprintVariableDefinition.of(
                                "actionType",
                                "Action on match (RUN_WORKFLOW)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "RUN_WORKFLOW"))
                        ),
                        BlueprintVariableDefinition.of(
                                "actionTarget",
                                "Workflow path, event name, variable=value, or report path",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "payloadFilterExpr",
                                "Optional CEL filter on latest event payload (payload map context)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "enabled",
                                "Whether the correlator is active",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        BlueprintVariableDefinition.of(
                                "lastTriggeredAt",
                                "Runtime: last trigger timestamp (ISO-8601)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    /** @deprecated use {@link #ensureBuiltInBlueprints()} */
    @Deprecated
    public void seedModels() {
        if (!blueprintRegistry.findByName("dashboard-v1").isEmpty()) {
            return;
        }

        BlueprintDefinition dashboard = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "dashboard-v1",
                "Low-code HMI dashboard with widget layout stored as JSON",
                BlueprintType.MIXIN,
                ObjectType.DASHBOARD,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "title",
                                "Dashboard title shown in the header",
                                "info",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Dashboard"))
                        ),
                        BlueprintVariableDefinition.of(
                                "refreshIntervalMs",
                                "Widget polling interval in milliseconds",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        BlueprintVariableDefinition.of(
                                "layout",
                                "Dashboard layout JSON (grid + widgets)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", DashboardLayouts.EMPTY_DASHBOARD))
                        ),
                        BlueprintVariableDefinition.of(
                                com.ispf.core.dashboard.DashboardContextConstants.VARIABLE,
                                "Operator session context (selection, params, widgets)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", DashboardContextSupport.EMPTY_JSON))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );

        blueprintEngine.createBlueprint(dashboard.withSystemIntrinsicFlag());

        BlueprintDefinition report = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "report-v1",
                "SQL report definition stored on object tree (REQ-PF-12)",
                BlueprintType.MIXIN,
                ObjectType.REPORT,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "title",
                                "Report title",
                                "info",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Report"))
                        ),
                        BlueprintVariableDefinition.of(
                                "dataSourcePath",
                                "Data source object path for SQL schema",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "query",
                                "SELECT/WITH SQL query (? placeholders)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "parameters",
                                "JSON array of SQL parameter names",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "[]"))
                        ),
                        BlueprintVariableDefinition.of(
                                "columns",
                                "JSON array of {field, label}",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "[]"))
                        ),
                        BlueprintVariableDefinition.of(
                                "defaultParameters",
                                "JSON object of default parameter values for preview",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        BlueprintVariableDefinition.of(
                                "maxRows",
                                "Maximum rows returned",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 1000))
                        ),
                        BlueprintVariableDefinition.of(
                                "refreshIntervalMs",
                                "Auto-refresh interval in view mode",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 30000))
                        ),
                        BlueprintVariableDefinition.of(
                                "templateFormat",
                                "YARG template format: xlsx, docx, html (empty = no template)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "layout",
                                "Report layout JSON (web designer)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );

        blueprintEngine.createBlueprint(report.withSystemIntrinsicFlag());

        BlueprintDefinition workflow = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "workflow-v1",
                "BPMN workflow with NATS event bridge",
                BlueprintType.MIXIN,
                ObjectType.WORKFLOW,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "title",
                                "Workflow title",
                                "info",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Workflow"))
                        ),
                        BlueprintVariableDefinition.of(
                                "status",
                                "Lifecycle status: DRAFT, ACTIVE, STOPPED",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "DRAFT"))
                        ),
                        BlueprintVariableDefinition.of(
                                "bpmnXml",
                                "BPMN 2.0 process definition",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "triggerJson",
                                "Trigger configuration JSON (variable or event)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        BlueprintVariableDefinition.of(
                                "operatorAppId",
                                "Operator App that receives user tasks from this workflow",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "instanceState",
                                "Last workflow instance state JSON",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        BlueprintVariableDefinition.of(
                                "lastRunAt",
                                "Timestamp of last workflow run",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "lastAction",
                                "Last action recorded by workflow",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "inputSchemaJson",
                                "JSON Schema (draft-ish) for invoke_workflow_tool input",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        BlueprintVariableDefinition.of(
                                "outputSchemaJson",
                                "JSON Schema describing completed instance variables to export",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        BlueprintVariableDefinition.of(
                                "toolDescription",
                                "Agent/MCP tool description when exposed as a workflow tool",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "sideEffectClass",
                                "READ | WRITE | CONTROL",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "WRITE"))
                        ),
                        BlueprintVariableDefinition.of(
                                "retryMaxAttempts",
                                "Max retries after FAILED (0 = disabled)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "0"))
                        ),
                        BlueprintVariableDefinition.of(
                                "retryBackoffSeconds",
                                "Backoff seconds between retries",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "30"))
                        ),
                        BlueprintVariableDefinition.of(
                                "errorWorkflowPath",
                                "Optional workflow path started on exhausted failure",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "webhookSlug",
                                "Public webhook slug (ACTIVE workflows only)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "cronExpression",
                                "Optional cron expression for scheduled starts",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );

        blueprintEngine.createBlueprint(workflow.withSystemIntrinsicFlag());
    }

    public BlueprintEngine blueprintEngine() {
        return blueprintEngine;
    }
}
