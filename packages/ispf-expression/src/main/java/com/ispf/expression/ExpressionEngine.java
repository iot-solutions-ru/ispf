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
            try {
                CelRuntime.Program program = runtime.createProgram(ast);
                return program.eval(buildBindings(platformObject));
            } catch (Exception e) {
                throw new ExpressionException("Evaluation failed: " + source, e);
            }
        }
    }

    private static Map<String, Object> buildBindings(PlatformObject platformObject) {
        Map<String, Object> self = new HashMap<>();
        for (Variable variable : platformObject.variables().values()) {
            Optional<DataRecord> value = variable.value();
            if (value.isPresent() && value.get().rowCount() > 0) {
                self.put(variable.name(), value.get().firstRow());
            }
        }
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("self", self);
        bindings.put("parent", Map.of());
        return bindings;
    }
}
