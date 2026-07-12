package com.ispf.core.binding;

import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefKind;
import com.ispf.core.ref.PlatformRefParser;

/**
 * Reference to a variable that can trigger a binding rule.
 *
 * @param objectPath object path, {@code self} / empty for the rule owner object, or {@code *} for any local variable
 * @param variableName variable name, or {@code *} for any variable on the object
 * @param ref optional canonical PlatformRef string (dual-read with objectPath + variableName)
 */
public record BindingVariableRef(String objectPath, String variableName, String ref) {

    public static final String SELF = "self";
    public static final String ANY = "*";

    public BindingVariableRef {
        if (variableName == null || variableName.isBlank()) {
            variableName = ANY;
        }
        if (ref != null && ref.isBlank()) {
            ref = null;
        }
    }

    public BindingVariableRef(String objectPath, String variableName) {
        this(objectPath, variableName, null);
    }

    public static BindingVariableRef local(String variableName) {
        return new BindingVariableRef(SELF, variableName);
    }

    public static BindingVariableRef localAny() {
        return new BindingVariableRef(SELF, ANY);
    }

    public static BindingVariableRef remote(String objectPath, String variableName) {
        return new BindingVariableRef(objectPath, variableName);
    }

    public static BindingVariableRef fromRef(String refString) {
        PlatformRef ref = PlatformRefParser.parse(refString);
        if (ref.kind() != PlatformRefKind.VARIABLE) {
            throw new IllegalArgumentException("Variable activator ref required: " + refString);
        }
        String objectPath = ref.isCurrentObject() ? SELF : ref.object();
        return new BindingVariableRef(objectPath, ref.name(), refString);
    }

    public BindingVariableRef normalize() {
        if (ref == null || ref.isBlank()) {
            return this;
        }
        PlatformRef parsed = PlatformRefParser.parse(ref);
        if (parsed.kind() != PlatformRefKind.VARIABLE) {
            return this;
        }
        String path = parsed.isCurrentObject() ? SELF : parsed.object();
        return new BindingVariableRef(path, parsed.name(), ref);
    }

    public boolean matches(String ruleObjectPath, String changedObjectPath, String changedVariable) {
        BindingVariableRef normalized = normalize();
        String activatorPath = normalized.objectPath == null || normalized.objectPath.isBlank()
                || SELF.equals(normalized.objectPath)
                ? ruleObjectPath
                : normalized.objectPath;
        if (!ANY.equals(activatorPath) && !activatorPath.equals(changedObjectPath)) {
            return false;
        }
        return ANY.equals(normalized.variableName) || normalized.variableName.equals(changedVariable);
    }
}
