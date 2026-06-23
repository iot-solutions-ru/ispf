package com.ispf.server.object;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.BindingDependencyParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BindingRulesService {

    private static final DataSchema RULES_SCHEMA = DataSchema.builder("bindingRulesJson")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public BindingRulesService(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    public List<BindingRule> listRules(String objectPath) {
        PlatformObject object = objectManager.require(objectPath);
        return readRules(object);
    }

    public List<BindingRule> saveRules(String objectPath, List<BindingRule> rules) {
        List<BindingRule> normalized = normalizeRules(rules);
        for (BindingRule rule : normalized) {
            validateRule(objectPath, rule);
        }
        writeRules(objectPath, normalized);
        return normalized;
    }

    public BindingRule upsertRule(String objectPath, BindingRule rule) {
        validateRule(objectPath, rule);
        List<BindingRule> rules = new ArrayList<>(listRules(objectPath));
        rules.removeIf(existing -> existing.id().equals(rule.id()));
        rules.add(rule);
        return saveRules(objectPath, rules).stream()
                .filter(r -> r.id().equals(rule.id()))
                .findFirst()
                .orElseThrow();
    }

    public void deleteRule(String objectPath, String ruleId) {
        List<BindingRule> rules = listRules(objectPath).stream()
                .filter(rule -> !rule.id().equals(ruleId))
                .toList();
        writeRules(objectPath, rules);
    }

    public static BindingActivators defaultActivators(String objectPath, String expression) {
        var remoteRefs = BindingDependencyParser.parseRefAtDependencies(expression);
        if (!remoteRefs.isEmpty()) {
            return new BindingActivators(false, new ArrayList<>(remoteRefs), null, 0);
        }
        return BindingActivators.onLocalChange();
    }

    private void validateRule(String objectPath, BindingRule rule) {
        PlatformObject object = objectManager.require(objectPath);
        if (object.getVariable(rule.target().variableName()).isEmpty()) {
            throw new IllegalArgumentException("Unknown target variable: " + rule.target().variableName());
        }
    }

    private List<BindingRule> readRules(PlatformObject object) {
        return object.getVariable(BindingRulesConstants.RULES_VARIABLE)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(json -> !json.isBlank())
                .map(this::parseRules)
                .orElse(List.of());
    }

    private List<BindingRule> parseRules(String json) {
        try {
            List<BindingRuleDto> dtos = objectMapper.readValue(json, new TypeReference<>() {});
            return dtos.stream().map(BindingRuleDto::toRule).toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid binding rules JSON: " + e.getMessage());
        }
    }

    private void writeRules(String objectPath, List<BindingRule> rules) {
        try {
            List<BindingRuleDto> dtos = rules.stream().map(BindingRuleDto::from).toList();
            String json = objectMapper.writeValueAsString(dtos);
            DataRecord record = DataRecord.single(RULES_SCHEMA, Map.of("value", json));
            objectManager.upsertSystemVariable(objectPath, BindingRulesConstants.RULES_VARIABLE, RULES_SCHEMA, record);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist binding rules", e);
        }
    }

    private static List<BindingRule> normalizeRules(List<BindingRule> rules) {
        List<BindingRule> normalized = new ArrayList<>();
        for (BindingRule rule : rules) {
            BindingActivators activators = rule.activators();
            if (activators.onVariableChange().isEmpty() && !activators.onStartup() && activators.periodicMs() <= 0) {
                activators = defaultActivators("", rule.expression());
            }
            normalized.add(new BindingRule(
                    rule.id(),
                    rule.name(),
                    rule.enabled(),
                    rule.order(),
                    activators,
                    rule.condition(),
                    rule.expression(),
                    rule.target()
            ));
        }
        normalized.sort((left, right) -> Integer.compare(left.order(), right.order()));
        return normalized;
    }

    private record BindingRuleDto(
            String id,
            String name,
            boolean enabled,
            int order,
            BindingActivatorsDto activators,
            String condition,
            String expression,
            BindingTargetDto target
    ) {
        static BindingRuleDto from(BindingRule rule) {
            return new BindingRuleDto(
                    rule.id(),
                    rule.name(),
                    rule.enabled(),
                    rule.order(),
                    BindingActivatorsDto.from(rule.activators()),
                    rule.condition(),
                    rule.expression(),
                    new BindingTargetDto(rule.target().variableName(), rule.target().field())
            );
        }

        BindingRule toRule() {
            return new BindingRule(
                    id,
                    name,
                    enabled,
                    order,
                    activators != null ? activators.toActivators() : null,
                    condition,
                    expression,
                    new BindingTarget(target.variableName(), target.field())
            );
        }
    }

    private record BindingActivatorsDto(
            boolean onStartup,
            List<BindingVariableRefDto> onVariableChange,
            String onEvent,
            long periodicMs
    ) {
        static BindingActivatorsDto from(BindingActivators activators) {
            return new BindingActivatorsDto(
                    activators.onStartup(),
                    activators.onVariableChange().stream().map(BindingVariableRefDto::from).toList(),
                    activators.onEvent(),
                    activators.periodicMs()
            );
        }

        BindingActivators toActivators() {
            List<BindingVariableRef> refs = onVariableChange != null
                    ? onVariableChange.stream().map(BindingVariableRefDto::toRef).toList()
                    : List.of();
            return new BindingActivators(onStartup, refs, onEvent, periodicMs);
        }
    }

    private record BindingVariableRefDto(String objectPath, String variableName) {
        static BindingVariableRefDto from(BindingVariableRef ref) {
            return new BindingVariableRefDto(ref.objectPath(), ref.variableName());
        }

        BindingVariableRef toRef() {
            return new BindingVariableRef(objectPath, variableName);
        }
    }

    private record BindingTargetDto(String variableName, String field) {
    }
}
