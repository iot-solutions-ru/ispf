package com.ispf.server.platform.analytics.formula;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Re-expands binding rules that reference a Tier B formula after its definition changes.
 */
@Service
public class BindingFormulaRebindService {

    private final ObjectManager objectManager;
    private final BindingRulesService bindingRulesService;

    public BindingFormulaRebindService(ObjectManager objectManager, BindingRulesService bindingRulesService) {
        this.objectManager = objectManager;
        this.bindingRulesService = bindingRulesService;
    }

    @Transactional
    public int rebindFormulaReferences(String formulaId, String scope, String appId) {
        if (formulaId == null || formulaId.isBlank()) {
            return 0;
        }
        String normalizedScope = normalizeScope(scope);
        int reboundRules = 0;
        for (PlatformObject node : objectManager.tree().all()) {
            String objectPath = node.path();
            List<BindingRule> rules = bindingRulesService.listRules(objectPath);
            long matches = rules.stream()
                    .filter(rule -> matchesFormulaRef(rule, formulaId, normalizedScope, appId))
                    .count();
            if (matches > 0) {
                bindingRulesService.saveRules(objectPath, rules);
                reboundRules += (int) matches;
            }
        }
        return reboundRules;
    }

    private static boolean matchesFormulaRef(
            BindingRule rule,
            String formulaId,
            String scope,
            String appId
    ) {
        if (!rule.hasFormulaRef() || !formulaId.equals(rule.formulaRef())) {
            return false;
        }
        String ruleScope = rule.formulaScope() == null || rule.formulaScope().isBlank()
                ? AnalyticsFormula.SCOPE_SITE
                : rule.formulaScope().toLowerCase(Locale.ROOT);
        if (!scope.equals(ruleScope)) {
            return false;
        }
        if (AnalyticsFormula.SCOPE_APP.equals(scope)) {
            String expectedAppId = appId == null ? "" : appId.trim();
            String ruleAppId = rule.formulaAppId() == null ? "" : rule.formulaAppId().trim();
            return expectedAppId.equals(ruleAppId);
        }
        return true;
    }

    private static String normalizeScope(String scope) {
        if (scope != null && AnalyticsFormula.SCOPE_APP.equalsIgnoreCase(scope.trim())) {
            return AnalyticsFormula.SCOPE_APP;
        }
        return AnalyticsFormula.SCOPE_SITE;
    }
}
