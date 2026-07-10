package com.ispf.core.binding;

import java.util.Map;

/**
 * Optional Tier B formula link on a binding rule (ADR-0042 / BL-215).
 */
public record BindingFormulaRef(
        String formulaRef,
        Map<String, String> formulaParams,
        String formulaScope,
        String formulaAppId
) {
    public BindingFormulaRef {
        if (formulaParams != null) {
            formulaParams = Map.copyOf(formulaParams);
        }
    }

    public boolean isPresent() {
        return formulaRef != null && !formulaRef.isBlank();
    }
}
