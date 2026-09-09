package com.ispf.plugin.blueprint;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Opt-in MIXIN reevaluation (ADR-0058). Persisted via {@link BlueprintDefinition#parameters()}.
 */
public record BlueprintReevaluation(boolean enabled, Set<MixinReevaluationTrigger> triggers) {

    public static final String PARAM_ENABLED = "reevaluationEnabled";
    public static final String PARAM_TRIGGERS = "reevaluationTriggers";

    public static final BlueprintReevaluation DISABLED = new BlueprintReevaluation(false, Set.of());

    public BlueprintReevaluation {
        triggers = triggers == null || triggers.isEmpty()
                ? Set.of()
                : Set.copyOf(triggers);
    }

    public boolean watches(MixinReevaluationTrigger trigger) {
        return enabled && triggers.contains(trigger);
    }

    public static BlueprintReevaluation fromParameters(java.util.Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return DISABLED;
        }
        boolean enabled = "true".equalsIgnoreCase(parameters.getOrDefault(PARAM_ENABLED, "false"));
        if (!enabled) {
            return DISABLED;
        }
        String raw = parameters.getOrDefault(PARAM_TRIGGERS, "");
        Set<MixinReevaluationTrigger> parsed = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                try {
                    parsed.add(MixinReevaluationTrigger.valueOf(token.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // skip unknown tokens (forward-compat)
                }
            }
        }
        if (parsed.isEmpty()) {
            // enabled with empty triggers → default v1 set
            parsed.addAll(EnumSet.of(
                    MixinReevaluationTrigger.OBJECT_CREATED,
                    MixinReevaluationTrigger.SERVER_READY
            ));
        }
        return new BlueprintReevaluation(true, parsed);
    }

    public void writeTo(java.util.Map<String, String> parameters) {
        if (parameters == null) {
            return;
        }
        if (!enabled) {
            parameters.remove(PARAM_ENABLED);
            parameters.remove(PARAM_TRIGGERS);
            return;
        }
        parameters.put(PARAM_ENABLED, "true");
        parameters.put(
                PARAM_TRIGGERS,
                triggers.stream().map(Enum::name).collect(Collectors.joining(","))
        );
    }

    public static BlueprintReevaluation of(boolean enabled, MixinReevaluationTrigger... triggers) {
        if (!enabled) {
            return DISABLED;
        }
        Set<MixinReevaluationTrigger> set = triggers == null || triggers.length == 0
                ? EnumSet.of(MixinReevaluationTrigger.OBJECT_CREATED, MixinReevaluationTrigger.SERVER_READY)
                : Arrays.stream(triggers).collect(Collectors.toCollection(LinkedHashSet::new));
        return new BlueprintReevaluation(true, set);
    }
}
