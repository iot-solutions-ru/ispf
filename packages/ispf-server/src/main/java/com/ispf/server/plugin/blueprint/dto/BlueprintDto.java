package com.ispf.server.plugin.blueprint.dto;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.plugin.blueprint.BlueprintAttachment;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintReevaluation;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.plugin.blueprint.MixinReevaluationTrigger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record BlueprintDto(
        String id,
        String name,
        String description,
        BlueprintType type,
        ObjectType targetObjectType,
        String suitabilityExpression,
        BlueprintReevaluationDto reevaluation,
        String objectPath,
        List<BlueprintVariableDefinition> variables,
        List<EventDescriptor> events,
        List<FunctionDescriptor> functions,
        List<BlueprintBindingRule> bindings,
        Map<String, String> parameters,
        Instant createdAt,
        Instant updatedAt
) {
    public static BlueprintDto from(BlueprintDefinition model) {
        return new BlueprintDto(
                model.id(),
                model.name(),
                model.description(),
                model.type(),
                model.targetObjectType(),
                model.suitabilityExpression(),
                BlueprintReevaluationDto.from(model.reevaluation()),
                model.catalogObjectPath(),
                model.variables(),
                model.events(),
                model.functions(),
                model.bindingRules(),
                model.parameters(),
                model.createdAt(),
                model.updatedAt()
        );
    }

    public record BlueprintReevaluationDto(boolean enabled, List<String> triggers) {
        public static BlueprintReevaluationDto from(BlueprintReevaluation reevaluation) {
            BlueprintReevaluation cfg = reevaluation != null ? reevaluation : BlueprintReevaluation.DISABLED;
            return new BlueprintReevaluationDto(
                    cfg.enabled(),
                    cfg.triggers().stream().map(Enum::name).toList()
            );
        }

        public BlueprintReevaluation toModel() {
            if (!enabled) {
                return BlueprintReevaluation.DISABLED;
            }
            MixinReevaluationTrigger[] parsed = triggers == null
                    ? new MixinReevaluationTrigger[0]
                    : triggers.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> MixinReevaluationTrigger.valueOf(s.toUpperCase()))
                    .toArray(MixinReevaluationTrigger[]::new);
            return BlueprintReevaluation.of(true, parsed);
        }
    }
}
