package com.ispf.core.ref;

import com.ispf.core.binding.BindingVariableRef;

import java.util.Objects;

/**
 * Canonical address of a variable, function, event, or historian tag on the object tree.
 *
 * <p>Text form (slash grammar): see ADR-0043.
 */
public record PlatformRef(
        String object,
        PlatformRefKind kind,
        String name,
        String field
) {

    public static final String CURRENT_OBJECT = "@";
    public static final String DEFAULT_FIELD = "value";

    public PlatformRef {
        if (object == null || object.isBlank()) {
            throw new IllegalArgumentException("object is required");
        }
        if (kind == null) {
            kind = PlatformRefKind.VARIABLE;
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (kind == PlatformRefKind.VARIABLE) {
            if (field == null || field.isBlank()) {
                field = DEFAULT_FIELD;
            }
        } else {
            field = null;
        }
    }

    public static PlatformRef variable(String object, String variableName) {
        return variable(object, variableName, DEFAULT_FIELD);
    }

    public static PlatformRef variable(String object, String variableName, String field) {
        return new PlatformRef(object, PlatformRefKind.VARIABLE, variableName, field);
    }

    public static PlatformRef function(String object, String functionName) {
        return new PlatformRef(object, PlatformRefKind.FUNCTION, functionName, null);
    }

    public static PlatformRef event(String object, String eventName) {
        return new PlatformRef(object, PlatformRefKind.EVENT, eventName, null);
    }

    public static PlatformRef tag(String object, String ruleId) {
        return new PlatformRef(object, PlatformRefKind.TAG, ruleId, null);
    }

    public static PlatformRef currentVariable(String variableName) {
        return variable(CURRENT_OBJECT, variableName);
    }

    public static PlatformRef currentVariable(String variableName, String field) {
        return variable(CURRENT_OBJECT, variableName, field);
    }

    public boolean isCurrentObject() {
        return CURRENT_OBJECT.equals(object);
    }

    public boolean isVariable() {
        return kind == PlatformRefKind.VARIABLE;
    }

    public boolean isFunction() {
        return kind == PlatformRefKind.FUNCTION;
    }

    public boolean isEvent() {
        return kind == PlatformRefKind.EVENT;
    }

    public boolean isTag() {
        return kind == PlatformRefKind.TAG;
    }

    /**
     * Resolves {@code @} to the rule-owner path for dependency indexing.
     */
    public PlatformRef resolveObject(String ruleObjectPath) {
        if (!isCurrentObject()) {
            return this;
        }
        if (ruleObjectPath == null || ruleObjectPath.isBlank()) {
            throw new IllegalArgumentException("ruleObjectPath is required to resolve @");
        }
        return switch (kind) {
            case VARIABLE -> variable(ruleObjectPath, name, field);
            case FUNCTION -> function(ruleObjectPath, name);
            case EVENT -> event(ruleObjectPath, name);
            case TAG -> tag(ruleObjectPath, name);
        };
    }

    public BindingVariableRef toVariableActivatorRef(String ruleObjectPath) {
        if (kind != PlatformRefKind.VARIABLE) {
            throw new IllegalStateException("Not a variable ref: " + this);
        }
        PlatformRef resolved = resolveObject(ruleObjectPath);
        if (resolved.object().equals(ruleObjectPath)) {
            return BindingVariableRef.local(resolved.name());
        }
        return BindingVariableRef.remote(resolved.object(), resolved.name());
    }

    public String eventKey(String ruleObjectPath) {
        PlatformRef resolved = resolveObject(ruleObjectPath);
        return resolved.object() + "|" + resolved.name();
    }

    @Override
    public String toString() {
        return PlatformRefFormatter.format(this);
    }

    public boolean structurallyEquals(PlatformRef other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(object, other.object)
                && kind == other.kind
                && Objects.equals(name, other.name)
                && Objects.equals(field, other.field);
    }
}
