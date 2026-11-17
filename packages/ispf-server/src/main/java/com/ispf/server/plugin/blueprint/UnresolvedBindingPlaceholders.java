package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.plugin.blueprint.BlueprintBindingRule;

/**
 * Detects leftover {@code $}{@code {...}} placeholders in blueprint or saved binding rules.
 * Startup must not feed such text into {@code PlatformRefParser} / rule evaluation.
 */
public final class UnresolvedBindingPlaceholders {

    private UnresolvedBindingPlaceholders() {
    }

    public static boolean inText(String text) {
        return text != null && text.contains("${");
    }

    /** True when any field of a blueprint rule still contains a raw placeholder. */
    public static boolean present(BlueprintBindingRule rule) {
        if (rule == null) {
            return false;
        }
        // id is kept literal on resolve (ownership / detach); do not treat ${ in id as unresolved.
        if (inText(rule.name())
                || inText(rule.condition())
                || inText(rule.expression())
                || inText(rule.targetVariable())
                || inText(rule.targetField())
                || inText(rule.kind())
                || inText(rule.windowBucket())
                || inText(rule.formulaRef())
                || inText(rule.formulaScope())
                || inText(rule.formulaAppId())) {
            return true;
        }
        if (rule.rollupBuckets() != null) {
            for (String bucket : rule.rollupBuckets()) {
                if (inText(bucket)) {
                    return true;
                }
            }
        }
        if (rule.formulaParams() != null) {
            for (String value : rule.formulaParams().values()) {
                if (inText(value)) {
                    return true;
                }
            }
        }
        BindingTarget target = rule.target();
        if (target != null && (inText(target.kind())
                || inText(target.variableName())
                || inText(target.field())
                || inText(target.path())
                || inText(target.eventName())
                || inText(target.ref()))) {
            return true;
        }
        BindingActivators activators = rule.activators();
        if (activators != null && (inText(activators.onEvent())
                || inText(activators.onEventRef()))) {
            return true;
        }
        if (activators != null) {
            for (BindingVariableRef ref : activators.onVariableChange()) {
                if (inText(ref.objectPath())
                        || inText(ref.variableName())
                        || inText(ref.ref())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True when a saved rule still has unresolved placeholders in fields that evaluation or
     * {@code PlatformRefParser} would touch.
     */
    public static boolean present(BindingRule rule) {
        if (rule == null) {
            return false;
        }
        if (inText(rule.expression()) || inText(rule.condition())) {
            return true;
        }
        BindingTarget target = rule.target();
        if (target != null && (inText(target.kind())
                || inText(target.variableName())
                || inText(target.field())
                || inText(target.path())
                || inText(target.eventName())
                || inText(target.ref()))) {
            return true;
        }
        if (inText(rule.windowBucket())
                || inText(rule.formulaRef())
                || inText(rule.formulaScope())
                || inText(rule.formulaAppId())) {
            return true;
        }
        if (rule.rollupBuckets() != null) {
            for (String bucket : rule.rollupBuckets()) {
                if (inText(bucket)) {
                    return true;
                }
            }
        }
        if (rule.formulaParams() != null) {
            for (String value : rule.formulaParams().values()) {
                if (inText(value)) {
                    return true;
                }
            }
        }
        BindingActivators activators = rule.activators();
        if (activators != null && (inText(activators.onEvent())
                || inText(activators.onEventRef()))) {
            return true;
        }
        if (activators != null) {
            for (BindingVariableRef ref : activators.onVariableChange()) {
                if (inText(ref.objectPath())
                        || inText(ref.variableName())
                        || inText(ref.ref())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Unresolved placeholders remain — do not evaluate.
     * Also skip when target/activator refs would hit {@code PlatformRefParser}.
     */
    public static boolean blocksEvaluation(BindingRule rule) {
        return present(rule);
    }
}
