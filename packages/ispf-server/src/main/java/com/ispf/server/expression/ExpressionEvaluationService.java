package com.ispf.server.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.BindingExpressionEvaluator;
import com.ispf.expression.BindingExpressionValidator;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.PlatformBindingRegistry;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ServerBindingEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ExpressionEvaluationService {

    private static final DataSchema GENERIC_RESULT_SCHEMA = DataSchema.builder("_expr")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final ServerBindingEvaluationContext bindingContext;
    private final BindingExpressionEvaluator bindingEvaluator = new BindingExpressionEvaluator();
    private final ExpressionEngine expressionEngine = new ExpressionEngine();

    public ExpressionEvaluationService(
            ObjectManager objectManager,
            ServerBindingEvaluationContext bindingContext
    ) {
        this.objectManager = objectManager;
        this.bindingContext = bindingContext;
    }

    public EvaluateResult evaluate(String objectPath, String expression, String targetVariable) {
        String trimmed = expression != null ? expression.trim() : "";
        List<EvaluateStep> steps = new ArrayList<>();

        if (trimmed.isBlank()) {
            return new EvaluateResult(false, expression, null, null, "Expression is blank", steps);
        }

        try {
            BindingExpressionValidator.validateOrThrow(trimmed);
            steps.add(new EvaluateStep("validate", "ok", Map.of("expression", trimmed)));
        } catch (Exception e) {
            steps.add(new EvaluateStep("validate", "error", e.getMessage()));
            return new EvaluateResult(false, trimmed, null, null, e.getMessage(), steps);
        }

        PlatformObject node;
        try {
            node = objectManager.require(objectPath);
            steps.add(new EvaluateStep("load-object", "ok", objectPath));
        } catch (Exception e) {
            steps.add(new EvaluateStep("load-object", "error", e.getMessage()));
            return new EvaluateResult(false, trimmed, null, null, e.getMessage(), steps);
        }

        Map<String, Object> variableContext = buildVariableContext(node);
        steps.add(new EvaluateStep("variable-context", "ok", variableContext));

        boolean platformBinding = PlatformBindingRegistry.matches(trimmed);
        if (platformBinding) {
            steps.add(new EvaluateStep("compile", "ok", Map.of("engine", "platform-binding", "expression", trimmed)));
            PlatformBindingRegistry.find(trimmed).ifPresent(binding ->
                    steps.add(new EvaluateStep("platform-binding", "ok", Map.of(
                            "binding", binding.getClass().getSimpleName())))
            );
        } else {
            try {
                expressionEngine.validateCelCompile(trimmed);
                steps.add(new EvaluateStep("compile-cel", "ok", Map.of("engine", "cel", "expression", trimmed)));
            } catch (Exception e) {
                steps.add(new EvaluateStep("compile-cel", "error", e.getMessage()));
                return new EvaluateResult(false, trimmed, null, null, e.getMessage(), steps);
            }
            Map<String, Object> celBindings = expressionEngine.buildEvaluationBindings(node, Map.of());
            steps.add(new EvaluateStep("cel-bindings", "ok", celBindings));
        }

        try {
            Object rawResult = evaluateRaw(node, trimmed, targetVariable);
            String resultType = rawResult != null ? rawResult.getClass().getSimpleName() : "null";
            steps.add(new EvaluateStep("evaluate", "ok", rawResult));
            if (targetVariable != null && !targetVariable.isBlank()) {
                steps.add(new EvaluateStep("map-result", "ok", Map.of(
                        "targetVariable", targetVariable,
                        "resultType", resultType
                )));
            }
            return new EvaluateResult(true, trimmed, rawResult, resultType, null, steps);
        } catch (Exception e) {
            steps.add(new EvaluateStep("evaluate", "error", e.getMessage()));
            return new EvaluateResult(false, trimmed, null, null, e.getMessage(), steps);
        }
    }

    private Object evaluateRaw(PlatformObject node, String expression, String targetVariable) {
        if (PlatformBindingRegistry.matches(expression)) {
            DataSchema schema = resolveTargetSchema(node, targetVariable);
            Optional<DataRecord> record = bindingEvaluator.evaluate(
                    node,
                    targetVariable != null ? targetVariable : "_expr",
                    expression,
                    schema,
                    bindingContext
            );
            return record.map(DataRecord::firstRow).orElse(null);
        }
        return expressionEngine.evaluate(expression, node);
    }

    private static DataSchema resolveTargetSchema(PlatformObject node, String targetVariable) {
        if (targetVariable != null && !targetVariable.isBlank()) {
            return node.getVariable(targetVariable)
                    .map(Variable::schema)
                    .orElse(GENERIC_RESULT_SCHEMA);
        }
        return GENERIC_RESULT_SCHEMA;
    }

    private static Map<String, Object> buildVariableContext(PlatformObject node) {
        Map<String, Object> context = new LinkedHashMap<>();
        for (Variable variable : node.variables().values()) {
            variable.value().ifPresent(record -> {
                if (record.rowCount() > 0) {
                    Map<String, Object> row = record.firstRow();
                    context.put(variable.name(), row.size() == 1 ? row.values().iterator().next() : row);
                }
            });
        }
        return context;
    }

    public record EvaluateStep(String phase, String status, Object detail) {
    }

    public record EvaluateResult(
            boolean valid,
            String expression,
            Object result,
            String resultType,
            String error,
            List<EvaluateStep> steps
    ) {
    }
}
