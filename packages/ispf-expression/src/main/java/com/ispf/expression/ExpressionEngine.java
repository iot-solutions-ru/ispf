package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Expression engine based on Google CEL.
 */
public class ExpressionEngine {

    private final CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("self", SimpleType.DYN)
            .addVar("parent", SimpleType.DYN)
            .build();

    private final CelCompiler payloadCompiler = CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("payload", SimpleType.DYN)
            .build();

    private final CelRuntime runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();

    public CompiledExpression compile(String expression) {
        try {
            CelAbstractSyntaxTree ast = compiler.compile(expression).getAst();
            return new CompiledExpression(expression, ast, runtime);
        } catch (CelValidationException e) {
            throw new ExpressionException("Invalid expression: " + expression, e);
        }
    }

    public Object evaluate(String expression, PlatformObject platformObject) {
        return compile(expression).evaluate(platformObject);
    }

    /**
     * Evaluates an alert condition, ensuring {@code watchVariable} is present in {@code self}
     * even when the async handler runs slightly after the triggering telemetry write.
     */
    public Object evaluateAlertCondition(String expression, PlatformObject platformObject, String watchVariable) {
        return compile(expression).evaluate(platformObject, watchVariable);
    }

    public Object evaluateWithPayload(String expression, Map<String, Object> payload) {
        try {
            CelAbstractSyntaxTree ast = payloadCompiler.compile(expression).getAst();
            CelRuntime.Program program = runtime.createProgram(ast);
            Map<String, Object> bindings = new HashMap<>();
            bindings.put("payload", payload != null ? payload : Map.of());
            return program.eval(bindings);
        } catch (CelValidationException e) {
            throw new ExpressionException("Invalid expression: " + expression, e);
        } catch (Exception e) {
            throw new ExpressionException("Evaluation failed: " + expression, e);
        }
    }

    public static final class CompiledExpression {
        private final String source;
        private final CelAbstractSyntaxTree ast;
        private final CelRuntime runtime;

        CompiledExpression(String source, CelAbstractSyntaxTree ast, CelRuntime runtime) {
            this.source = source;
            this.ast = ast;
            this.runtime = runtime;
        }

        public String source() {
            return source;
        }

        public Object evaluate(PlatformObject platformObject) {
            return evaluate(platformObject, null);
        }

        Object evaluate(PlatformObject platformObject, String watchVariable) {
            try {
                CelRuntime.Program program = runtime.createProgram(ast);
                return program.eval(buildBindings(platformObject, watchVariable));
            } catch (Exception e) {
                throw new ExpressionException("Evaluation failed: " + source, e);
            }
        }
    }

    private static Map<String, Object> buildBindings(PlatformObject platformObject) {
        return buildBindings(platformObject, null);
    }

    private static Map<String, Object> buildBindings(PlatformObject platformObject, String watchVariable) {
        Map<String, Object> self = new HashMap<>();
        for (Variable variable : platformObject.variables().values()) {
            Optional<DataRecord> value = variable.value();
            if (value.isPresent() && value.get().rowCount() > 0) {
                self.put(variable.name(), normalizeRow(value.get().firstRow()));
            }
        }
        if (watchVariable != null && !watchVariable.isBlank()) {
            platformObject.getVariable(watchVariable)
                    .flatMap(Variable::value)
                    .filter(record -> record.rowCount() > 0)
                    .ifPresent(record -> self.put(watchVariable, normalizeRow(record.firstRow())));
        }
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("self", self);
        bindings.put("parent", Map.of());
        return bindings;
    }

    private static Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = HashMap.newHashMap(row.size());
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            normalized.put(entry.getKey(), normalizeValue(entry.getValue()));
        }
        return normalized;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value;
    }
}
