package com.ispf.server.function;

import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.PlatformObject;
import com.ispf.expression.BindingEvaluationContext;
import com.ispf.expression.BindingExpressionEvaluator;
import com.ispf.expression.ExpressionEngine;
import com.ispf.server.platform.analytics.engine.HistorianCelPreprocessor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class FunctionExpressionEvaluator {

    private static final Pattern HISTORIAN_CEL = Pattern.compile("hist\\.", Pattern.CASE_INSENSITIVE);

    private final BindingExpressionEvaluator bindingEvaluator = new BindingExpressionEvaluator();
    private final ExpressionEngine expressionEngine;
    private final HistorianPort historianPort;
    private final LiveVariablePort liveVariablePort;
    private final BindingEvaluationContext bindingContext;
    private final FunctionExpressionResolver expressionResolver;

    public FunctionExpressionEvaluator(
            ExpressionEngine expressionEngine,
            HistorianPort historianPort,
            LiveVariablePort liveVariablePort,
            BindingEvaluationContext bindingContext,
            FunctionExpressionResolver expressionResolver
    ) {
        this.expressionEngine = expressionEngine;
        this.historianPort = historianPort;
        this.liveVariablePort = liveVariablePort;
        this.bindingContext = bindingContext;
        this.expressionResolver = expressionResolver;
    }

    public DataRecord evaluate(
            PlatformObject node,
            FunctionExpressionBody body,
            DataRecord input,
            DataSchema outputSchema
    ) {
        String expression = expressionResolver.resolve(body);
        Map<String, Object> inputContext = buildInputContext(input);
        if (containsHistorianCel(expression)) {
            String expanded = HistorianCelPreprocessor.expand(
                    expression,
                    historianPort,
                    liveVariablePort,
                    Instant.now()
            );
            Object raw = expressionEngine.evaluate(expanded, node, inputContext);
            return BindingExpressionEvaluator.mapResult(outputSchema, raw);
        }
        return bindingEvaluator.evaluate(
                        node,
                        "_fn",
                        expression,
                        outputSchema,
                        bindingContext,
                        inputContext
                )
                .orElseThrow(() -> new IllegalStateException("Expression returned empty: " + expression));
    }

    private static Map<String, Object> buildInputContext(DataRecord input) {
        if (input == null || input.rowCount() == 0) {
            return Map.of();
        }
        return new LinkedHashMap<>(input.firstRow());
    }

    static boolean containsHistorianCel(String expression) {
        return expression != null && HISTORIAN_CEL.matcher(expression).find();
    }
}
