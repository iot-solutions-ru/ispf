package com.ispf.core.object;

import java.util.Optional;

/**
 * Applied-blueprint contributions cannot be removed from the target object.
 * Detach the blueprint to drop what it owns.
 */
public final class BlueprintOwnedMember {

    private BlueprintOwnedMember() {
    }

    public static void assertDeletableVariable(PlatformObject node, String name) {
        assertDeletable(node.ownerOfVariable(name), "variable", name);
    }

    public static void assertDeletableEvent(PlatformObject node, String name) {
        assertDeletable(node.ownerOfEvent(name), "event", name);
    }

    public static void assertDeletableFunction(PlatformObject node, String name) {
        assertDeletable(node.ownerOfFunction(name), "function", name);
    }

    public static void assertDeletableBindingRule(PlatformObject node, String ruleId) {
        assertDeletable(node.ownerOfBindingRule(ruleId), "binding rule", ruleId);
    }

    private static void assertDeletable(Optional<String> owner, String kind, String name) {
        if (owner.isPresent()) {
            throw new IllegalArgumentException(
                    "Cannot delete " + kind + " '" + name + "': owned by applied blueprint " + owner.get()
            );
        }
    }
}
