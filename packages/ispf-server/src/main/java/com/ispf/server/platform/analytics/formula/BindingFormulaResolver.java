package com.ispf.server.platform.analytics.formula;

import com.ispf.core.binding.BindingRule;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Re-expands Tier B {@link BindingRule#formulaRef()} links before validation/compile (BL-215).
 */
@Service
public class BindingFormulaResolver {

    private final AnalyticsFormulaService formulaService;

    public BindingFormulaResolver(AnalyticsFormulaService formulaService) {
        this.formulaService = formulaService;
    }

    public BindingRule resolve(BindingRule rule) {
        if (rule == null || !rule.hasFormulaRef()) {
            return rule;
        }
        String scope = rule.formulaScope() != null && !rule.formulaScope().isBlank()
                ? rule.formulaScope()
                : AnalyticsFormula.SCOPE_SITE;
        Map<String, String> params = rule.formulaParams() != null ? rule.formulaParams() : Map.of();
        String expanded = formulaService.expand(rule.formulaRef(), params, scope, rule.formulaAppId());
        return rule.withExpression(expanded);
    }
}
