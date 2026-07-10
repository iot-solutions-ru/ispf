package com.ispf.expression;

import java.util.List;

/**
 * Catalog metadata for reactive platform binding functions (ADR-0042 / BL-212b).
 * Evaluation remains in {@link PlatformBindingRegistry}; this is discovery-only.
 */
public final class PlatformBindingCatalog {

    public record Parameter(
            String name,
            String type,
            boolean required,
            String description,
            String defaultValue
    ) {
    }

    public record Entry(
            String id,
            String displayName,
            String syntax,
            List<Parameter> parameters,
            String description,
            List<String> examples,
            boolean stateful,
            String category
    ) {
    }

    private static final List<Entry> REACTIVE_ENTRIES = List.of(
            entry("selectField", "Select field", "selectField(<sourceVar>)",
                    List.of(param("source", "variable", true, "Source variable on the object", null)),
                    "Reads a field from a DataRecord variable.", List.of("selectField(temperature)"), false, "signal"),
            entry("scale", "Scale", "scale(<sourceVar>, <inMin>, <inMax>, <outMin>, <outMax>)",
                    List.of(
                            param("source", "variable", true, "Source variable", null),
                            param("inMin", "number", true, "Input minimum", "0"),
                            param("inMax", "number", true, "Input maximum", "100"),
                            param("outMin", "number", true, "Output minimum", "0"),
                            param("outMax", "number", true, "Output maximum", "1")
                    ),
                    "Linearly maps a numeric value from one range to another.",
                    List.of("scale(level, 0, 100, 0, 1)"), false, "signal"),
            entry("clamp", "Clamp", "clamp(<sourceVar>, <min>, <max>)",
                    List.of(
                            param("source", "variable", true, "Source variable", null),
                            param("min", "number", true, "Minimum output", "0"),
                            param("max", "number", true, "Maximum output", "100")
                    ),
                    "Clamps a numeric value between min and max.", List.of("clamp(pressure, 0, 100)"), false, "signal"),
            entry("format", "Format", "format(<pattern>, <sourceVar>)",
                    List.of(
                            param("pattern", "string", true, "Printf-style pattern", "%.1f"),
                            param("source", "variable", true, "Source variable", null)
                    ),
                    "Formats a numeric value as text (reactive rules that target string variables).",
                    List.of("format(\"%.1f\", temperature)"), false, "signal"),
            entry("delta", "Delta", "delta(<sourceVar>[, field])",
                    List.of(param("source", "variable", true, "Source variable", null)),
                    "Difference from the previous evaluation sample.", List.of("delta(flowRate)"), true, "stateful"),
            entry("rate", "Rate", "rate(<sourceVar>[, field])",
                    List.of(param("source", "variable", true, "Source variable", null)),
                    "Per-second rate from consecutive samples.", List.of("rate(counter)"), true, "stateful"),
            entry("counterRate", "Counter rate", "counterRate(<sourceVar>[, field])",
                    List.of(param("source", "variable", true, "Monotonic counter variable", null)),
                    "Per-second rate for SNMP-style counters with wrap handling.", List.of("counterRate(ifInOctets)"), true, "stateful"),
            entry("counterDelta", "Counter delta", "counterDelta(<sourceVar>[, field])",
                    List.of(param("source", "variable", true, "Monotonic counter variable", null)),
                    "Delta for monotonic counters with wrap handling.", List.of("counterDelta(ifInOctets)"), true, "stateful"),
            entry("movingAvg", "Moving average", "movingAvg(<sourceVar>, <windowSec>[, field])",
                    List.of(
                            param("source", "variable", true, "Source variable", null),
                            param("windowSec", "number", true, "Window length in seconds", "60")
                    ),
                    "Rolling average over live samples in a time window.", List.of("movingAvg(temperature, 60)"), true, "stateful"),
            entry("movingMin", "Moving minimum", "movingMin(<sourceVar>, <windowSec>[, field])",
                    List.of(
                            param("source", "variable", true, "Source variable", null),
                            param("windowSec", "number", true, "Window length in seconds", "30")
                    ),
                    "Rolling minimum over live samples.", List.of("movingMin(pressure, 30)"), true, "stateful"),
            entry("movingMax", "Moving maximum", "movingMax(<sourceVar>, <windowSec>[, field])",
                    List.of(
                            param("source", "variable", true, "Source variable", null),
                            param("windowSec", "number", true, "Window length in seconds", "30")
                    ),
                    "Rolling maximum over live samples.", List.of("movingMax(pressure, 30)"), true, "stateful"),
            entry("deadband", "Deadband", "deadband(<sourceVar>, <band>[, field])",
                    List.of(
                            param("source", "variable", true, "Source variable", null),
                            param("band", "number", true, "Deadband width", "1.0")
                    ),
                    "Suppresses changes smaller than the deadband.", List.of("deadband(level, 1.0)"), true, "stateful"),
            entry("hysteresis", "Hysteresis", "hysteresis(<sourceVar>, <onThreshold>, <offThreshold>[, field])",
                    List.of(
                            param("source", "variable", true, "Source variable", null),
                            param("onThreshold", "number", true, "Turn-on threshold", "80"),
                            param("offThreshold", "number", true, "Turn-off threshold", "70")
                    ),
                    "Schmitt-trigger style boolean from an analog source.", List.of("hysteresis(level, 80, 70)"), true, "stateful"),
            entry("unitConvert", "Unit convert", "unitConvert(<sourceVar>, <fromUnit>, <toUnit>[, field])",
                    List.of(
                            param("source", "variable", true, "Source variable", null),
                            param("fromUnit", "unit", true, "Source unit", "C"),
                            param("toUnit", "unit", true, "Target unit", "F")
                    ),
                    "Converts supported engineering units.", List.of("unitConvert(temperature, C, F)"), false, "signal"),
            entry("refAt", "Reference at path", "refAt(<objectPath>, <remoteVar>[, field])",
                    List.of(
                            param("path", "tagPath", true, "Remote object path", null),
                            param("remoteVar", "variable", true, "Remote variable name", null)
                    ),
                    "Reads a variable from another object in the tree.", List.of("refAt(\"root.platform.devices.pump-01\", flow)"), false, "cross"),
            entry("callFunction", "Call function", "callFunction(<functionName>[, <sourceVar>[, field]])",
                    List.of(
                            param("function", "function", true, "Tree function name", null),
                            param("input", "variable", false, "Optional input variable", null)
                    ),
                    "Invokes an application/platform tree function on each reactive tick.",
                    List.of("callFunction(myFunc, inputVar)"), false, "function"),
            entry("callFunctionAt", "Call function at path", "callFunctionAt(<objectPath>, <functionName>[, <sourceVar>[, field]])",
                    List.of(
                            param("path", "tagPath", true, "Target object path", null),
                            param("function", "function", true, "Tree function name", null),
                            param("input", "variable", false, "Optional input variable", null)
                    ),
                    "Invokes a tree function on a remote object.", List.of("callFunctionAt(\"root.remote\", myFunc, inputVar)"), false, "function"),
            entry("sumRecordField", "Sum record field", "sumRecordField(<tableVar>, <field>)",
                    List.of(
                            param("table", "variable", true, "Table variable", null),
                            param("field", "string", true, "Numeric field to sum", "amount")
                    ),
                    "Sums a numeric column across table rows.", List.of("sumRecordField(orders, \"amount\")"), false, "aggregate")
    );

    private PlatformBindingCatalog() {
    }

    public static List<Entry> reactiveEntries() {
        return REACTIVE_ENTRIES;
    }

    private static Entry entry(
            String id,
            String displayName,
            String syntax,
            List<Parameter> parameters,
            String description,
            List<String> examples,
            boolean stateful,
            String category
    ) {
        return new Entry(id, displayName, syntax, parameters, description, examples, stateful, category);
    }

    private static Parameter param(String name, String type, boolean required, String description, String defaultValue) {
        return new Parameter(name, type, required, description, defaultValue);
    }
}
