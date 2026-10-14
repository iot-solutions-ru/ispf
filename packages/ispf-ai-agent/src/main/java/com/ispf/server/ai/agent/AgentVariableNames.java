package com.ispf.server.ai.agent;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolve course/UI display names (Int, Sine Wave) onto exact variable ids (intValue, sineWave).
 */
final class AgentVariableNames {

    private static final Map<String, String> COURSE_ALIASES = Map.ofEntries(
            Map.entry("int", "intValue"),
            Map.entry("integer", "intValue"),
            Map.entry("intvalue", "intValue"),
            Map.entry("float", "floatValue"),
            Map.entry("double", "floatValue"),
            Map.entry("floatvalue", "floatValue"),
            Map.entry("sine", "sineWave"),
            Map.entry("sinewave", "sineWave"),
            Map.entry("saw", "sawtoothWave"),
            Map.entry("sawtooth", "sawtoothWave"),
            Map.entry("sawtoothwave", "sawtoothWave"),
            Map.entry("triangle", "triangleWave"),
            Map.entry("trianglewave", "triangleWave"),
            Map.entry("table", "table"),
            Map.entry("sum", "sumIntFloat"),
            Map.entry("sumintfloat", "sumIntFloat"),
            Map.entry("sumwaves", "sumWaves"),
            Map.entry("tableintsum", "tableIntSum")
    );

    private AgentVariableNames() {
    }

    static String resolve(PlatformObject node, String requested) {
        if (node == null || requested == null || requested.isBlank()) {
            return requested;
        }
        String trimmed = requested.trim();
        Optional<Variable> exact = node.getVariable(trimmed);
        if (exact.isPresent()) {
            return trimmed;
        }
        String compact = compact(trimmed);
        for (Variable variable : node.variables().values()) {
            String name = variable.name();
            if (name.equalsIgnoreCase(trimmed) || compact(name).equals(compact)) {
                return name;
            }
        }
        String alias = COURSE_ALIASES.get(compact);
        if (alias != null && node.getVariable(alias).isPresent()) {
            return alias;
        }
        return trimmed;
    }

    static Map<String, Object> unknownVariableError(PlatformObject node, String requested) {
        Set<String> names = node == null
                ? Set.of()
                : node.variables().values().stream().map(Variable::name).collect(Collectors.toCollection(
                        java.util.LinkedHashSet::new
                ));
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("status", "ERROR");
        err.put("error", "Unknown variable: " + requested);
        err.put(
                "hint",
                "Use exact ids from list_variables (virtual-unified: sineWave, sawtoothWave, intValue, floatValue, "
                        + "table, sumIntFloat, sumWaves). Course labels Int/Float/Sine Wave map to intValue/floatValue/sineWave. "
                        + "Available: " + String.join(", ", names)
        );
        err.put("availableVariables", names);
        return err;
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("[\\s_-]+", "").toLowerCase(Locale.ROOT);
    }
}
