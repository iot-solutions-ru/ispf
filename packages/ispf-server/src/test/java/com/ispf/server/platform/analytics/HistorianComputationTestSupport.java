package com.ispf.server.platform.analytics;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test helper for historian binding rules (ADR-0041).
 */
public final class HistorianComputationTestSupport {

    private HistorianComputationTestSupport() {
    }

    public static String ensureDevice(ObjectManager objectManager, String parent, String nodeName) {
        String path = parent + "." + nodeName;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(parent, nodeName, ObjectType.DEVICE, nodeName, "historian test device", null);
        }
        return path;
    }

    public static void upsertRollingAvgRule(
            BindingRulesService bindingRulesService,
            String devicePath,
            String ruleId,
            String sourcePath,
            String sourceVariable,
            String outputVariable,
            String windowBucket
    ) {
        upsertBuiltinRule(
                bindingRulesService,
                devicePath,
                ruleId,
                "rollingAvg",
                sourcePath,
                sourceVariable,
                outputVariable,
                windowBucket
        );
    }

    public static void upsertBuiltinRule(
            BindingRulesService bindingRulesService,
            String devicePath,
            String ruleId,
            String helper,
            String sourcePath,
            String sourceVariable,
            String outputVariable,
            String windowBucket
    ) {
        List<BindingRule> rules = new ArrayList<>(bindingRulesService.listRules(devicePath));
        rules.removeIf(rule -> rule.id().equals(ruleId));
        rules.add(new BindingRule(
                ruleId,
                ruleId,
                true,
                rules.size(),
                BindingRuleKind.HISTORIAN,
                new BindingActivators(
                        false,
                        List.of(new BindingVariableRef(sourcePath, sourceVariable)),
                        null,
                        60_000L,
                        false,
                        false
                ),
                "",
                helper + "(" + sourcePath + "." + sourceVariable + ", " + windowBucket + ")",
                new BindingTarget("variable", outputVariable, "value", null, null),
                windowBucket,
                null
        ));
        bindingRulesService.saveRules(devicePath, rules);
    }

    public static void upsertCelHistorianRule(
            BindingRulesService bindingRulesService,
            String devicePath,
            String ruleId,
            String expression,
            String sourcePath,
            String sourceVariable,
            String outputVariable,
            String windowBucket
    ) {
        List<BindingRule> rules = new ArrayList<>(bindingRulesService.listRules(devicePath));
        rules.removeIf(rule -> rule.id().equals(ruleId));
        rules.add(new BindingRule(
                ruleId,
                ruleId,
                true,
                rules.size(),
                BindingRuleKind.HISTORIAN,
                new BindingActivators(
                        false,
                        List.of(new BindingVariableRef(sourcePath, sourceVariable)),
                        null,
                        60_000L,
                        false,
                        false
                ),
                "",
                expression,
                new BindingTarget("variable", outputVariable, "value", null, null),
                windowBucket,
                null
        ));
        bindingRulesService.saveRules(devicePath, rules);
    }

    public static DataRecord stringRecord(String value) {
        return DataRecord.single(
                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        );
    }
}
