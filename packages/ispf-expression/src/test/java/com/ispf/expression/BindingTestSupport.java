package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test helper for evaluating binding expressions against a target variable. */
final class BindingTestSupport {

    private static final BindingExpressionEvaluator EVALUATOR = new BindingExpressionEvaluator();
    private static final Map<String, String> REGISTERED = new LinkedHashMap<>();

    private BindingTestSupport() {
    }

    static void clearRegistered() {
        REGISTERED.clear();
    }

    static Variable binding(String name, DataSchema schema, String expression) {
        REGISTERED.put(name, expression);
        return bindingVariable(name, schema);
    }

    static Variable bindingVariable(String name, DataSchema schema) {
        Map<String, Object> defaults;
        if (schema.fieldCount() == 1) {
            defaults = switch (schema.fields().getFirst().type()) {
                case STRING -> Map.of("value", "");
                case BOOLEAN -> Map.of("value", false);
                default -> Map.of("value", 0.0);
            };
        } else {
            defaults = Map.of("value", 0.0);
        }
        return new Variable(name, schema, true, false, DataRecord.single(schema, defaults));
    }

    static List<String> evaluateRegistered(PlatformObject node) {
        return evaluateRegistered(node, BindingEvaluationContext.NONE);
    }

    static List<String> evaluateRegistered(PlatformObject node, BindingEvaluationContext context) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> entry : REGISTERED.entrySet()) {
            changed.addAll(evaluate(node, entry.getKey(), entry.getValue(), context));
        }
        return changed;
    }

    static List<String> evaluate(PlatformObject node, String targetName, String expression) {
        return evaluate(node, targetName, expression, BindingEvaluationContext.NONE);
    }

    static List<String> evaluate(
            PlatformObject node,
            String targetName,
            String expression,
            BindingEvaluationContext context
    ) {
        Variable target = node.getVariable(targetName).orElseThrow();
        List<String> changed = new ArrayList<>();
        EVALUATOR.evaluate(node, targetName, expression, target.schema(), context).ifPresent(record -> {
            target.setComputedValue(record);
            changed.add(target.name());
        });
        return changed;
    }
}
