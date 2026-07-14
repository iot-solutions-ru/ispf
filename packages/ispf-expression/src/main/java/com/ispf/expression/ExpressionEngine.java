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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Expression engine based on Google CEL.
 */
public class ExpressionEngine {

    private static final Pattern SELF_IDENT = Pattern.compile("\\bself\\b");
    private static final Pattern PARENT_IDENT = Pattern.compile("\\bparent\\b");
    private static final Pattern CONTEXT_IDENT = Pattern.compile("\\bcontext\\b");
    private static final Pattern INPUT_IDENT = Pattern.compile("\\binput\\b");

    private final CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("self", SimpleType.DYN)
            .addVar("parent", SimpleType.DYN)
            .addVar("context", SimpleType.DYN)
            .addVar("input", SimpleType.DYN)
            .build();

    private final CelCompiler payloadCompiler = CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("payload", SimpleType.DYN)
            .build();

    private final CelRuntime runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();

    private final ConcurrentHashMap<String, CompiledExpression> compiledCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PayloadCompiledExpression> payloadCompiledCache = new ConcurrentHashMap<>();

    public CompiledExpression compile(String expression) {
        return compiledCache.computeIfAbsent(expression, this::compileUncached);
    }

    private CompiledExpression compileUncached(String expression) {
        try {
            CelAbstractSyntaxTree ast = compiler.compile(expression).getAst();
            return new CompiledExpression(expression, ast, runtime, BindingNeeds.analyze(expression));
        } catch (CelValidationException e) {
            throw new ExpressionException("Invalid expression: " + expression, e);
        }
    }

    public Object evaluate(String expression, PlatformObject platformObject) {
        return compile(expression).evaluate(platformObject, null, Map.of());
    }

    public Object evaluate(String expression, PlatformObject platformObject, Map<String, Object> context) {
        return compile(expression).evaluate(platformObject, null, context != null ? context : Map.of());
    }

    /**
     * Evaluates an alert condition, ensuring {@code watchVariable} is present in {@code self}
     * even when the async handler runs slightly after the triggering telemetry write —
     * only when the expression actually references {@code self}.
     */
    public Object evaluateAlertCondition(String expression, PlatformObject platformObject, String watchVariable) {
        return compile(expression).evaluate(platformObject, watchVariable);
    }

    public Object evaluateWithPayload(String expression, Map<String, Object> payload) {
        return payloadCompiledCache.computeIfAbsent(expression, this::compilePayloadUncached)
                .evaluate(payload);
    }

    /** Builds CEL evaluation bindings for debugger step-through (always full {@code self}). */
    public Map<String, Object> buildEvaluationBindings(
            PlatformObject platformObject,
            Map<String, Object> context
    ) {
        return buildBindings(platformObject, null, context != null ? context : Map.of(), BindingNeeds.ALL);
    }

    /** Compiles expression for debugger (throws on invalid CEL). */
    public void validateCelCompile(String expression) {
        compile(expression);
    }

    private PayloadCompiledExpression compilePayloadUncached(String expression) {
        try {
            CelAbstractSyntaxTree ast = payloadCompiler.compile(expression).getAst();
            return new PayloadCompiledExpression(expression, ast, runtime);
        } catch (CelValidationException e) {
            throw new ExpressionException("Invalid expression: " + expression, e);
        }
    }

    /**
     * Which CEL root bindings an expression needs. Avoids scanning every object variable when
     * {@code self} is unused (e.g. loadtest {@code conditionExpr = "true"}).
     */
    public record BindingNeeds(boolean self, boolean parent, boolean context, boolean input) {
        static final BindingNeeds ALL = new BindingNeeds(true, true, true, true);
        static final BindingNeeds NONE = new BindingNeeds(false, false, false, false);

        static BindingNeeds analyze(String expression) {
            if (expression == null || expression.isBlank()) {
                return NONE;
            }
            return new BindingNeeds(
                    SELF_IDENT.matcher(expression).find(),
                    PARENT_IDENT.matcher(expression).find(),
                    CONTEXT_IDENT.matcher(expression).find(),
                    INPUT_IDENT.matcher(expression).find()
            );
        }
    }

    public static final class CompiledExpression {
        private final String source;
        private final CelAbstractSyntaxTree ast;
        private final CelRuntime runtime;
        private final BindingNeeds needs;
        private volatile CelRuntime.Program program;

        CompiledExpression(String source, CelAbstractSyntaxTree ast, CelRuntime runtime, BindingNeeds needs) {
            this.source = source;
            this.ast = ast;
            this.runtime = runtime;
            this.needs = needs != null ? needs : BindingNeeds.ALL;
        }

        public String source() {
            return source;
        }

        /** Exposed for tests / diagnostics. */
        public BindingNeeds bindingNeeds() {
            return needs;
        }

        public Object evaluate(PlatformObject platformObject) {
            return evaluate(platformObject, null, Map.of());
        }

        Object evaluate(PlatformObject platformObject, String watchVariable) {
            return evaluate(platformObject, watchVariable, Map.of());
        }

        Object evaluate(PlatformObject platformObject, String watchVariable, Map<String, Object> context) {
            try {
                return program().eval(buildBindings(platformObject, watchVariable, context, needs));
            } catch (Exception e) {
                throw new ExpressionException("Evaluation failed: " + source, e);
            }
        }

        private CelRuntime.Program program() throws Exception {
            CelRuntime.Program cached = program;
            if (cached != null) {
                return cached;
            }
            synchronized (this) {
                if (program == null) {
                    program = runtime.createProgram(ast);
                }
                return program;
            }
        }
    }

    private static final class PayloadCompiledExpression {
        private final String source;
        private final CelAbstractSyntaxTree ast;
        private final CelRuntime runtime;
        private volatile CelRuntime.Program program;

        PayloadCompiledExpression(String source, CelAbstractSyntaxTree ast, CelRuntime runtime) {
            this.source = source;
            this.ast = ast;
            this.runtime = runtime;
        }

        Object evaluate(Map<String, Object> payload) {
            try {
                CelRuntime.Program program = program();
                Map<String, Object> bindings = new HashMap<>();
                bindings.put("payload", payload != null ? payload : Map.of());
                return program.eval(bindings);
            } catch (Exception e) {
                throw new ExpressionException("Evaluation failed: " + source, e);
            }
        }

        private CelRuntime.Program program() throws Exception {
            CelRuntime.Program cached = program;
            if (cached != null) {
                return cached;
            }
            synchronized (this) {
                if (program == null) {
                    program = runtime.createProgram(ast);
                }
                return program;
            }
        }
    }

    private static Map<String, Object> buildBindings(
            PlatformObject platformObject,
            String watchVariable,
            Map<String, Object> context,
            BindingNeeds needs
    ) {
        Map<String, Object> inputContext = context != null ? context : Map.of();
        Map<String, Object> bindings = new HashMap<>(4);
        bindings.put("self", needs.self() ? buildSelfMap(platformObject, watchVariable) : Map.of());
        bindings.put("parent", Map.of());
        Map<String, Object> ctx = (needs.context() || needs.input()) ? inputContext : Map.of();
        bindings.put("context", needs.context() ? ctx : Map.of());
        bindings.put("input", needs.input() ? ctx : Map.of());
        return bindings;
    }

    private static Map<String, Object> buildSelfMap(PlatformObject platformObject, String watchVariable) {
        Map<String, Object> self = new HashMap<>();
        if (platformObject != null) {
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
        }
        return self;
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
