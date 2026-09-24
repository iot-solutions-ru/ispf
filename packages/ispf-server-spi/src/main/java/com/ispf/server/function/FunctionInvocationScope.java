package com.ispf.server.function;

import java.util.function.Supplier;

/**
 * Tracks nested (script) and system-trusted invocation chains.
 * Privileged platform functions are blocked for direct user invoke unless nested or system.
 */
public final class FunctionInvocationScope {

    private static final ThreadLocal<Integer> NESTED_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> SYSTEM_TRUSTED_DEPTH = ThreadLocal.withInitial(() -> 0);

    private FunctionInvocationScope() {
    }

    public static boolean isNested() {
        return NESTED_DEPTH.get() > 0;
    }

    public static boolean isSystemTrusted() {
        return SYSTEM_TRUSTED_DEPTH.get() > 0;
    }

    public static <T> T callNested(Supplier<T> action) {
        NESTED_DEPTH.set(NESTED_DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            int depth = NESTED_DEPTH.get() - 1;
            if (depth <= 0) {
                NESTED_DEPTH.remove();
            } else {
                NESTED_DEPTH.set(depth);
            }
        }
    }

    public static void runNested(Runnable action) {
        callNested(() -> {
            action.run();
            return null;
        });
    }

    public static <T> T callSystemTrusted(Supplier<T> action) {
        SYSTEM_TRUSTED_DEPTH.set(SYSTEM_TRUSTED_DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            int depth = SYSTEM_TRUSTED_DEPTH.get() - 1;
            if (depth <= 0) {
                SYSTEM_TRUSTED_DEPTH.remove();
            } else {
                SYSTEM_TRUSTED_DEPTH.set(depth);
            }
        }
    }

    public static void runSystemTrusted(Runnable action) {
        callSystemTrusted(() -> {
            action.run();
            return null;
        });
    }
}
