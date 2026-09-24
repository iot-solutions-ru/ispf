package com.ispf.server.spi;

/**
 * Startup order shared by the object-tree readiness gate and listeners that must run after it.
 * Values match {@code Ordered.LOWEST_PRECEDENCE - 1} and the following step, without a Spring dependency.
 */
public final class ObjectTreeReadyOrder {

    public static final int OBJECT_TREE_READY_ORDER = Integer.MAX_VALUE - 1;
    public static final int AFTER_OBJECT_TREE_READY_ORDER = OBJECT_TREE_READY_ORDER + 1;

    private ObjectTreeReadyOrder() {
    }
}
