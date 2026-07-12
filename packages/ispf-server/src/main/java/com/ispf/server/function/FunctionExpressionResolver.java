package com.ispf.server.function;

import com.ispf.server.platform.analytics.formula.AnalyticsFormula;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FunctionExpressionResolver {

    private final AnalyticsFormulaService formulaService;

    public FunctionExpressionResolver(AnalyticsFormulaService formulaService) {
        this.formulaService = formulaService;
    }

    public String resolve(FunctionExpressionBody body) {
        if (body == null) {
            throw new IllegalArgumentException("Expression function body is required");
        }
        if (body.hasFormulaRef()) {
            String scope = body.formulaScope() != null && !body.formulaScope().isBlank()
                    ? body.formulaScope()
                    : AnalyticsFormula.SCOPE_SITE;
            Map<String, String> params = body.formulaParams() != null ? body.formulaParams() : Map.of();
            return formulaService.expand(body.formulaRef(), params, scope, body.formulaAppId());
        }
        return body.expression();
    }
}
