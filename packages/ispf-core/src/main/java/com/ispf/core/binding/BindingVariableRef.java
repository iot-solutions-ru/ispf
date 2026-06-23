package com.ispf.core.binding;

/**
 * Reference to a variable that can trigger a binding rule.
 *
 * @param objectPath object path, {@code self} / empty for the rule owner object, or {@code *} for any local variable
 * @param variableName variable name, or {@code *} for any variable on the object
 */
public record BindingVariableRef(String objectPath, String variableName) {

    public static final String SELF = "self";
    public static final String ANY = "*";

    public BindingVariableRef {
        if (variableName == null || variableName.isBlank()) {
            variableName = ANY;
        }
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

    public boolean matches(String ruleObjectPath, String changedObjectPath, String changedVariable) {
        String activatorPath = objectPath == null || objectPath.isBlank() || SELF.equals(objectPath)
                ? ruleObjectPath
                : objectPath;
        if (!ANY.equals(activatorPath) && !activatorPath.equals(changedObjectPath)) {
            return false;
        }
        return ANY.equals(variableName) || variableName.equals(changedVariable);
    }
}
