package com.ispf.server.plugin.blueprint;

import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.server.platform.analytics.formula.AnalyticsFormula;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaParameter;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaService;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Embeds and applies analytics formulas shipped with blueprints (BL-215).
 */
@Service
public class BlueprintAnalyticsFormulaSupport {

    public static final String PARAMETERS_KEY = "analyticsFormulasJson";

    private final AnalyticsFormulaService formulaService;
    private final ObjectMapper objectMapper;

    public BlueprintAnalyticsFormulaSupport(AnalyticsFormulaService formulaService, ObjectMapper objectMapper) {
        this.formulaService = formulaService;
        this.objectMapper = objectMapper;
    }

    public void mergeBlueprintFormulas(BlueprintDefinition model) {
        List<AnalyticsFormula> formulas = readFromParameters(model.parameters());
        if (formulas.isEmpty()) {
            return;
        }
        for (AnalyticsFormula formula : formulas) {
            if (formulaService.find(formula.id(), formula.scope(), formula.appId()).isEmpty()) {
                formulaService.create(formula);
            }
        }
    }

    public List<AnalyticsFormula> readFromParameters(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }
        String json = parameters.get(PARAMETERS_KEY);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<BlueprintAnalyticsFormula> entries = objectMapper.readValue(json, new TypeReference<>() {});
            return entries.stream().map(this::toAnalyticsFormula).toList();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid blueprint analytics formulas JSON: " + ex.getMessage());
        }
    }

    public String writeToParameters(List<AnalyticsFormula> formulas, Map<String, String> parameters) {
        Map<String, String> next = parameters != null ? new java.util.LinkedHashMap<>(parameters) : new java.util.LinkedHashMap<>();
        if (formulas == null || formulas.isEmpty()) {
            next.remove(PARAMETERS_KEY);
            return next.get(PARAMETERS_KEY);
        }
        try {
            List<BlueprintAnalyticsFormula> entries = formulas.stream().map(this::fromAnalyticsFormula).toList();
            next.put(PARAMETERS_KEY, objectMapper.writeValueAsString(entries));
            return next.get(PARAMETERS_KEY);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize blueprint analytics formulas", ex);
        }
    }

    private AnalyticsFormula toAnalyticsFormula(BlueprintAnalyticsFormula entry) {
        List<AnalyticsFormulaParameter> parameters = entry.parameters() == null
                ? List.of()
                : entry.parameters().stream()
                        .map(param -> new AnalyticsFormulaParameter(
                                param.name(),
                                param.type(),
                                param.required(),
                                param.description(),
                                param.defaultValue()
                        ))
                        .toList();
        return new AnalyticsFormula(
                entry.id(),
                entry.displayName(),
                entry.kind(),
                entry.expression(),
                parameters,
                entry.createdBy(),
                entry.version() != null ? entry.version() : 1,
                entry.scope() != null ? entry.scope() : AnalyticsFormula.SCOPE_SITE,
                entry.appId()
        );
    }

    private BlueprintAnalyticsFormula fromAnalyticsFormula(AnalyticsFormula formula) {
        List<BlueprintAnalyticsFormulaParameter> parameters = formula.parameters() == null
                ? List.of()
                : formula.parameters().stream()
                        .map(param -> new BlueprintAnalyticsFormulaParameter(
                                param.name(),
                                param.type(),
                                param.required(),
                                param.description(),
                                param.defaultValue()
                        ))
                        .toList();
        return new BlueprintAnalyticsFormula(
                formula.id(),
                formula.displayName(),
                formula.kind(),
                formula.expression(),
                parameters,
                formula.createdBy(),
                formula.version(),
                formula.scope(),
                formula.appId()
        );
    }

    public record BlueprintAnalyticsFormula(
            String id,
            String displayName,
            String kind,
            String expression,
            List<BlueprintAnalyticsFormulaParameter> parameters,
            String createdBy,
            Integer version,
            String scope,
            String appId
    ) {
    }

    public record BlueprintAnalyticsFormulaParameter(
            String name,
            String type,
            boolean required,
            String description,
            String defaultValue
    ) {
    }
}
